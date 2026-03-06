# Retry gefaalde extracties + status-pills Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Voeg een retry-knop toe voor gefaalde extracties, vervang platte statusweergave door vl-pill componenten, en toon groene tab-titel met checkmark wanneer alle extracties succesvol zijn.

**Architecture:** Nieuw REST endpoint + service-methode hergebruiken de bestaande queue-infrastructuur (ExtractieWorker polling). Frontend wijzigingen in twee web components: bezwaarschriften-bezwaren-tabel (pills) en bezwaarschriften-project-selectie (retry-knop + tab-styling).

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, MockMvc tests, Mockito, DOMG Web Components (vl-pill, vl-button)

---

### Task 1: Repository-query voor gefaalde taken per project

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakRepository.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakRepositoryTest.java`

**Step 1: Write the failing test**

Voeg een test toe aan `ExtractieTaakRepositoryTest.java` die de nieuwe query valideert:

```java
@Test
void vindtGefaaldeTakenVoorProject() {
    // Bestaande test-fixture hergebruiken of aanmaken met testEntityManager
    // Maak 2 taken voor project "windmolens": 1x FOUT, 1x KLAAR
    // Maak 1 taak voor project "zonnepark": 1x FOUT
    // Verwacht: findByProjectNaamAndStatus("windmolens", FOUT) geeft alleen de FOUT-taak van windmolens
    var foutTaak = maakTaak("windmolens", "bezwaar-001.txt", ExtractieTaakStatus.FOUT);
    maakTaak("windmolens", "bezwaar-002.txt", ExtractieTaakStatus.KLAAR);
    maakTaak("zonnepark", "bezwaar-003.txt", ExtractieTaakStatus.FOUT);

    var resultaat = repository.findByProjectNaamAndStatus("windmolens", ExtractieTaakStatus.FOUT);

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).getBestandsnaam()).isEqualTo("bezwaar-001.txt");
}
```

Check de bestaande `ExtractieTaakRepositoryTest` voor de `maakTaak` helper en testEntityManager setup. Als die er niet is, maak een helper die een `ExtractieTaak` persist via testEntityManager.

**Step 2: Run test to verify it fails**

Run: `mvn test -pl app -Dtest=ExtractieTaakRepositoryTest#vindtGefaaldeTakenVoorProject -f pom.xml`
Expected: FAIL — methode `findByProjectNaamAndStatus` bestaat nog niet.

**Step 3: Write minimal implementation**

Voeg toe aan `ExtractieTaakRepository.java` (na de bestaande methodes, rond regel 54):

```java
List<ExtractieTaak> findByProjectNaamAndStatus(String projectNaam, ExtractieTaakStatus status);
```

Spring Data genereert de query automatisch op basis van de methodenaam.

**Step 4: Run test to verify it passes**

Run: `mvn test -pl app -Dtest=ExtractieTaakRepositoryTest#vindtGefaaldeTakenVoorProject -f pom.xml`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakRepository.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakRepositoryTest.java
git commit -m "feat: repository query voor gefaalde taken per project"
```

---

### Task 2: Service-methode herplanGefaaldeTaken

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java`

**Step 1: Write the failing tests**

Voeg twee tests toe aan `ExtractieTaakServiceTest.java`. Volg het patroon van bestaande tests — ze gebruiken `@Mock ExtractieTaakRepository`, `@Mock ExtractieNotificatie`, en een `maakTaak()` helper.

Test 1 — herplant gefaalde taken:

