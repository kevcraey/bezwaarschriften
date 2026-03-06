# Design: Passage-deduplicatie als backend-functionaliteit

## Samenvatting

Passage-deduplicatie (groepering van vrijwel identieke passages) verhuist van de frontend naar de backend en wordt een configureerbare stap in het clusteringproces. De gebruiker kiest bij het starten van clustering of deduplicatie **voor** of **na** HDBSCAN plaatsvindt.

- **Voor clustering**: beïnvloedt de input van HDBSCAN (minder items, betere clusters)
- **Na clustering**: identiek aan het huidige gedrag, maar server-side berekend

In beide gevallen wordt het resultaat gepersisteerd voor traceerbaarheid. De API levert altijd gegroepeerde data; de frontend rendert enkel.

## Motivatie

- Veel bezwaarschriften bevatten letterlijk dezelfde passage. 100x dezelfde passage domineert een cluster en vertekent de centroid.
- Door voor clustering te dedupliceren krijgt HDBSCAN een schoner beeld, wat betere clusters oplevert.
- De bestaande frontend-groepering (`groepeerPassages` in `passage-groepering.js`) is puur visueel en niet traceerbaar. Door het naar de backend te verplaatsen wordt het resultaat gepersisteerd en controleerbaar.

## Datamodel

### Nieuwe tabellen

```sql
passage_groep
  id                          BIGSERIAL PRIMARY KEY
  clustering_taak_id          BIGINT NOT NULL  -- FK -> clustering_taak
  passage                     TEXT NOT NULL     -- representatieve passage-tekst
  samenvatting                TEXT NOT NULL     -- samenvatting van representatief bezwaar
  embedding                   VECTOR(1024)     -- embedding voor HDBSCAN
  categorie                   VARCHAR(50) NOT NULL
  score_percentage            INTEGER          -- centroid-score na clustering

passage_groep_lid
  id                          BIGSERIAL PRIMARY KEY
  passage_groep_id            BIGINT NOT NULL  -- FK -> passage_groep
  bezwaar_id                  BIGINT NOT NULL  -- FK -> geextraheerd_bezwaar
  bestandsnaam                VARCHAR NOT NULL
```

### Wijzigingen bestaande tabellen

- `clustering_taak`: nieuw veld `deduplicatie_voor_clustering` (BOOLEAN NOT NULL DEFAULT FALSE) — slaat de gemaakte keuze op
- `kernbezwaar_referentie`: verwijst naar `passage_groep_id` i.p.v. direct naar `bezwaar_id`

### Invarianten

- Elke `passage_groep` heeft minstens 1 lid
- Een groep met 1 lid = een uniek bezwaar
- Een groep met N leden = N vrijwel identieke passages uit verschillende documenten
- Bij "deduplicatie na clustering": elke bezwaar start als eigen groep met 1 lid; na clustering worden groepen binnen een kernbezwaar samengevoegd

## Flow

### Modus A: deduplicatie VOOR clustering

1. Haal alle bezwaren op per categorie
2. Groepeer op passage-gelijkenis (Dice-coefficient >= 0.9, zelfde algoritme als huidige frontend)
3. Per groep: kies representatief bezwaar (langste passage), neem diens samenvatting + embedding
4. Persisteer `passage_groep` + `passage_groep_lid`
5. HDBSCAN krijgt 1 `ClusteringInvoer` per groep
6. Per cluster: centroid-score berekenen per groep, representatief kernbezwaar kiezen
7. Sla kernbezwaar + referenties op (verwijzend naar `passage_groep`)

### Modus B: deduplicatie NA clustering

1. Haal alle bezwaren op per categorie
2. Elke bezwaar wordt een `passage_groep` met 1 lid — persisteer
3. HDBSCAN clustert alle individuele groepen (identiek aan huidige gedrag)
4. Per cluster: centroid-score berekenen, kernbezwaar opslaan
5. Per kernbezwaar: groepeer de referenties op passage-gelijkenis, voeg `passage_groep_lid` records samen waar nodig
6. Sla gemerged referenties op

