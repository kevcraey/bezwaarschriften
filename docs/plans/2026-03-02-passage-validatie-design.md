# Passage-validatie bij extractie

## Probleem

De LLM retourneert passages (citaten) uit bezwaarschriften, maar deze komen niet altijd exact overeen met de originele tekst. De gebruiker moet kunnen zien welke passages niet teruggevonden konden worden in het brondocument.

## Beslissingen

- **Timing:** validatie in `markeerKlaar`, direct na extractie
- **Matching:** whitespace-normalisatie + exacte substring-check + 90% fuzzy fallback
- **Status:** boolean `passageGevonden` per bezwaar + boolean `heeftOpmerkingen` op taak
- **UI:** waarschuwingstekst per niet-gevonden bezwaar, gesorteerd bovenaan

## Datamodel

### Database (Flyway migratie)

**`geextraheerd_bezwaar`** — nieuw veld:
```sql
ALTER TABLE geextraheerd_bezwaar ADD COLUMN passage_gevonden BOOLEAN NOT NULL DEFAULT TRUE;
```

**`extractie_taak`** — nieuw veld:
```sql
ALTER TABLE extractie_taak ADD COLUMN heeft_opmerkingen BOOLEAN NOT NULL DEFAULT FALSE;
```

### Java entiteiten

**`GeextraheerdBezwaarEntiteit`** — `passageGevonden` boolean (default `true`)

**`ExtractieTaak`** — `heeftOpmerkingen` boolean (default `false`)

### DTOs

**`ExtractieDetailDto.BezwaarDetail`** — uitbreiden met `boolean passageGevonden`

**`ExtractieTaakDto`** — uitbreiden met `boolean heeftOpmerkingen`

## PassageValidator

Nieuwe `@Component` klasse in `project` package.

### Algoritme

1. **Normaliseer** document- en passage-tekst: alle whitespace comprimeren naar enkele spatie, trim
2. **Exacte substring-check** op genormaliseerde tekst
   - Gevonden → `passageGevonden = true`
   - Meerdere voorkomens → eerste match telt
3. **Fuzzy fallback** als exact niet gevonden:
   - Sliding window over genormaliseerde documenttekst (venstergrootte = lengte passage)
   - Bereken karakter-overlap similarity ratio
   - Drempel: **90%**
   - Gevonden → `passageGevonden = true`
4. Noch exact, noch fuzzy → `passageGevonden = false`

### Interface

```java
public record ValidatieResultaat(int aantalNietGevonden) {}

public ValidatieResultaat valideer(
    List<GeextraheerdBezwaarEntiteit> bezwaren,
    Map<Integer, String> passageMap,
    String documentTekst);
```

Zet `passageGevonden` direct op de meegegeven entiteiten (by reference).

## Integratie in markeerKlaar

Volgorde in `ExtractieTaakService.markeerKlaar()`:

1. Bouw passage-entiteiten op (bestaand)
2. Bouw bezwaar-entiteiten op (bestaand)
3. **Nieuw:** haal brondocument-tekst op via `ProjectPoort.geefBestandsPad()` + `IngestiePoort.leesBestand()`
4. **Nieuw:** roep `PassageValidator.valideer()` aan → zet `passageGevonden` per bezwaar
5. **Nieuw:** als `aantalNietGevonden > 0` → `taak.setHeeftOpmerkingen(true)`
6. Opslaan passages, bezwaren, taak (bestaand)
7. Notificatie (bestaand)

**Error handling:** als brondocument niet leesbaar is → log warning, sla validatie over, markeer alle passages als gevonden.

**Nieuwe dependencies voor `ExtractieTaakService`:**
- `ProjectPoort` (voor `geefBestandsPad`)
- `IngestiePoort` (voor `leesBestand`)
- `PassageValidator`

## Frontend

### Side-panel (bezwaarschriften-bezwaren-tabel.js)

**Sortering:** bezwaren met `passageGevonden === false` bovenaan.

**Waarschuwing per bezwaar:**
```html
<div class="bezwaar-waarschuwing">⚠️ Passage kon niet gevonden worden</div>
```

CSS:
```css
.bezwaar-waarschuwing {
  color: #a5673f;
  font-size: 0.85rem;
  margin-bottom: 0.25rem;
}
```

**Titel:** bij opmerkingen: `"bestand.txt - 5 bezwaren gevonden (met opmerkingen)"`

### API response

```json
{
  "bestandsnaam": "bezwaar-1.txt",
  "aantalBezwaren": 5,
  "bezwaren": [
    { "samenvatting": "...", "passage": "...", "passageGevonden": false },
    { "samenvatting": "...", "passage": "...", "passageGevonden": true }
  ]
}
```

## Testplan

### Backend unit tests

**PassageValidatorTest:**
- Exacte match gevonden (1x voorkomen)
- Exacte match gevonden (meerdere keren)
- Niet gevonden (0 keer)
- Whitespace-normalisatie (extra spaties/newlines)
- Fuzzy match: 91% similarity → gevonden
- Fuzzy match: 89% similarity → niet gevonden
- Lege passage tekst
- Lege document tekst

**ExtractieTaakServiceTest uitbreiding:**
- `markeerKlaar` alle passages gevonden → `heeftOpmerkingen = false`
- `markeerKlaar` 1+ niet gevonden → `heeftOpmerkingen = true`, `passageGevonden = false`
- `markeerKlaar` onleesbaar document → graceful degradation

**ExtractieControllerTest uitbreiding:**
- GET details retourneert `passageGevonden` in response

### Frontend tests (Cypress component testing)

Cypress component testing opzetten in `webapp/` naar voorbeeld van pasberekening project (`/Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/domg/repos/pasberekening/webapp/`).

**Setup (eenmalig):**
- `cypress` + `cypress-lit` als devDependencies
- `cypress.config.js` met component testing configuratie (webpack bundler, `includeShadowDom: true`)
- `cypress/support/component-index.html` + `cypress/support/component.js`
- npm script `"test": "cypress run --component"`

**Patroon:** `cy.mount(html\`<bezwaarschriften-bezwaren-tabel></bezwaarschriften-bezwaren-tabel>\`)` met stubbed fetch responses.

**test/bezwaarschriften-bezwaren-tabel-extractie-details.cy.js:**
- Niet-gevonden bezwaren staan bovenaan in side-panel
- Waarschuwingstekst (`.bezwaar-waarschuwing`) zichtbaar bij `passageGevonden: false`
- Geen waarschuwing bij `passageGevonden: true`
- Titel bevat "(met opmerkingen)" bij niet-gevonden passages
- Alle passages gevonden → geen waarschuwingen, standaard titel