```java
@Test
void herplanGefaaldeTakenZetTerugNaarWachtend() {
    var taak1 = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.FOUT);
    taak1.setAantalPogingen(3);
    taak1.setMaxPogingen(3);
    taak1.setFoutmelding("Timeout");
    taak1.setAfgerondOp(Instant.now());
    var taak2 = maakTaak(2L, "windmolens", "bezwaar-002.txt", ExtractieTaakStatus.FOUT);
    taak2.setAantalPogingen(3);
    taak2.setMaxPogingen(3);
    taak2.setFoutmelding("Connection refused");
    taak2.setAfgerondOp(Instant.now());

    when(repository.findByProjectNaamAndStatus("windmolens", ExtractieTaakStatus.FOUT))
        .thenReturn(List.of(taak1, taak2));

    int aantal = service.herplanGefaaldeTaken("windmolens");

    assertThat(aantal).isEqualTo(2);
    assertThat(taak1.getStatus()).isEqualTo(ExtractieTaakStatus.WACHTEND);
    assertThat(taak1.getMaxPogingen()).isEqualTo(4);
    assertThat(taak1.getFoutmelding()).isNull();
    assertThat(taak1.getAfgerondOp()).isNull();
    assertThat(taak1.getVerwerkingGestartOp()).isNull();
    assertThat(taak2.getStatus()).isEqualTo(ExtractieTaakStatus.WACHTEND);
    verify(notificatie, times(2)).taakGewijzigd(any(ExtractieTaakDto.class));
}
```

Test 2 — geen gefaalde taken retourneert 0:

```java
@Test
void herplanGefaaldeTakenGeeftNulAlsGeenFouten() {
    when(repository.findByProjectNaamAndStatus("windmolens", ExtractieTaakStatus.FOUT))
        .thenReturn(List.of());

    int aantal = service.herplanGefaaldeTaken("windmolens");

    assertThat(aantal).isZero();
    verify(notificatie, org.mockito.Mockito.never()).taakGewijzigd(any());
}
```

**Step 2: Run tests to verify they fail**

Run: `mvn test -pl app -Dtest="ExtractieTaakServiceTest#herplanGefaaldeTakenZetTerugNaarWachtend+herplanGefaaldeTakenGeeftNulAlsGeenFouten" -f pom.xml`
Expected: FAIL — methode `herplanGefaaldeTaken` bestaat nog niet.

**Step 3: Write minimal implementation**

Voeg toe aan `ExtractieTaakService.java` (na de `geefLaatsteTaak` methode, rond regel 181):

```java
/**
 * Herplant alle gefaalde extractie-taken voor een project door ze terug te zetten
 * naar WACHTEND met 1 extra poging.
 *
 * @param projectNaam naam van het project
 * @return het aantal opnieuw ingeplande taken
 */
@Transactional
public int herplanGefaaldeTaken(String projectNaam) {
    var gefaaldeTaken = repository.findByProjectNaamAndStatus(projectNaam,
        ExtractieTaakStatus.FOUT);
    for (var taak : gefaaldeTaken) {
        taak.setMaxPogingen(taak.getMaxPogingen() + 1);
        taak.setStatus(ExtractieTaakStatus.WACHTEND);
        taak.setFoutmelding(null);
        taak.setVerwerkingGestartOp(null);
        taak.setAfgerondOp(null);
        repository.save(taak);
        notificatie.taakGewijzigd(ExtractieTaakDto.van(taak));
    }
    LOGGER.info("Herplant {} gefaalde taken voor project '{}'", gefaaldeTaken.size(), projectNaam);
    return gefaaldeTaken.size();
}
```

**Step 4: Run tests to verify they pass**

Run: `mvn test -pl app -Dtest="ExtractieTaakServiceTest#herplanGefaaldeTakenZetTerugNaarWachtend+herplanGefaaldeTakenGeeftNulAlsGeenFouten" -f pom.xml`
Expected: PASS

**Step 5: Run all service tests to check for regressions**

Run: `mvn test -pl app -Dtest=ExtractieTaakServiceTest -f pom.xml`
Expected: All PASS

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java
git commit -m "feat: herplanGefaaldeTaken service methode"
```

---

### Task 3: REST endpoint voor retry

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieController.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieControllerTest.java`

**Step 1: Write the failing test**

Voeg toe aan `ExtractieControllerTest.java`. Volg het bestaande patroon: `@WebMvcTest(ExtractieController.class)`, `@WithMockUser`, `@MockBean ExtractieTaakService`, csrf token bij POST.

```java
@Test
void retryHerplantGefaaldeTaken() throws Exception {
    when(extractieTaakService.herplanGefaaldeTaken("windmolens")).thenReturn(3);

    mockMvc.perform(post("/api/v1/projects/windmolens/extracties/retry")
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.aantalOpnieuwIngepland").value(3));
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl app -Dtest=ExtractieControllerTest#retryHerplantGefaaldeTaken -f pom.xml`
Expected: FAIL — 404, endpoint bestaat nog niet.

