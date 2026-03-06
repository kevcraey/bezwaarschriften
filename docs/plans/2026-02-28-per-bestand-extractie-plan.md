# Per-bestand extractie met gemockte bezwaren — Implementatieplan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Gebruikers kunnen per bestand een extractie triggeren via een knop in de tabelrij. Het resultaat (aantal bezwaren) wordt gemockt weergegeven. De tabel verschijnt bij projectselectie.

**Architecture:** Bestaande hexagonale structuur uitbreiden. `BezwaarBestand` record en `VerwerkingsResultaat` krijgen `aantalBezwaren` veld. Nieuw endpoint `POST /{naam}/bezwaren/{bestandsnaam}/extraheer` in `ProjectController`. Frontend tabel krijgt acties-kolom en aantal-bezwaren-kolom.

**Tech Stack:** Java 21, Spring Boot 2.7.x, JUnit 5 + Mockito, Lit-based web components (@domg-wc)

---

### Task 1: BezwaarBestand record uitbreiden met aantalBezwaren

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestand.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java`

**Step 1: Pas bestaande test aan die aantalWoorden verifieert, en voeg assertie toe voor aantalBezwaren**

In `ProjectServiceTest.java`, update `verwerkingZetStatusOpExtractieKlaarBijSucces` om ook `aantalBezwaren` te verifieren (moet `null` zijn bij batch-verwerking, want de mock-extractie is nog niet aangeroepen):

```java
@Test
void verwerkingZetStatusOpExtractieKlaarBijSucces() throws Exception {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.txt"));
    when(ingestiePoort.leesBestand(Path.of("input", "windmolens", "bezwaren", "bezwaar-001.txt")))
        .thenReturn(new Brondocument("dit is een test tekst", "bezwaar-001.txt",
            "input/windmolens/bezwaren/bezwaar-001.txt", Instant.now()));

    var resultaat = service.verwerk("windmolens");

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).status()).isEqualTo(BezwaarBestandStatus.EXTRACTIE_KLAAR);
    assertThat(resultaat.get(0).aantalWoorden()).isEqualTo(5);
    assertThat(resultaat.get(0).aantalBezwaren()).isNull();
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl app -Dtest=ProjectServiceTest#verwerkingZetStatusOpExtractieKlaarBijSucces -Denforcer.skip=true`
Expected: Compilation error — `aantalBezwaren()` method does not exist on `BezwaarBestand`.

**Step 3: Voeg aantalBezwaren toe aan BezwaarBestand record**

Update `BezwaarBestand.java`:

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Representeert een bezwaarbestand met zijn verwerkingsstatus.
 *
 * @param bestandsnaam Naam van het bezwaarbestand
 * @param status Huidige verwerkingsstatus
 * @param aantalWoorden Aantal woorden in het bestand (null als niet verwerkt)
 * @param aantalBezwaren Aantal geextraheerde bezwaren (null als niet geextraheerd)
 */
public record BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status,
    Integer aantalWoorden, Integer aantalBezwaren) {

  public BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status) {
    this(bestandsnaam, status, null, null);
  }
}
```

Update `VerwerkingsResultaat` in `ProjectService.java` (regel 128):

```java
private record VerwerkingsResultaat(BezwaarBestandStatus status, Integer aantalWoorden,
    Integer aantalBezwaren) { }
```

Update alle plekken in `ProjectService` waar `VerwerkingsResultaat` wordt aangemaakt (3 plekken):
- Regel 72 (`geefBezwaren`): `new BezwaarBestand(naam, resultaat.status(), resultaat.aantalWoorden(), resultaat.aantalBezwaren())`
- Regel 103 (succes): `new VerwerkingsResultaat(BezwaarBestandStatus.EXTRACTIE_KLAAR, aantalWoorden, null)`
- Regel 108 (fout): `new VerwerkingsResultaat(BezwaarBestandStatus.FOUT, null, null)`

**Step 4: Run test to verify it passes**

Run: `mvn test -pl app -Dtest=ProjectServiceTest -Denforcer.skip=true`
Expected: All tests in ProjectServiceTest pass.

**Step 5: Commit**

```
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestand.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java
git commit -m "feat: voeg aantalBezwaren toe aan BezwaarBestand record"
```

---

### Task 2: DTO uitbreiden en extraheer-endpoint toevoegen aan controller

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectControllerTest.java`

