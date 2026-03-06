# Design: Per-bestand extractie met gemockte bezwaren

**Datum:** 2026-02-28

## Context

De bezwarentabel toont bestanden met hun status (TODO, EXTRACTIE_KLAAR, FOUT, NIET_ONDERSTEUND). Momenteel verwerkt de "Verwerk alles"-knop alle TODO-bestanden in batch. Deze feature voegt per-bestand extractie toe via een knop in de tabelrij, met gemockte resultaten.

## Beslissingen

- Tabel verschijnt bij projectselectie (GET), niet pas na verwerking
- "Verwerk alles"-knop blijft bestaan naast de per-rij knop
- Nieuw dedicated endpoint per bestand: `POST /api/v1/projects/{naam}/bezwaren/{bestandsnaam}/extraheer`
- Extractie-knop altijd zichtbaar, maar disabled bij EXTRACTIE_KLAAR en NIET_ONDERSTEUND
- Resultaat voorlopig gemockt (niet echte AI-extractie)

## Backend wijzigingen

### Nieuw endpoint

```
POST /api/v1/projects/{naam}/bezwaren/{bestandsnaam}/extraheer
→ BezwaarBestandDto
```

Retourneert het resultaat voor dat ene bestand na extractie.

### DTO uitbreiding

`BezwaarBestandDto` krijgt extra veld `aantalBezwaren` (Integer, nullable). Null voor bestanden waar nog geen extractie is uitgevoerd.

### Mock-logica in ProjectService

| Bestand (volgorde) | Gedrag |
|---|---|
| 1e .txt bestand | Altijd succes, 3 bezwaren |
| 2e .txt bestand | Faalt 1 op 2 keer (toggle), 4 bezwaren bij succes |
| 3e .txt bestand | Altijd succes, 5 bezwaren |
| Overige .txt | Altijd succes, 2 bezwaren |

### Status-flow

```
TODO → extraheer → EXTRACTIE_KLAAR (+ aantalBezwaren) of FOUT
FOUT → opnieuw extraheer → EXTRACTIE_KLAAR (+ aantalBezwaren) of FOUT
```

### VerwerkingsResultaat uitbreiden

Het bestaande in-memory record `VerwerkingsResultaat` krijgt een extra veld `aantalBezwaren`.

## Frontend wijzigingen

### bezwaarschriften-bezwaren-tabel.js

- Nieuwe kolom **"Aantal bezwaren"**: toont getal bij EXTRACTIE_KLAAR, leeg bij andere statussen
- Nieuwe kolom **"Acties"**: bevat extractie-knop met analyse-icoon
- Knop enabled bij TODO en FOUT, disabled bij EXTRACTIE_KLAAR en NIET_ONDERSTEUND
- Bij klik: dispatcht custom event `extraheer-bezwaar` met `bestandsnaam` in detail

### bezwaarschriften-project-selectie.js

- Bij projectselectie: roept `GET /api/v1/projects/{naam}/bezwaren` aan en toont tabel
- Luistert naar `extraheer-bezwaar` event → roept `POST .../extraheer` aan
- Update alleen de betreffende rij in de bezwaren-array na response
- "Verwerk alles"-knop blijft: roept bestaand batch endpoint aan, update hele tabel
