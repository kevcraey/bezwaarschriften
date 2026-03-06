# Design: Centroid-matching en sortering van geclusterde bezwaren

## Probleem

Na clustering worden individuele bezwaren toegewezen aan kernbezwaren, maar er is geen indicatie van hoe goed elk bezwaar past bij zijn cluster. De dossierbehandelaar kan niet zien welke bezwaren representatief zijn en welke randgevallen.

## Oplossing

Per kernbezwaar een centroid berekenen (gemiddelde van 1024D bge-m3 embeddings), voor elk bezwaar de cosinus-gelijkenis met die centroid berekenen, de score persistent opslaan, en via de API gesorteerd (aflopend) aanbieden.

## Datamodel

- Nieuwe kolom `score` (DOUBLE PRECISION, nullable) op tabel `kernbezwaar_referentie`
- Flyway migratie
- `KernbezwaarReferentieEntiteit.score` (Double)
- `IndividueelBezwaarReferentie` record uitbreiden met `Integer scorePercentage`

## Berekening (tijdens clustering)

In `KernbezwaarService.clusterCategorie()`:

1. Centroid wordt al berekend via `berekenOrigineleCentroid()` -- dit verandert niet
2. **Nieuw**: na centroid-berekening, voor elk bezwaar in het cluster `cosinusGelijkenis(bezwaar.embedding, centroid)` berekenen
3. Scores meegeven aan `bouwReferenties()` als `Map<Long, Double>` (bezwaarId -> score)
4. Score opslaan in `KernbezwaarReferentieEntiteit`
5. Noise-bezwaren: score = `null` (geen centroid)

## API

- `geefKernbezwaren()`: referenties per kernbezwaar sorteren op score (aflopend, nulls last)
- `IndividueelBezwaarReferentie` bevat `scorePercentage` (Math.round(score * 100))
- Geen nieuwe endpoints nodig

## Frontend

- In `_toonPassages()`: bij elke passage-groep de hoogste `scorePercentage` tonen
- Passage-groepen sorteren op hoogste score (aflopend)
- Score weergave als compact label (bv. "84%")

## Wat niet verandert

- `berekenOrigineleCentroid()` en `cosinusGelijkenis()` methodes
- Selectie van representatief bezwaar (`vindDichtstBijCentroid`)
- Clustering-flow, worker, taken
- Kernbezwaar-lijst sortering (blijft op aantal passages)