**Step 1: Schrijf falende test voor het extraheer-endpoint**

Voeg twee tests toe aan `ProjectControllerTest.java`:

```java
@Test
void extraheertBezwarenVoorEnkelBestand() throws Exception {
    when(projectService.extraheer("windmolens", "bezwaar-001.txt"))
        .thenReturn(new BezwaarBestand("bezwaar-001.txt",
            BezwaarBestandStatus.EXTRACTIE_KLAAR, 150, 3));

    mockMvc.perform(post("/api/v1/projects/windmolens/bezwaren/bezwaar-001.txt/extraheer"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bestandsnaam").value("bezwaar-001.txt"))
        .andExpect(jsonPath("$.status").value("extractie-klaar"))
        .andExpect(jsonPath("$.aantalWoorden").value(150))
        .andExpect(jsonPath("$.aantalBezwaren").value(3));
}

@Test
void geeft404VoorOnbekendProjectBijExtraheer() throws Exception {
    when(projectService.extraheer("bestaat-niet", "bezwaar.txt"))
        .thenThrow(new ProjectNietGevondenException("bestaat-niet"));

    mockMvc.perform(post("/api/v1/projects/bestaat-niet/bezwaren/bezwaar.txt/extraheer"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messages[0].code").value("project.not-found"));
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl app -Dtest=ProjectControllerTest#extraheertBezwarenVoorEnkelBestand -Denforcer.skip=true`
Expected: Compilation error — `extraheer` method does not exist on `ProjectService`.

**Step 3: Voeg aantalBezwaren toe aan DTO en maak extraheer-endpoint**

In `ProjectController.java`:

1. Update `BezwaarBestandDto` (regel 88):
```java
record BezwaarBestandDto(String bestandsnaam, String status, Integer aantalWoorden,
    Integer aantalBezwaren) {}
```

2. Update de mapping in `BezwarenResponse.van()` (regel 72-73):
```java
.map(b -> new BezwaarBestandDto(b.bestandsnaam(), statusNaarString(b.status()),
    b.aantalWoorden(), b.aantalBezwaren()))
```

3. Voeg nieuw endpoint toe na het `verwerk` endpoint:
```java
/**
 * Extraheert individuele bezwaren uit een enkel bestand.
 *
 * @param naam Projectnaam
 * @param bestandsnaam Naam van het bezwaarbestand
 * @return Bijgewerkt bezwaarbestand met extractieresultaat
 */
@PostMapping("/{naam}/bezwaren/{bestandsnaam}/extraheer")
public ResponseEntity<BezwaarBestandDto> extraheer(
    @PathVariable String naam, @PathVariable String bestandsnaam) {
  var resultaat = projectService.extraheer(naam, bestandsnaam);
  return ResponseEntity.ok(new BezwaarBestandDto(
      resultaat.bestandsnaam(), statusNaarString(resultaat.status()),
      resultaat.aantalWoorden(), resultaat.aantalBezwaren()));
}
```

Let op: `statusNaarString` is een private methode in het inner record `BezwarenResponse`. Maak het een **static methode op controller-niveau** zodat zowel `BezwarenResponse.van()` als `extraheer()` het kunnen gebruiken. Verplaats de methode uit `BezwarenResponse` naar `ProjectController`:

```java
private static String statusNaarString(BezwaarBestandStatus status) {
    return switch (status) {
      case TODO -> "todo";
      case EXTRACTIE_KLAAR -> "extractie-klaar";
      case FOUT -> "fout";
      case NIET_ONDERSTEUND -> "niet ondersteund";
    };
}
```

4. Voeg stub methode toe aan `ProjectService.java` (zodat het compileert):
```java
public BezwaarBestand extraheer(String projectNaam, String bestandsnaam) {
    throw new UnsupportedOperationException("Nog niet geimplementeerd");
}
```

**Step 4: Run test to verify it fails (nu op runtime, niet compilatie)**

Run: `mvn test -pl app -Dtest=ProjectControllerTest#extraheertBezwarenVoorEnkelBestand -Denforcer.skip=true`
Expected: PASS — de MockBean mockt de service, dus de stub wordt niet aangeroepen.

**Step 5: Run alle controller tests**

