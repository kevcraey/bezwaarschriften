# Design: Verwerken-knop voor onafgeronde extracties

**Datum:** 2026-03-01

## Probleem

De huidige "Opnieuw proberen"-knop start alleen gefaalde extracties opnieuw. Documenten met status `TODO` (nog niet verwerkt) worden genegeerd. De gebruiker wil één knop die alle onafgeronde extracties start.

## Ontwerp

### Gedrag

- Knop verschijnt als er bestanden zijn met status `todo` of `fout`
- Knop verdwijnt als alle bestanden status `extractie-klaar` hebben (of `niet ondersteund`)
- Knoptekst: "Verwerken (N)" waar N = aantal `todo` + `fout`
- Documenten met status `niet ondersteund` worden genegeerd

### Backend

**Nieuw endpoint:** `POST /api/v1/projects/{naam}/extracties/verwerken`

**Service-methode:** `verwerkOnafgeronde(projectNaam)` in `ExtractieTaakService`:
1. Reset alle `FOUT`-taken naar `WACHTEND` (bestaande herplan-logica: maxPogingen+1, foutmelding wissen, timestamps resetten)
2. Zoek alle `TODO`-documenten op via `ProjectService.geefBezwaren()` en maak nieuwe `ExtractieTaak`-entiteiten aan (bestaande `indienen`-logica)
3. Return totaal aantal ingeplande taken

**Verwijderd:** `POST /extracties/retry` endpoint en `herplanGefaaldeTaken()` methode

### Frontend

**Bestand:** `bezwaarschriften-project-selectie.js`

- Visibility-logica: `hidden = count(todo + fout) === 0`
- Tekst: `Verwerken (N)`
- API-call: `POST /api/v1/projects/{projectNaam}/extracties/verwerken`
- Methode hernoemd: `_retryGefaaldeExtracties` → `_verwerkOnafgeronde`
