# Design: Verwijder individueel bezwaar

**Datum:** 2026-03-03

## Probleemstelling

Een gebruiker wil een individueel bezwaar kunnen verwijderen uit een document — zowel manueel toegevoegde bezwaren als AI-gegenereerde bezwaren. Bij verwijdering van een AI-bezwaar verschijnt de ✍️-signalisatie (manueel aangepast). Als het laatste manuele bezwaar verwijderd wordt, verdwijnt de ✍️-signalisatie.

## Huidige situatie

- Backend: `DELETE /api/v1/projects/{naam}/extracties/{bestandsnaam}/bezwaren/{bezwaarId}` bestaat, maar geeft 403 terug bij niet-manuele bezwaren.
- Frontend: verwijder-knop (`vl-button icon="bin" error ghost`) verschijnt al bij manuele bezwaren in de manueel-tab, zonder bevestigingsmodal.

## Design

### Backend

**`ExtractieTaakService.verwijderManueelBezwaar` → hernoemd naar `verwijderBezwaar`**

- Verwijder de guard die `IllegalStateException` gooit voor niet-manuele bezwaren.
- Logica voor `heeftManueel` na verwijdering:
  - Bezwaar was **niet manueel** (AI) → `heeftManueel = true` (gebruiker heeft document handmatig gewijzigd)
  - Bezwaar was **manueel** → herbereken: kijk of er nog manuele bezwaren overblijven

**`ExtractieController`**

- Hernoem aanroep naar `verwijderBezwaar`.
- Verwijder de `catch (IllegalStateException)` + 403-response (niet meer van toepassing).

### Frontend

**Bevestigingsmodal**

- Component: `vl-modal` (uit `@domg-wc/components/block/modal`)
- Title: "Bezwaar verwijderen"
- Body: "Weet je zeker dat je dit bezwaar wil verwijderen? Deze actie kan niet ongedaan gemaakt worden."
- Knoppen: `<vl-button error="">Verwijderen</vl-button>` + annuleer (cancel-slot van `vl-modal`)
- Eén gedeelde modal-instantie per side-sheet, aangemaakt bij initialisatie.
- State: modal slaat `bezwaarId`, `projectNaam`, `bestandsnaam` en actieve tab op bij openen.

**Verwijder-knop**

- Zichtbaar op **alle** bezwaren in beide tabs (automatisch én manueel).
- Stijl: `<vl-button icon="bin" error="" ghost="" label="Bezwaar verwijderen">` — identiek aan de document-verwijderknop.
- Klik opent de modal (geen directe DELETE meer).

**Na verwijdering**

- Herlaad side-panel via `toonExtractieDetails(projectNaam, bestandsnaam, actieveTab)`.
- `bezwaar-gewijzigd` custom event dispatchen (tabel bijwerken).

## Niet in scope

- Soft delete / undo-functionaliteit
- Verwijderen van passages uit de database bij bezwaarverwijdering