Run: `mvn test -pl app -Dtest=ProjectControllerTest -Denforcer.skip=true`
Expected: Alle tests passen. De bestaande tests moeten nog werken omdat `aantalBezwaren` nullable is en de bestaande tests niet op dat veld asserteren.

**Step 6: Commit**

```
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectControllerTest.java
git commit -m "feat: voeg extraheer-endpoint toe aan ProjectController"
```

---

### Task 3: Mock-extractie implementeren in ProjectService

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java`

**Step 1: Schrijf falende tests voor extraheer in ProjectServiceTest**

Voeg toe aan `ProjectServiceTest.java`:

```java
@Test
void extraheerEersteBestandGeeft3Bezwaren() throws Exception {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.txt", "bezwaar-002.txt"));
    when(ingestiePoort.leesBestand(Path.of("input", "windmolens", "bezwaren", "bezwaar-001.txt")))
        .thenReturn(new Brondocument("test tekst hier", "bezwaar-001.txt",
            "input/windmolens/bezwaren/bezwaar-001.txt", Instant.now()));

    var resultaat = service.extraheer("windmolens", "bezwaar-001.txt");

    assertThat(resultaat.status()).isEqualTo(BezwaarBestandStatus.EXTRACTIE_KLAAR);
    assertThat(resultaat.aantalBezwaren()).isEqualTo(3);
    assertThat(resultaat.aantalWoorden()).isEqualTo(3);
}

@Test
void extraheerDerdeBestandGeeft5Bezwaren() throws Exception {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("a.txt", "b.txt", "c.txt"));
    when(ingestiePoort.leesBestand(Path.of("input", "windmolens", "bezwaren", "c.txt")))
        .thenReturn(new Brondocument("tekst", "c.txt",
            "input/windmolens/bezwaren/c.txt", Instant.now()));

    var resultaat = service.extraheer("windmolens", "c.txt");

    assertThat(resultaat.status()).isEqualTo(BezwaarBestandStatus.EXTRACTIE_KLAAR);
    assertThat(resultaat.aantalBezwaren()).isEqualTo(5);
}

@Test
void extraheerTweedeBestandFaaltBijEerstePoging() throws Exception {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("a.txt", "b.txt", "c.txt"));
    when(ingestiePoort.leesBestand(Path.of("input", "windmolens", "bezwaren", "b.txt")))
        .thenReturn(new Brondocument("tekst", "b.txt",
            "input/windmolens/bezwaren/b.txt", Instant.now()));

    var resultaat = service.extraheer("windmolens", "b.txt");

    // Eerste poging faalt altijd (toggle start op false)
    assertThat(resultaat.status()).isEqualTo(BezwaarBestandStatus.FOUT);
    assertThat(resultaat.aantalBezwaren()).isNull();
}

@Test
void extraheerTweedeBestandSlaagdBijTweedePoging() throws Exception {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("a.txt", "b.txt", "c.txt"));
    when(ingestiePoort.leesBestand(Path.of("input", "windmolens", "bezwaren", "b.txt")))
        .thenReturn(new Brondocument("tekst", "b.txt",
            "input/windmolens/bezwaren/b.txt", Instant.now()));

    service.extraheer("windmolens", "b.txt"); // eerste poging faalt
    var resultaat = service.extraheer("windmolens", "b.txt"); // tweede poging slaagt

    assertThat(resultaat.status()).isEqualTo(BezwaarBestandStatus.EXTRACTIE_KLAAR);
    assertThat(resultaat.aantalBezwaren()).isEqualTo(4);
}

@Test
void extraheerNietOndersteundBestandGeeftFout() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bijlage.pdf"));

    var resultaat = service.extraheer("windmolens", "bijlage.pdf");

    assertThat(resultaat.status()).isEqualTo(BezwaarBestandStatus.NIET_ONDERSTEUND);
    assertThat(resultaat.aantalBezwaren()).isNull();
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl app -Dtest=ProjectServiceTest#extraheerEersteBestandGeeft3Bezwaren -Denforcer.skip=true`
Expected: FAIL — `UnsupportedOperationException` van de stub.

**Step 3: Implementeer extraheer-methode in ProjectService**

Vervang de stub in `ProjectService.java` met de volledige implementatie:

