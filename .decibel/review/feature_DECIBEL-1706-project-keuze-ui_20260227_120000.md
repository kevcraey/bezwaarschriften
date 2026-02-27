# Code Review

**Branch:** feature/DECIBEL-1706-project-keuze-ui → main
**Reviewer:** Claude (AI-assisted review)
**Datum:** 2026-02-27
**JIRA Ticket:** DECIBEL-1706

## 💬 Approve with Suggestions

### Samenvatting
- **Totaal Issues Gevonden:** 8 (🔴 Blocking: 0, 🟡 Important: 4, 🟢 Nitpick: 4)
- **Files Gewijzigd:** 21 files

### Overzicht Wijzigingen
- **Nieuwe Files:**
  - `app/src/main/java/.../config/GlobalExceptionHandler.java`
  - `app/src/main/java/.../project/BestandssysteemProjectAdapter.java`
  - `app/src/main/java/.../project/BezwaarBestand.java`
  - `app/src/main/java/.../project/BezwaarBestandStatus.java`
  - `app/src/main/java/.../project/ProjectController.java`
  - `app/src/main/java/.../project/ProjectNietGevondenException.java`
  - `app/src/main/java/.../project/ProjectPoort.java`
  - `app/src/main/java/.../project/ProjectService.java`
  - `app/src/main/java/.../project/package-info.java`
  - `app/src/test/java/.../project/BestandssysteemProjectAdapterTest.java`
  - `app/src/test/java/.../project/BezwaarBestandStatusTest.java`
  - `app/src/test/java/.../project/BezwaarBestandTest.java`
  - `app/src/test/java/.../project/ProjectControllerTest.java`
  - `app/src/test/java/.../project/ProjectServiceTest.java`
  - `app/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
  - `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`
  - `webapp/src/js/bezwaarschriften-project-selectie.js`
- **Gewijzigde Files:**
  - `app/src/main/java/.../config/SecurityConfiguration.java` (volledige herschrijving naar Spring Boot 3.x API)
  - `app/src/main/resources/application.yml` (grote cleanup legacy config)
  - `webapp/src/js/bezwaarschriften-landingspagina.js`
  - `webapp/src/js/index.js`

### Positieve Punten
- **Hexagonale architectuur correct toegepast:** `ProjectPoort` als port, `BestandssysteemProjectAdapter` als adapter — clean separation of concerns
- **Sterke test coverage:** 7 service-tests, 8 adapter-tests, 6 controller-tests inclusief foutpaden en edge cases
- **Idempotente verwerking:** reeds verwerkte bestanden worden correct overgeslagen (AC5)
- **Resource-safe bestandsoperaties:** `Files.list()` consistent in try-with-resources, geen resource leaks
- **XSS-preventie in tabel:** `_escapeHtml()` via `createTextNode` voor bestandsnamen is de correcte implementatie
- **URL-encoding in frontend:** `encodeURIComponent(projectNaam)` consistent toegepast
- **Null-guards in shadow DOM:** `this.shadowRoot && this.shadowRoot.querySelector(...)` overal aanwezig

### Code Quality Issues (Prioritized)

#### 1. Path traversal risico in BestandssysteemProjectAdapter
- **File:** `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapter.java:166-172`
- **Severity:** 🟡 Important
- **Impact:** Een projectNaam zoals `../sibling-folder` kan buiten de inputFolder navigeren. Spring MVC filtert `..` in path variables doorgaans, maar defense-in-depth vereist expliciete canonicalisatie in de adapter zelf.
- **Huidige Code:**
  ```java
  var projectPad = inputFolder.resolve(projectNaam);
  if (!Files.isDirectory(projectPad)) {
      throw new ProjectNietGevondenException(projectNaam);
  }
  ```
- **Probleem:** `inputFolder.resolve("../other")` navigeert buiten de inputFolder zonder dat dit gedetecteerd wordt door de `isDirectory`-check.
- **Oplossing:** Voeg normalisatie en grenscontrole toe na resolve.
- **Voorgestelde Code:**
  ```java
  var projectPad = inputFolder.resolve(projectNaam).normalize();
  if (!projectPad.startsWith(inputFolder.normalize())) {
      throw new ProjectNietGevondenException(projectNaam);
  }
  if (!Files.isDirectory(projectPad)) {
      throw new ProjectNietGevondenException(projectNaam);
  }
  ```

#### 2. `/api/v1/**` volledig openbaar — security regressie
- **File:** `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/SecurityConfiguration.java:17-18`
- **Severity:** 🟡 Important
- **Impact:** De oude config vereiste `BezwaarschriftenGebruiker` voor alle endpoints. Nu zijn alle API-endpoints inclusief de schrijfoperatie (`POST /verwerk`) anoniem toegankelijk. Voor de huidige dev-fase is dit pragmatisch, maar moet gedocumenteerd worden als technische schuld.
- **Huidige Code:**
  ```java
  .requestMatchers("/admin/health/**", "/admin/info", "/api/v1/**").permitAll()
  ```
- **Probleem:** Scope is te breed — heel `/api/v1/**` is publiek i.p.v. alleen de benodigde endpoints voor de huidige dev-fase.
- **Oplossing:** Voeg een TODO-comment toe die de intentie documenteert, zodat het niet ongemerkt in productie gaat.
- **Voorgestelde Code:**
  ```java
  // TODO DECIBEL-xxxx: API-authenticatie herstellen zodra OAuth opnieuw geconfigureerd is.
  // Tijdelijk permitAll voor dev-fase; vorige config vereiste BezwaarschriftenGebruiker.
  .requestMatchers("/admin/health/**", "/admin/info", "/api/v1/**").permitAll()
  ```

#### 3. Knop-toestand incorrect na succesvolle verwerking
- **File:** `webapp/src/js/bezwaarschriften-project-selectie.js:109-121`
- **Severity:** 🟡 Important
- **Impact:** Na succesvolle batchverwerking roept `finally` `_zetBezig(false)` aan, wat `knop.disabled = false` zet — ook als alle bezwaren nu EXTRACTIE_KLAAR zijn en de knop disabled zou moeten zijn. De knop blijft onterecht klikbaar.
- **Huidige Code:**
  ```javascript
  .then((data) => {
    this.__bezwaren = data.bezwaren;
    this._werkTabelBij();
    // _werkKnopBij() ontbreekt hier
  })
  .finally(() => {
    this._zetBezig(false); // zet disabled=false onvoorwaardelijk
  });
  ```
- **Probleem:** `_zetBezig(false)` overschrijft elke knop-staat die in de success-handler gezet zou worden.
- **Oplossing:** Roep `_werkKnopBij()` aan in de success-handler, en split `_zetBezig` op zodat de bezig-flag en de knop-state los van elkaar werken.
- **Voorgestelde Code:**
  ```javascript
  .then((data) => {
    this.__bezwaren = data.bezwaren;
    this._werkTabelBij();
    this._werkKnopBij(); // bepaalt correct of todo-items resteren
  })
  .finally(() => {
    this.__bezig = false;
    // knop-staat al correct gezet via _werkKnopBij() in success pad
  });
  ```

#### 4. XSS risico in status-fallback van bezwaren-tabel
- **File:** `webapp/src/js/bezwaarschriften-bezwaren-tabel.js:65`
- **Severity:** 🟡 Important
- **Impact:** Bestandsnaam wordt correct geëscaped via `_escapeHtml()`, maar de fallback voor onbekende statussen (`b.status`) niet. Als de API een onverwachte status retourneert met HTML-tekens, wordt die rauw in `innerHTML` gezet.
- **Huidige Code:**
  ```javascript
  <td>${STATUS_LABELS[b.status] || b.status}</td>
  ```
- **Probleem:** `b.status` is niet geëscaped in het fallback-pad.
- **Oplossing:** Escape ook de fallback.
- **Voorgestelde Code:**
  ```javascript
  <td>${STATUS_LABELS[b.status] || this._escapeHtml(b.status)}</td>
  ```

#### 5. Race condition bij snelle projectwisseling
- **File:** `webapp/src/js/bezwaarschriften-project-selectie.js:77-90`
- **Severity:** 🟢 Nitpick
- **Impact:** Twee `fetch`-aanroepen voor verschillende projecten kunnen in omgekeerde volgorde binnenkomen, waardoor de tabel verouderde data toont. In de praktijk onwaarschijnlijk maar wel een correct-by-construction probleem.
- **Oplossing:** `AbortController` gebruiken bij projectwisseling:
  ```javascript
  _laadBezwaren(projectNaam) {
    if (this._abortController) this._abortController.abort();
    this._abortController = new AbortController();
    fetch(`...`, { signal: this._abortController.signal })
      .catch((err) => {
        if (err.name === 'AbortError') return;
        this._toonFout('Bezwaren konden niet worden geladen.');
      });
  }
  ```

#### 6. Magic string `"bezwaren"` herhaald op twee plaatsen
- **File:** `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapter.java:171` en `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java:484`
- **Severity:** 🟢 Nitpick
- **Impact:** Als de map-naam ooit wijzigt, moet dit op twee plaatsen aangepast worden.
- **Oplossing:** Extraheer als constante in `ProjectPoort` of als package-level constante.

#### 7. Status "niet ondersteund" gebruikt spatie i.p.v. koppelteken
- **File:** `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java:315`
- **Severity:** 🟢 Nitpick
- **Impact:** API-contract is inconsistent: `todo`, `extractie-klaar`, `fout` gebruiken lowercase/koppelteken, `niet ondersteund` gebruikt een spatie. De frontend `STATUS_LABELS` verwacht nu een sleutel met spatie, wat ongebruikelijk is voor API-waarden.
- **Oplossing:** Overweeg `"niet-ondersteund"` voor consistentie. Vereist ook aanpassing in `STATUS_LABELS` in de frontend.

#### 8. In-memory statusRegister verliest staat bij herstart — ongedocumenteerd
- **File:** `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java:425`
- **Severity:** 🟢 Nitpick
- **Impact:** AC5 ("reeds verwerkte bestanden worden niet opnieuw verwerkt") geldt alleen binnen dezelfde JVM-sessie. Na herstart zijn alle statussen verdwenen. Dit is een bekende MVP-beperking maar staat nergens gedocumenteerd.
- **Oplossing:** Voeg een javadoc-comment toe: `// In-memory only: statussen worden gereset bij herstart (story 1.5 persisteert naar DB)`.