### Belangrijk verschil

In modus A groepeert stap 2 over de hele categorie (voor clustering). In modus B groepeert stap 5 binnen elk kernbezwaar (na clustering). Het netto-effect voor de gebruiker is gelijk, maar modus A geeft HDBSCAN een schoner beeld.

## Domeinmodel

```
Kernbezwaar
  +-- samenvatting (van het cluster)
  +-- individueleBezwaren[]
        +-- samenvatting (van dit specifieke argument)
        +-- scorePercentage (centroid-score)
        +-- passageGroep
              +-- id
              +-- passage (text)
              +-- documenten[]
                    +-- bezwaarId (FK -> geextraheerd_bezwaar)
                    +-- bestandsnaam
```

Een "individueel bezwaar" is een distinct bezwaar-argument dat in N documenten voorkomt. De `passageGroep` bevat de passage-tekst en de lijst documenten.

## API Response

```json
{
  "themas": [{
    "naam": "Geluidshinder",
    "passageDeduplicatieVoorClustering": true,
    "kernbezwaren": [{
      "id": 1,
      "samenvatting": "Representatieve samenvatting van het cluster",
      "antwoord": "...",
      "individueleBezwaren": [{
        "samenvatting": "De geluidshinder is onaanvaardbaar",
        "scorePercentage": 85,
        "passageGroep": {
          "id": 42,
          "passage": "De geluidshinder is onaanvaardbaar en...",
          "documenten": [
            {"bezwaarId": 1, "bestandsnaam": "bezwaar1.pdf"},
            {"bezwaarId": 5, "bestandsnaam": "bezwaar2.pdf"},
            {"bezwaarId": 12, "bestandsnaam": "bezwaar3.pdf"}
          ]
        }
      }]
    }]
  }]
}
```

- `passageDeduplicatieVoorClustering` op thema-niveau: geeft aan welke modus is gebruikt
- Sortering binnen kernbezwaar: op `scorePercentage` (dichtst bij centroid eerst)

## Frontend wijzigingen

### Verwijderen

- `passage-groepering.js` — `groepeerPassages()` en `diceCoefficient()` worden overbodig

### Aanpassen in `bezwaarschriften-kernbezwaren.js`

- Render `individueleBezwaren` direct als gegroepeerd (geen client-side groepering meer)
- Per individueel bezwaar: toon samenvatting + passage + lijst documenten
- De 4 plekken waar `groepeerPassages` wordt aangeroepen (regels 448, 490, 1003, 1203) worden vervangen door directe rendering van de nieuwe structuur

### Clustering-start UI

- Toggle toevoegen: "Passage-deduplicatie voor clustering"
- Default: aan (aanbevolen modus)
- De keuze wordt meegegeven aan de API-call die de clustering-taak aanmaakt

## Cascade-verwijdering

- Bij document-verwijdering: `passage_groep_lid` records voor dat document verwijderen. Als een `passage_groep` geen leden meer heeft -> verwijderen. Als een `kernbezwaar` geen referenties meer heeft -> verwijderen (bestaande logica).
- Bij herclustering: bestaande `passage_groep` + `passage_groep_lid` records voor die clustering_taak verwijderd en opnieuw aangemaakt.

## Migratie

Geen migratiepad voor bestaande data. Herclustering is vereist na deploy. Het systeem is experimenteel en herclustering is goedkoop.

## Gelijkenisalgoritme

Dice-coefficient op genormaliseerde passage-tekst (lowercase, trim), drempel >= 0.9. Dit is hetzelfde algoritme als de huidige frontend-implementatie in `passage-groepering.js`. De backend-implementatie hergebruikt de bestaande `berekenSimilarity`-methode uit `PassageValidator` (Sorensen-Dice op bigrammen).