```java
/** Toggle voor bestand-2 mock: faalt bij false, slaagt bij true. */
private final Map<String, Boolean> tweedeBestandToggle = new ConcurrentHashMap<>();

/**
 * Extraheert individuele bezwaren uit een enkel bestand (gemockt).
 *
 * <p>Mock-gedrag:
 * <ul>
 *   <li>1e .txt bestand in project: altijd 3 bezwaren</li>
 *   <li>2e .txt bestand: faalt 1 op 2 keer (toggle), 4 bezwaren bij succes</li>
 *   <li>3e .txt bestand: altijd 5 bezwaren</li>
 *   <li>Overige .txt: altijd 2 bezwaren</li>
 * </ul>
 *
 * @param projectNaam Naam van het project
 * @param bestandsnaam Naam van het bezwaarbestand
 * @return Bijgewerkt bezwaarbestand met extractieresultaat
 */
public BezwaarBestand extraheer(String projectNaam, String bestandsnaam) {
    var alleBestandsnamen = projectPoort.geefBestandsnamen(projectNaam);
    var projectStatussen = statusRegister.computeIfAbsent(projectNaam,
        k -> new ConcurrentHashMap<>());

    if (!isTxtBestand(bestandsnaam)) {
      return new BezwaarBestand(bestandsnaam, BezwaarBestandStatus.NIET_ONDERSTEUND);
    }

    // Bepaal volgorde-index van dit bestand onder de .txt bestanden
    var txtBestanden = alleBestandsnamen.stream()
        .filter(this::isTxtBestand)
        .toList();
    int index = txtBestanden.indexOf(bestandsnaam);

    var bestandsPad = inputFolder.resolve(projectNaam).resolve("bezwaren")
        .resolve(bestandsnaam);
    try {
      var brondocument = ingestiePoort.leesBestand(bestandsPad);
      var aantalWoorden = telWoorden(brondocument.tekst());
      Integer aantalBezwaren = bepaalAantalBezwaren(projectNaam, bestandsnaam, index);

      if (aantalBezwaren == null) {
        // Mock-fout voor 2e bestand
        projectStatussen.put(bestandsnaam,
            new VerwerkingsResultaat(BezwaarBestandStatus.FOUT, null, null));
        LOGGER.warn("Mock-extractie gefaald voor '{}' in project '{}'",
            bestandsnaam, projectNaam);
        return new BezwaarBestand(bestandsnaam, BezwaarBestandStatus.FOUT, null, null);
      }

      projectStatussen.put(bestandsnaam,
          new VerwerkingsResultaat(BezwaarBestandStatus.EXTRACTIE_KLAAR,
              aantalWoorden, aantalBezwaren));
      LOGGER.info("Extractie '{}' in project '{}': {} bezwaren gevonden",
          bestandsnaam, projectNaam, aantalBezwaren);
      return new BezwaarBestand(bestandsnaam, BezwaarBestandStatus.EXTRACTIE_KLAAR,
          aantalWoorden, aantalBezwaren);
    } catch (Exception e) {
      projectStatussen.put(bestandsnaam,
          new VerwerkingsResultaat(BezwaarBestandStatus.FOUT, null, null));
      LOGGER.warn("Fout bij extractie van '{}' in project '{}': {}",
          bestandsnaam, projectNaam, e.getMessage());
      return new BezwaarBestand(bestandsnaam, BezwaarBestandStatus.FOUT, null, null);
    }
}

private Integer bepaalAantalBezwaren(String projectNaam, String bestandsnaam, int index) {
    return switch (index) {
      case 0 -> 3;
      case 1 -> {
        String toggleKey = projectNaam + ":" + bestandsnaam;
        boolean slaagt = tweedeBestandToggle.compute(toggleKey,
            (k, v) -> v == null ? false : !v);
        yield slaagt ? 4 : null;
      }
      case 2 -> 5;
      default -> 2;
    };
}
```

**Step 4: Run tests to verify they pass**

Run: `mvn test -pl app -Dtest=ProjectServiceTest -Denforcer.skip=true`
Expected: All ProjectServiceTest tests pass.

**Step 5: Commit**

```
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java
git commit -m "feat: implementeer gemockte per-bestand extractie in ProjectService"
```

---

### Task 4: Run alle backend tests

**Step 1: Run de volledige test suite**

Run: `mvn test -pl app -Denforcer.skip=true`
Expected: Alle tests (inclusief ActuatorIntegrationTest, HealthIntegrationTest) slagen.

