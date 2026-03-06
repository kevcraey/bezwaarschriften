# Design: Post-processing van HDBSCAN noise via Centroid Matching

**Datum:** 2026-03-06
**Status:** Goedgekeurd

## Samenvatting

Voeg een post-processing stap (Two-Stage Assignment) toe na HDBSCAN-clustering, waarbij noise-bezwaren via centroid matching alsnog aan het best passende kernbezwaar worden gekoppeld. Tegelijk worden categorieen en thema's verwijderd als abstractielaag.

## Scope

1. Centroid matching post-processing na HDBSCAN
2. Handmatige toewijzing van resterende noise vanuit side panel
3. Verwijdering van categorieen en thema's (vereenvoudiging datamodel)
4. Paginering van bezwaren in side panel (15 per pagina)

## Datamodel

### Huidige keten (wordt verwijderd)

```
Project -> Thema (=categorie) -> Kernbezwaar -> Referentie
```

### Nieuwe keten

```
Project -> Kernbezwaar -> Referentie
```

### Database-wijzigingen

**Nieuwe kolom op `kernbezwaar_referentie`:**

```sql
ALTER TABLE kernbezwaar_referentie
  ADD COLUMN toewijzingsmethode VARCHAR(20) NOT NULL DEFAULT 'HDBSCAN';
```

Waarden: `HDBSCAN`, `CENTROID_FALLBACK`, `MANUEEL`.

**`kernbezwaar`** — vervang `thema_id` door `project_naam` (VARCHAR, NOT NULL).

**`geextraheerd_bezwaar`** — verwijder kolom `categorie`.

**`clustering_taak`** — verwijder kolom `categorie` (een taak per project).

**`thema`** tabel — verwijderen (data migreren: `project_naam` overnemen uit thema naar kernbezwaar).

Bestaande `score` kolom (Double, 0.0-1.0) wordt hergebruikt voor centroid-score bij alle toewijzingsmethoden.

## Backend-architectuur

### Nieuwe bestanden

**`ToewijzingsMethode.java`** (enum in `kernbezwaar` package)

```java
public enum ToewijzingsMethode {
  HDBSCAN, CENTROID_FALLBACK, MANUEEL
}
```

**`CentroidMatchingService.java`** (in `clustering` package)

- Puur vectorwiskunde, geen DB-afhankelijkheden
- `wijsNoiseToe(clusters, noiseItems, origineleEmbeddings, threshold)` — retourneert:
  - Uitgebreide clusters (noise boven drempel toegevoegd)
  - Resterende noise (onder drempel)
  - Per bezwaarId: toewijzingsmethode + score
- `berekenTop5Suggesties(bezwaarEmbedding, clusterCentroids)` — lijst van (kernbezwaarId, score), gesorteerd op score

### Gewijzigde bestanden

| Bestand | Wijziging |
|---|---|
| `KernbezwaarReferentieEntiteit` | Nieuw veld `toewijzingsmethode` |
| `IndividueelBezwaarReferentie` | Nieuw veld `toewijzingsmethode` |
| `ClusteringConfig` | Nieuw veld `centroidMatchingThreshold` (default 0.85) |
| `KernbezwaarService` | `clusterCategorie()` wordt `clusterProject()`, centroid matching na HDBSCAN, retourneert `List<Kernbezwaar>` |
| `KernbezwaarController` | Response wordt `List<Kernbezwaar>`, nieuwe endpoints voor toewijzing en suggesties |
| `ClusteringTaak` | Verwijder `categorie` |
| `ClusteringTaakService` | Vereenvoudigd (geen per-categorie logica) |
| `ClusteringTaakController` | Vereenvoudigd |

### Verwijderde bestanden

| Bestand | Reden |
|---|---|
| `ThemaEntiteit` | Geen thema's meer |
| `ThemaRepository` | Geen thema's meer |
| `Thema` record | Niet meer nodig |
| `ThemasResponse` (indien aanwezig) | Vervangen door `KernbezwarenResponse` |

### Nieuwe API-endpoints

- `GET /api/v1/projects/{naam}/noise/{bezwaarId}/suggesties` — top-5 kernbezwaar-suggesties met scores (on-the-fly berekend)
- `PUT /api/v1/projects/{naam}/referenties/{id}/toewijzing` — handmatige toewijzing van noise-bezwaar naar kernbezwaar

## Clustering flow

```
1. Gebruiker klikt "Cluster" voor project
2. ClusteringTaak aangemaakt (zonder categorie)
3. ClusteringWorker pikt taak op -> KernbezwaarService.clusterProject()
4. Embeddings genereren (indien nodig)
5. HDBSCAN clustering -> ClusteringResultaat(clusters, noiseIds)
6. CentroidMatchingService.wijsNoiseToe():
   a. Bereken centroid per cluster (originele 1024D vectoren)
   b. Per noise-bezwaar: cosine-similarity met alle centroids
   c. Hoogste score >= 0.85 -> verplaats naar cluster (CENTROID_FALLBACK)
   d. Hoogste score < 0.85 -> blijft noise
7. Opslaan: kernbezwaren + referenties (met toewijzingsmethode + score)
8. Resterende noise -> "Niet-geclusterde bezwaren" kernbezwaar
```

## Handmatige toewijzing flow

```
1. Gebruiker opent side panel voor "Niet-geclusterde bezwaren"
2. Klikt "Toewijzen" bij een noise-bezwaar
3. Frontend: GET /api/v1/projects/{naam}/noise/{bezwaarId}/suggesties
4. Backend: laad embedding bezwaar + centroids alle kernbezwaren -> top 5
5. Dropdown toont top 5 met scores
6. Gebruiker selecteert -> PUT /api/v1/projects/{naam}/referenties/{id}/toewijzing
7. Backend: verplaats referentie (kernbezwaarId update, methode=MANUEEL)
8. Leeg noise-kernbezwaar -> automatisch opruimen
9. Frontend: herlaad side panel
```

## Frontend-wijzigingen

### Side panel

1. **Paginering** — 15 bezwaren per pagina met vorige/volgende navigatie
2. **Toewijzingsmethode-badge** per bezwaar:
   - `HDBSCAN` — grijze pill (standaard, subtiel)
   - `CENTROID_FALLBACK` — oranje pill ("Automatisch toegewezen")
   - `MANUEEL` — blauwe pill ("Handmatig toegewezen")
3. **Score** — centroid-score voor alle methoden

### Noise bezwaren (handmatige toewijzing)

- "Toewijzen" knop per noise-referentie
- Dropdown met top-5 kernbezwaren + scores (on-the-fly geladen)
- Na toewijzing: side panel ververst

### Clustering-knoppen (vereenvoudigd)

- Een "Cluster" knop per project (i.p.v. per categorie)
- Een "Verwijder clustering" knop
- Geen categorie-tabs meer

## Configuratie

```yaml
bezwaarschriften:
  clustering:
    centroid-matching-threshold: 0.85
```

## Centroid-berekening voor top-5 suggesties

Centroids worden on-the-fly berekend: voor elk kernbezwaar worden de embeddings van alle gekoppelde bezwaren opgehaald en het gemiddelde genomen. Acceptabel omdat:
- Klein aantal kernbezwaren (typisch <50)
- Embeddings al in de DB (`geextraheerd_bezwaar.embedding_passage/samenvatting`)
- Geen AI-call, puur vectorwiskunde