**Step 3: Write minimal implementation**

Voeg toe aan `ExtractieController.java` (na het `geefTaken` endpoint, rond regel 54):

```java
/**
 * Herplant alle gefaalde extractie-taken voor het opgegeven project.
 *
 * @param naam projectnaam
 * @return het aantal opnieuw ingeplande taken
 */
@PostMapping("/{naam}/extracties/retry")
public ResponseEntity<RetryResponse> retry(@PathVariable String naam) {
    int aantal = extractieTaakService.herplanGefaaldeTaken(naam);
    return ResponseEntity.ok(new RetryResponse(aantal));
}

/** Response DTO voor het retry-endpoint. */
record RetryResponse(int aantalOpnieuwIngepland) {}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -pl app -Dtest=ExtractieControllerTest#retryHerplantGefaaldeTaken -f pom.xml`
Expected: PASS

**Step 5: Run all controller tests**

Run: `mvn test -pl app -Dtest=ExtractieControllerTest -f pom.xml`
Expected: All PASS

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieController.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieControllerTest.java
git commit -m "feat: POST retry endpoint voor gefaalde extracties"
```

---

### Task 4: Status als vl-pill in bezwaren-tabel

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

**Step 1: Registreer VlPillComponent**

Voeg de import toe bovenaan het bestand (na de bestaande imports):

```javascript
import {VlPillComponent} from '@domg-wc/components/block/pill/vl-pill.component.js';
```

Voeg `VlPillComponent` toe aan de `registerWebComponents` call:

```javascript
registerWebComponents([VlTableComponent, VlPillComponent]);
```

**Step 2: Voeg STATUS_PILL_TYPES mapping toe**

Voeg direct na de bestaande `STATUS_LABELS` constante toe:

```javascript
const STATUS_PILL_TYPES = {
  'todo': '',
  'wachtend': 'warning',
  'bezig': 'warning',
  'extractie-klaar': 'success',
  'fout': 'error',
  'niet ondersteund': '',
};
```

**Step 3: Wijzig _formatStatus om vl-pill te retourneren**

Vervang de volledige `_formatStatus` methode (regels 173-198) door:

```javascript
_formatStatus(b, nu) {
    nu = nu || Date.now();
    const taakData = this.__takenData[b.bestandsnaam];
    let label;

    if (b.status === 'wachtend' && taakData && taakData.aangemaaktOp) {
      const wachtMs = nu - new Date(taakData.aangemaaktOp).getTime();
      label = `Wachtend (${this._formatTijd(wachtMs)})`;
    } else if (b.status === 'bezig' && taakData) {
      const wachtMs = taakData.verwerkingGestartOp && taakData.aangemaaktOp ?
        new Date(taakData.verwerkingGestartOp).getTime() -
            new Date(taakData.aangemaaktOp).getTime() :
        0;
      const verwerkMs = taakData.verwerkingGestartOp ?
        nu - new Date(taakData.verwerkingGestartOp).getTime() :
        0;
      label = `Bezig (${this._formatTijd(wachtMs)} + ${this._formatTijd(verwerkMs)})`;
    } else {
      label = STATUS_LABELS[b.status] || this._escapeHtml(b.status);
    }

    const type = STATUS_PILL_TYPES[b.status] || '';
    const typeAttr = type ? ` data-vl-type="${type}"` : '';
    const disabledAttr = b.status === 'niet ondersteund' ? ' data-vl-disabled' : '';
    return `<vl-pill${typeAttr}${disabledAttr}>${label}</vl-pill>`;
}
```

Let op: de `aantalWoorden` weergave bij extractie-klaar is bewust verwijderd (was testdata).

**Step 4: Wijzig _updateTimers voor pills**

In `_updateTimers()` (regels 161-171): de pill is nu een child element van de cel. Wijzig de update-regel:

```javascript
_updateTimers() {
    const nu = Date.now();
    this.__bezwaren.forEach((b) => {
      if (b.status !== 'wachtend' && b.status !== 'bezig') return;
      const cel = this.shadowRoot.querySelector(
          `.status-cel[data-bestandsnaam="${CSS.escape(b.bestandsnaam)}"]`,
      );
      if (!cel) return;
      const pill = cel.querySelector('vl-pill');
      if (pill) {
        pill.textContent = this._formatStatusLabel(b, nu);
      }
    });
}
```

**Step 5: Extract _formatStatusLabel helper**

Voeg een helper toe die alleen het label retourneert (zonder HTML), voor timer-updates:

```javascript
_formatStatusLabel(b, nu) {
    nu = nu || Date.now();
    const taakData = this.__takenData[b.bestandsnaam];

    if (b.status === 'wachtend' && taakData && taakData.aangemaaktOp) {
      const wachtMs = nu - new Date(taakData.aangemaaktOp).getTime();
      return `Wachtend (${this._formatTijd(wachtMs)})`;
    }

    if (b.status === 'bezig' && taakData) {
      const wachtMs = taakData.verwerkingGestartOp && taakData.aangemaaktOp ?
        new Date(taakData.verwerkingGestartOp).getTime() -
            new Date(taakData.aangemaaktOp).getTime() :
        0;
      const verwerkMs = taakData.verwerkingGestartOp ?
        nu - new Date(taakData.verwerkingGestartOp).getTime() :
        0;
      return `Bezig (${this._formatTijd(wachtMs)} + ${this._formatTijd(verwerkMs)})`;
    }

    return STATUS_LABELS[b.status] || b.status;
}
```

**Step 6: Test handmatig**

Start de applicatie en verifieer:
- "Te verwerken" → neutrale pill
- "Wachtend" → gele pill met timer
- "Bezig" → gele pill met timer
- "Extractie klaar" → groene pill
- "Fout" → rode pill
- "Niet ondersteund" → grijze (disabled) pill

Run: `mvn spring-boot:run -pl app -f pom.xml`

**Step 7: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "feat: status weergave als vl-pill in bezwaren-tabel"
```