**Step 2: Als er falende tests zijn, fix ze**

Mogelijke problemen:
- `ProjectControllerTest` tests die `BezwaarBestand` aanmaken met de oude 2-argument constructor moeten nog werken (de convenience constructor is behouden).
- Jackson serialisatie van het nieuwe `aantalBezwaren` veld: zal automatisch als `null` geserialiseerd worden.

**Step 3: Commit fixes als nodig**

---

### Task 5: Frontend — tabel uitbreiden met acties-kolom en aantal-bezwaren-kolom

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

**Step 1: Voeg kolommen toe en extractie-knop**

Vervang de volledige inhoud van `bezwaarschriften-bezwaren-tabel.js`:

```javascript
import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlTableComponent} from '@domg-wc/components/block/table/vl-table.component.js';
import {VlButtonComponent} from '@domg-wc/components/atom/button/vl-button.component.js';
import {vlGlobalStyles} from '@domg-wc/styles';

registerWebComponents([VlTableComponent, VlButtonComponent]);

const STATUS_LABELS = {
  'todo': 'Te verwerken',
  'extractie-klaar': 'Extractie klaar',
  'fout': 'Fout',
  'niet ondersteund': 'Niet ondersteund',
};

export class BezwaarschriftenBezwarenTabel extends BaseHTMLElement {
  static get properties() {
    return {
      bezwaren: {type: Array},
    };
  }

  constructor() {
    super(`
      <style>
        ${vlGlobalStyles}
        .extractie-knop:disabled {
          opacity: 0.4;
          cursor: not-allowed;
        }
      </style>
      <vl-table>
        <table>
          <thead>
            <tr>
              <th>Bestandsnaam</th>
              <th>Status</th>
              <th>Aantal bezwaren</th>
              <th>Acties</th>
            </tr>
          </thead>
          <tbody id="tabel-body"></tbody>
        </table>
      </vl-table>
    `);
    this.__bezwaren = [];
  }

  set bezwaren(waarde) {
    this.__bezwaren = waarde || [];
    this._renderRijen();
  }

  get bezwaren() {
    return this.__bezwaren;
  }

  connectedCallback() {
    super.connectedCallback();
    this._renderRijen();
  }

  _renderRijen() {
    const tbody = this.shadowRoot && this.shadowRoot.querySelector('#tabel-body');
    if (!tbody) return;

    if (this.__bezwaren.length === 0) {
      tbody.innerHTML = '<tr><td colspan="4">Geen bestanden gevonden</td></tr>';
      return;
    }

    tbody.innerHTML = this.__bezwaren
        .map((b) => {
          const kanExtraheren = b.status === 'todo' || b.status === 'fout';
          const disabled = kanExtraheren ? '' : 'disabled';
          const aantalBezwaren = b.aantalBezwaren != null ? b.aantalBezwaren : '';
          return `<tr>
            <td>${this._escapeHtml(b.bestandsnaam)}</td>
            <td>${this._formatStatus(b)}</td>
            <td>${aantalBezwaren}</td>
            <td>
              <button class="extractie-knop" data-bestandsnaam="${this._escapeHtml(b.bestandsnaam)}" ${disabled}
                title="Extraheer bezwaren">&#128269;</button>
            </td>
          </tr>`;
        })
        .join('');

    // Event listeners koppelen aan knoppen
    tbody.querySelectorAll('.extractie-knop:not([disabled])').forEach((knop) => {
      knop.addEventListener('click', (e) => {
        const bestandsnaam = e.target.dataset.bestandsnaam;
        this.dispatchEvent(new CustomEvent('extraheer-bezwaar', {
          detail: {bestandsnaam},
          bubbles: true,
          composed: true,
        }));
      });
    });
  }

  _formatStatus(b) {
    const label = STATUS_LABELS[b.status] || this._escapeHtml(b.status);
    if (b.status === 'extractie-klaar' && b.aantalWoorden != null) {
      return `${label} (${b.aantalWoorden} woorden)`;
    }
    return label;
  }

  _escapeHtml(str) {
    const div = document.createElement('div');
    div.appendChild(document.createTextNode(str));
    return div.innerHTML;
  }
}

defineWebComponent(BezwaarschriftenBezwarenTabel, 'bezwaarschriften-bezwaren-tabel');
```

