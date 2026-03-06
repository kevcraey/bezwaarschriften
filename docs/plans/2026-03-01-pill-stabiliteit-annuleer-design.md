# Design: Stabiele pills + annuleer-functionaliteit

## Probleem

1. **Pill-flicker**: status-pills voor "wachtend"/"bezig" taken veranderen subtiel van breedte doordat de timer-tekst elke seconde wijzigt (bv. "0:09" → "0:10").
2. **Geen annuleer-optie**: stuck of ongewenste taken kunnen niet gestopt worden. Gebruiker wil een kruisje op actieve pills om de verwerking te annuleren met bevestiging.

## Design

### Pill-flicker fix (CSS-only)

Twee CSS-regels op de pill:
- `font-variant-numeric: tabular-nums` — maakt alle cijfers even breed in het huidige font.
- `min-width: 180px; display: inline-block;` — vangt het geval dat minuten van 1 naar 2 cijfers gaan.

Geen HTML-wijzigingen nodig.

### Annuleer-functionaliteit

#### Backend

**ExtractieWorker** — bijhouden van Future-referenties:
- `ConcurrentHashMap<Long, Future<?>> lopendeTaken` — vult bij `executor.submit()`, verwijdert na voltooiing.
- Nieuwe methode `annuleerTaak(Long taakId)` — haalt Future op en roept `future.cancel(true)` aan (thread interrupt).

**ExtractieTaakService** — nieuwe methode:
- `annuleerTaak(Long taakId)` — verwijdert taak uit DB. Als status BEZIG: roept `ExtractieWorker.annuleerTaak()` aan. Stuurt WebSocket-notificatie.

**ExtractieController** — nieuw endpoint:
- `DELETE /api/v1/projects/{naam}/extracties/{taakId}` — roept service aan, retourneert 204 No Content.

#### Frontend

**bezwaarschriften-bezwaren-tabel.js:**
- Kruisje (`x`) als klikbare button in de pill, alleen voor "wachtend"/"bezig" status.
- Klik dispatcht custom event `annuleer-taak` met `{bestandsnaam, taakId}`.

**bezwaarschriften-project-selectie.js:**
- Luistert naar `annuleer-taak` event.
- Opent bevestigingsmodal: "Weet je zeker dat je de verwerking van [bestandsnaam] wil annuleren?"
- Bij bevestiging: `DELETE` API-call → WebSocket update ververst tabel automatisch.

## Buiten scope

- Batch-annuleren (meerdere taken tegelijk) — kan later via bestaande selectie-mechanisme.
- Nieuwe status `GEANNULEERD` — taak wordt simpelweg verwijderd, bestand keert terug naar "onverwerkt".