---

### Task 5: Retry-knop in project-selectie UI

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js`

**Step 1: Voeg retry-knop toe aan template**

In de constructor template, voeg de knop toe na de "Extraheer geselecteerde" knop (na regel 37):

```html
<vl-button id="retry-knop" hidden>Opnieuw proberen</vl-button>
```

**Step 2: Voeg _werkRetryKnopBij methode toe**

Voeg een nieuwe methode toe (na `_werkDocumentenTabTitelBij`, rond regel 355):

```javascript
_werkRetryKnopBij() {
    const retryKnop = this.shadowRoot && this.shadowRoot.querySelector('#retry-knop');
    if (!retryKnop) return;
    const aantalFout = this.__bezwaren.filter((b) => b.status === 'fout').length;
    retryKnop.hidden = aantalFout === 0;
    if (aantalFout > 0) {
      retryKnop.textContent = `Opnieuw proberen (${aantalFout})`;
    }
}
```

**Step 3: Roep _werkRetryKnopBij aan vanuit bestaande update-punten**

Voeg `this._werkRetryKnopBij()` toe op dezelfde plekken waar `_werkDocumentenTabTitelBij()` wordt aangeroepen. Dat zijn 4 plekken:

1. `_verwerkTaakUpdate()` — na `this._werkDocumentenTabTitelBij()` (regel 139)
2. `_syncExtracties()` — na `this._werkDocumentenTabTitelBij()` (regel 159)
3. `_laadBezwaren()` — na `this._werkDocumentenTabTitelBij()` (regel 276)
4. `_dienExtractiesIn()` — na `this._werkDocumentenTabTitelBij()` (regel 311)

**Step 4: Voeg retry event listener toe in _koppelEventListeners**

Voeg toe in `_koppelEventListeners()`, na de query van bestaande knoppen (rond regel 189):

```javascript
const retryKnop = this.shadowRoot && this.shadowRoot.querySelector('#retry-knop');
```

En voeg de click handler toe (na de `extraheerKnop` handler, rond regel 228):

```javascript
if (retryKnop) {
    retryKnop.addEventListener('vl-click', () => {
        if (this.__bezig || !this.__geselecteerdProject) return;
        this._retryGefaaldeExtracties(this.__geselecteerdProject);
    });
}
```

**Step 5: Voeg _retryGefaaldeExtracties methode toe**

Voeg toe na `_dienExtractiesIn` (rond regel 320):

```javascript
_retryGefaaldeExtracties(projectNaam) {
    this._verbergFout();
    this._zetBezig(true);

    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/extracties/retry`, {
      method: 'POST',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Retry mislukt');
          return response.json();
        })
        .then((data) => {
          if (data.aantalOpnieuwIngepland > 0) {
            this._toonToast('success',
              `${data.aantalOpnieuwIngepland} extractie(s) opnieuw ingepland.`);
          }
        })
        .catch(() => {
          this._toonFout('Opnieuw proberen mislukt.');
        })
        .finally(() => {
          this._zetBezig(false);
        });
}
```

De WebSocket-updates zorgen ervoor dat de tabel automatisch bijwerkt en de retry-knop verdwijnt zodra er geen fouten meer zijn.

**Step 6: Test handmatig**

Verifieer:
- Retry-knop is onzichtbaar als er geen fouten zijn
- Retry-knop verschijnt met correct aantal als er fouten zijn
- Na klik: taken springen naar "Wachtend", knop verdwijnt
- Toast bevestigt het aantal herplande taken

**Step 7: Commit**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "feat: retry-knop voor gefaalde extracties"
```

---

### Task 6: Groene tab-titel met checkmark als alles klaar is

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js`

**Step 1: Wijzig _werkDocumentenTabTitelBij**

Pas de methode `_werkDocumentenTabTitelBij()` aan (regels 334-355). Voeg een check toe of alle documenten extractie-klaar zijn, en pas dan de stijl aan:

```javascript
_werkDocumentenTabTitelBij() {
    const pane = this.shadowRoot && this.shadowRoot.querySelector('#documenten');
    if (!pane || this.__bezwaren.length === 0) return;

    const totaal = this.__bezwaren.length;
    const aantalKlaar = this.__bezwaren.filter((b) => b.status === 'extractie-klaar').length;
    const aantalFout = this.__bezwaren.filter((b) => b.status === 'fout').length;
    const isBezig = this.__bezwaren.some(
        (b) => b.status === 'wachtend' || b.status === 'bezig',
    );
    const allesKlaar = aantalKlaar === totaal;

    let titel = `Documenten (${aantalKlaar}/${totaal})`;
    if (allesKlaar) titel = `\u2714\uFE0F Documenten (${totaal}/${totaal})`;
    if (isBezig) titel += ' \u23F3';
    if (aantalFout > 0) titel += ` \u26A0\uFE0F${aantalFout}`;

    const tabs = this.shadowRoot.querySelector('vl-tabs');
    const slot = tabs && tabs.shadowRoot &&
        tabs.shadowRoot.querySelector(`slot[name="documenten-title-slot"]`);
    if (slot) {
      slot.innerHTML = titel;
      slot.style.color = allesKlaar ? '#0e7c3a' : '';
    }
}
```

De kleurcode `#0e7c3a` is het standaard Vlaanderen groen uit het DOMG design system.

**Step 2: Test handmatig**

Verifieer:
- Als niet alle documenten klaar zijn: normale tab-titel
- Als alle documenten extractie-klaar zijn: groene tab-titel met checkmark prefix
- Als er daarna een retry plaatsvindt: titel gaat terug naar normaal

**Step 3: Commit**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "feat: groene tab-titel met checkmark als alle extracties klaar zijn"
```

---

### Task 7: Integratietest en opruimen

**Step 1: Run alle backend tests**

Run: `mvn test -pl app -f pom.xml`
Expected: All PASS

**Step 2: Start de applicatie en test het hele flow**

Run: `mvn spring-boot:run -pl app -f pom.xml`

Test het volledige scenario:
1. Selecteer een project
2. Extraheer bestanden (sommige zullen falen via mock)
3. Verifieer rode pills bij fouten
4. Verifieer dat retry-knop verschijnt met correct aantal
5. Klik retry — verifieer dat taken terug naar wachtend gaan
6. Wacht tot alle extracties klaar zijn — verifieer groene tab-titel met checkmark

**Step 3: Commit alles**

Als er fix-up wijzigingen waren, commit deze.