**Step 2: Handmatig testen**

Start de app en verifieer:
- Tabel toont 4 kolommen: Bestandsnaam, Status, Aantal bezwaren, Acties
- Extractie-knop (vergrootglas-icoon) zichtbaar in elke rij
- Knop disabled bij `extractie-klaar` en `niet ondersteund`
- Knop enabled bij `todo` en `fout`

**Step 3: Commit**

```
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "feat: voeg acties-kolom en aantal-bezwaren-kolom toe aan bezwarentabel"
```

---

### Task 6: Frontend — tabel tonen bij projectselectie + per-bestand extractie afhandelen

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js`

**Step 1: Pas project-selectie component aan**

Update `bezwaarschriften-project-selectie.js`. De wijzigingen:

1. Bij projectselectie: GET `/bezwaren` aanroepen en tabel tonen
2. Luisteren naar `extraheer-bezwaar` event
3. Per-bestand POST naar `/extraheer` endpoint
4. Rij in bezwaren-array updaten na response

```javascript
import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlSelectComponent} from '@domg-wc/components/form/select/vl-select.component.js';
import {VlButtonComponent} from '@domg-wc/components/atom/button/vl-button.component.js';
import {vlGlobalStyles, vlGridStyles} from '@domg-wc/styles';
import './bezwaarschriften-bezwaren-tabel.js';

registerWebComponents([VlSelectComponent, VlButtonComponent]);

export class BezwaarschriftenProjectSelectie extends BaseHTMLElement {
  static get properties() {
    return {
      __projecten: {state: true},
      __geselecteerdProject: {state: true},
      __bezwaren: {state: true},
      __bezig: {state: true},
      __fout: {state: true},
    };
  }

  constructor() {
    super(`
      <style>
        ${vlGlobalStyles}
        ${vlGridStyles}
      </style>
      <div id="selectie-wrapper">
        <vl-select id="project-select" placeholder="Kies een project..."></vl-select>
        <vl-button id="verwerk-knop">Verwerk alles</vl-button>
        <p id="fout-melding" hidden></p>
      </div>
      <bezwaarschriften-bezwaren-tabel id="bezwaren-tabel" hidden></bezwaarschriften-bezwaren-tabel>
    `);
    this.__projecten = [];
    this.__geselecteerdProject = null;
    this.__bezwaren = [];
    this.__bezig = false;
    this.__fout = null;
  }

  connectedCallback() {
    super.connectedCallback();
    this._laadProjecten();
    this._koppelEventListeners();
  }

  _laadProjecten() {
    fetch('/api/v1/projects')
        .then((response) => {
          if (!response.ok) throw new Error('Ophalen projecten mislukt');
          return response.json();
        })
        .then((data) => {
          this.__projecten = data.projecten;
          const selectEl = this.shadowRoot && this.shadowRoot.querySelector('#project-select');
          if (selectEl) {
            selectEl.options = this.__projecten.map((naam) => ({value: naam, label: naam}));
          }
        })
        .catch(() => {
          this._toonFout('Projecten konden niet worden geladen.');
        });
  }

  _koppelEventListeners() {
    const selectEl = this.shadowRoot && this.shadowRoot.querySelector('#project-select');
    const verwerkKnop = this.shadowRoot && this.shadowRoot.querySelector('#verwerk-knop');

    if (selectEl) {
      selectEl.addEventListener('vl-change', (e) => {
        this._verbergFout();
        const naam = e.detail.value;
        this.__geselecteerdProject = naam || null;
        if (naam) {
          this._laadBezwaren(naam);
        } else {
          this.__bezwaren = [];
          this._verbergTabel();
        }
      });
    }

    if (verwerkKnop) {
      verwerkKnop.addEventListener('vl-click', () => {
        if (this.__bezig) return;
        if (!this.__geselecteerdProject) {
          this._toonFout('Selecteer eerst een project.');
          return;
        }
        this._verwerkBezwaren(this.__geselecteerdProject);
      });
    }

    // Luister naar extraheer-bezwaar events van de tabel
    this.shadowRoot.addEventListener('extraheer-bezwaar', (e) => {
      if (!this.__geselecteerdProject) return;
      this._extraheerBestand(this.__geselecteerdProject, e.detail.bestandsnaam);
    });
  }

