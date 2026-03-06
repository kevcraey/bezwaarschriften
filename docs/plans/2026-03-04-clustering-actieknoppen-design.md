# Design: Clustering actieknoppen per status

**Datum:** 2026-03-04

## Doel

Actieknoppen altijd zichtbaar maken in de clustering-statusrij, met per status de juiste acties. `GEANNULEERD` status verwijderen — annuleren brengt een categorie terug naar `todo`.

## Actieknoppen per status

| Status | Knoppen | Icoon(en) | Stijl | Gedrag |
|--------|---------|-----------|-------|--------|
| `todo` | Play | `play-filled` | ghost | POST start |
| `wachtend` | Annuleer | `close` | ghost | DELETE → wordt `todo` |
| `bezig` | Annuleer | `close` | ghost | DELETE → wordt `todo` |
| `klaar` | Verwijder + Retry | `bin` + `synchronize` | ghost+error / ghost | Beide: modal als antwoorden bestaan, dan DELETE (+ POST bij retry) |
| `fout` | Retry | `synchronize` | ghost | POST start (overschrijft fout-taak) |

Bulk-knoppen "Cluster alles" en "Verwijder alles" blijven bestaan. "Verwijder alles" toont ook de bevestigingsmodal.

## Backend-wijzigingen

- `GEANNULEERD` verwijderen uit `ClusteringTaakStatus` enum
- `annuleer()` in `ClusteringTaakService`: taak verwijderen ipv status op GEANNULEERD zetten
- `ClusteringWorker` bij `ClusteringGeannuleerdException`: taak verwijderen ipv status wijzigen
- Database-migratie: bestaande `GEANNULEERD` rijen opruimen
- Geen nieuwe endpoints nodig

## Frontend-wijzigingen

- `_maakActieKnop()` wordt `_maakActieKnoppen()`: geeft meerdere knoppen terug voor `klaar`
- Nieuwe handler `_retryClustering(categorie)`: DELETE (met modal-flow) → POST start
- Annuleer-gedrag: na DELETE verschijnt categorie weer als `todo`
- `geannuleerd` case verwijderen uit rendering
- Vuilbak: `vl-button-icon` met `ghost` en `error` attributen

## Retry-flow (frontend-georkestreerd)

1. Gebruiker klikt retry bij `klaar`
2. Frontend checkt of bevestiging nodig is (DELETE met `bevestigd=false`)
3. Bij 409: modal tonen, bij bevestiging DELETE met `bevestigd=true`
4. Na succesvolle verwijdering: POST start clustering
5. Bij annulering modal: niets doen

## Testplan

### Backend

- Unit test: annuleren van `WACHTEND` taak verwijdert de taak
- Unit test: `ClusteringWorker` bij `ClusteringGeannuleerdException` verwijdert de taak
- Integratietest: annuleer → categorie verschijnt weer als `todo` in overzicht

### Frontend (Cypress)

- `todo` rij toont play-knop
- `wachtend` rij toont annuleer-knop
- `bezig` rij toont annuleer-knop
- `klaar` rij toont vuilbak + retry-knop
- `fout` rij toont retry-knop
- Retry bij `klaar`: modal verschijnt, bevestigen → verwijdering + herstart
- Retry bij `klaar`: modal verschijnt, annuleren → niets gebeurt
- Annuleer bij `wachtend`/`bezig`: categorie gaat terug naar `todo`