  _laadBezwaren(projectNaam) {
    this._verbergFout();
    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/bezwaren`)
        .then((response) => {
          if (!response.ok) throw new Error('Ophalen bezwaren mislukt');
          return response.json();
        })
        .then((data) => {
          this.__bezwaren = data.bezwaren;
          this._werkTabelBij();
        })
        .catch(() => {
          this._toonFout('Bezwaren konden niet worden geladen.');
        });
  }

  _extraheerBestand(projectNaam, bestandsnaam) {
    this._verbergFout();
    const url = `/api/v1/projects/${encodeURIComponent(projectNaam)}/bezwaren/${encodeURIComponent(bestandsnaam)}/extraheer`;
    fetch(url, {method: 'POST'})
        .then((response) => {
          if (!response.ok) throw new Error('Extractie mislukt');
          return response.json();
        })
        .then((bijgewerkt) => {
          // Update de betreffende rij in de bezwaren-array
          this.__bezwaren = this.__bezwaren.map((b) =>
            b.bestandsnaam === bijgewerkt.bestandsnaam ? bijgewerkt : b,
          );
          this._werkTabelBij();
        })
        .catch(() => {
          this._toonFout(`Extractie van '${bestandsnaam}' is mislukt.`);
        });
  }

  _verwerkBezwaren(projectNaam) {
    this._verbergFout();
    this._zetBezig(true);
    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/verwerk`, {method: 'POST'})
        .then((response) => {
          if (!response.ok) throw new Error('Verwerking mislukt');
          return response.json();
        })
        .then((data) => {
          this.__bezwaren = data.bezwaren;
          this._werkTabelBij();
        })
        .catch(() => {
          this._toonFout('Verwerking kon niet worden gestart.');
        })
        .finally(() => {
          this.__bezig = false;
        });
  }

  _werkTabelBij() {
    const tabel = this.shadowRoot && this.shadowRoot.querySelector('#bezwaren-tabel');
    if (tabel) {
      tabel.bezwaren = this.__bezwaren;
      tabel.hidden = false;
    }
  }

  _verbergTabel() {
    const tabel = this.shadowRoot && this.shadowRoot.querySelector('#bezwaren-tabel');
    if (tabel) tabel.hidden = true;
  }

  _zetBezig(bezig) {
    this.__bezig = bezig;
    const knop = this.shadowRoot && this.shadowRoot.querySelector('#verwerk-knop');
    if (knop) knop.disabled = bezig;
  }

  _toonFout(bericht) {
    const foutEl = this.shadowRoot && this.shadowRoot.querySelector('#fout-melding');
    if (foutEl) {
      foutEl.textContent = bericht;
      foutEl.hidden = false;
    }
  }

  _verbergFout() {
    const foutEl = this.shadowRoot && this.shadowRoot.querySelector('#fout-melding');
    if (foutEl) foutEl.hidden = true;
  }
}

defineWebComponent(BezwaarschriftenProjectSelectie, 'bezwaarschriften-project-selectie');
```

**Step 2: Handmatig testen**

Start de app en verifieer:
1. Selecteer een project → tabel verschijnt meteen met bestanden in TODO status
2. Klik extractie-knop op bestand 1 → status wordt "Extractie klaar", aantal bezwaren toont "3"
3. Klik extractie-knop op bestand 2 → status wordt "Fout", knop blijft enabled
4. Klik nogmaals op bestand 2 → status wordt "Extractie klaar", aantal bezwaren toont "4"
5. Klik extractie-knop op bestand 3 → status wordt "Extractie klaar", aantal bezwaren toont "5"
6. "Verwerk alles" knop werkt nog steeds als batch

**Step 3: Commit**

```
git add webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "feat: toon tabel bij projectselectie en handel per-bestand extractie af"
```

---

### Task 7: End-to-end verificatie en finaal commit

**Step 1: Run alle backend tests**

Run: `mvn test -pl app -Denforcer.skip=true`
Expected: Alle tests slagen.

**Step 2: Start de applicatie en test handmatig**

Run de app via IntelliJ ("Alles starten") en verifieer het volledige scenario:
1. Projectselectie toont tabel
2. Per-bestand extractie werkt
3. Retry bij fout werkt
4. "Verwerk alles" werkt
5. Deselecteren verbergt tabel

**Step 3: Finale commit als er nog ongecommitte wijzigingen zijn**
