# Projecten Landing Page - Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Voeg een projectenoverzicht-landingspagina toe met hash-routing, zodat gebruikers projecten kunnen bekijken, toevoegen, verwijderen en doorklikken naar een projectdetailpagina.

**Architecture:** Hash-based routing in `bezwaarschriften-landingspagina` (naar pasberekening-patroon). Nieuw `bezwaarschriften-projecten-overzicht` component met `vl-rich-data-table`. Backend uitgebreid met `POST /api/v1/projects` en `DELETE /api/v1/projects/{naam}`. `GET /api/v1/projects` response wordt objecten met `aantalDocumenten`.

**Tech Stack:** Java 21, Spring Boot 3.4, Web Components (LIT/BaseHTMLElement), @domg-wc v2.7.0, vl-rich-data-table

---

## Task 1: `ProjectPoort` — `maakProjectAan` methode

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectPoort.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapter.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapterTest.java`

**Step 1: Write the failing test**

In `BestandssysteemProjectAdapterTest.java`, voeg toe:

```java
@Test
void maaktProjectMapAanMetBezwarenSubmap() throws Exception {
    adapter.maakProjectAan("nieuw-project");

    assertThat(Files.isDirectory(inputFolder.resolve("nieuw-project"))).isTrue();
    assertThat(Files.isDirectory(inputFolder.resolve("nieuw-project").resolve("bezwaren"))).isTrue();
}

@Test
void maakProjectAan_gooitExceptionAlsProjectAlBestaat() throws Exception {
    Files.createDirectory(inputFolder.resolve("bestaand"));

    assertThrows(
        IllegalArgumentException.class,
        () -> adapter.maakProjectAan("bestaand")
    );
}

@Test
void maakProjectAan_gooitExceptionBijPathTraversal() {
    assertThrows(
        IllegalArgumentException.class,
        () -> adapter.maakProjectAan("../kwaadaardig")
    );
}
```

**Step 2: Run test to verify it fails**

Run: `cd app && mvn test -pl . -Dtest="BestandssysteemProjectAdapterTest#maaktProjectMapAanMetBezwarenSubmap+maakProjectAan_gooitExceptionAlsProjectAlBestaat+maakProjectAan_gooitExceptionBijPathTraversal" -Denforcer.skip=true`
Expected: FAIL — method `maakProjectAan` does not exist

**Step 3: Add method to interface**

In `ProjectPoort.java`, voeg toe na `geefProjecten()`:

```java
/**
 * Maakt een nieuw project aan met een bezwaren-submap.
 *
 * @param naam Naam van het project
 * @throws IllegalArgumentException Als het project al bestaat of de naam ongeldig is
 */
void maakProjectAan(String naam);
```

**Step 4: Write minimal implementation**

In `BestandssysteemProjectAdapter.java`, voeg toe:

```java
@Override
public void maakProjectAan(final String naam) {
    var projectPad = inputFolder.resolve(naam).normalize();
    if (!projectPad.startsWith(inputFolder.normalize())) {
        throw new IllegalArgumentException("Ongeldige projectnaam: " + naam);
    }
    if (Files.exists(projectPad)) {
        throw new IllegalArgumentException("Project bestaat al: " + naam);
    }
    try {
        Files.createDirectories(projectPad.resolve("bezwaren"));
        LOGGER.info("Project '{}' aangemaakt", naam);
    } catch (IOException e) {
        throw new RuntimeException("Kon project niet aanmaken: " + naam, e);
    }
}
```

**Step 5: Run tests to verify they pass**

Run: `cd app && mvn test -pl . -Dtest="BestandssysteemProjectAdapterTest" -Denforcer.skip=true`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectPoort.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapter.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapterTest.java
git commit -m "feat: voeg maakProjectAan toe aan ProjectPoort"
```

---

## Task 2: `ProjectPoort` — `verwijderProject` methode

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectPoort.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapter.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapterTest.java`

**Step 1: Write the failing test**

In `BestandssysteemProjectAdapterTest.java`, voeg toe:

```java
@Test
void verwijdertProjectMapRecursief() throws Exception {
    var bezwarenMap = inputFolder.resolve("oud-project").resolve("bezwaren");
    Files.createDirectories(bezwarenMap);
    Files.writeString(bezwarenMap.resolve("bestand.txt"), "inhoud");

    boolean result = adapter.verwijderProject("oud-project");

    assertThat(result).isTrue();
    assertThat(Files.exists(inputFolder.resolve("oud-project"))).isFalse();
}

@Test
void verwijderProject_geeftFalseAlsProjectNietBestaat() {
    boolean result = adapter.verwijderProject("bestaat-niet");

    assertThat(result).isFalse();
}

@Test
void verwijderProject_gooitExceptionBijPathTraversal() {
    assertThrows(
        IllegalArgumentException.class,
        () -> adapter.verwijderProject("../kwaadaardig")
    );
}
```

**Step 2: Run test to verify it fails**

Run: `cd app && mvn test -pl . -Dtest="BestandssysteemProjectAdapterTest#verwijdertProjectMapRecursief+verwijderProject_geeftFalseAlsProjectNietBestaat+verwijderProject_gooitExceptionBijPathTraversal" -Denforcer.skip=true`
Expected: FAIL — method does not exist

**Step 3: Add method to interface**

In `ProjectPoort.java`, voeg toe:

```java
/**
 * Verwijdert een project en alle bestanden erin recursief.
 *
 * @param naam Naam van het project
 * @return {@code true} als het project verwijderd is, {@code false} als het niet bestond
 * @throws IllegalArgumentException Als de naam ongeldig is (path traversal)
 */
boolean verwijderProject(String naam);
```

**Step 4: Write minimal implementation**

In `BestandssysteemProjectAdapter.java`, voeg imports toe:

```java
import java.util.Comparator;
import java.util.stream.Stream;
```

Voeg methode toe:

```java
@Override
public boolean verwijderProject(final String naam) {
    var projectPad = inputFolder.resolve(naam).normalize();
    if (!projectPad.startsWith(inputFolder.normalize())) {
        throw new IllegalArgumentException("Ongeldige projectnaam: " + naam);
    }
    if (!Files.isDirectory(projectPad)) {
        return false;
    }
    try (Stream<Path> walk = Files.walk(projectPad)) {
        walk.sorted(Comparator.reverseOrder()).forEach(pad -> {
            try {
                Files.delete(pad);
            } catch (IOException e) {
                throw new RuntimeException("Kon niet verwijderen: " + pad, e);
            }
        });
        LOGGER.info("Project '{}' verwijderd", naam);
        return true;
    } catch (IOException e) {
        throw new RuntimeException("Kon project niet verwijderen: " + naam, e);
    }
}
```

**Step 5: Run tests to verify they pass**

Run: `cd app && mvn test -pl . -Dtest="BestandssysteemProjectAdapterTest" -Denforcer.skip=true`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectPoort.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapter.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapterTest.java
git commit -m "feat: voeg verwijderProject toe aan ProjectPoort"
```

---

## Task 3: `ProjectService` — `maakProjectAan` en `verwijderProject`

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakRepository.java`

**Step 1: Write failing tests**

In `ProjectServiceTest.java`, voeg toe:

```java
@Test
void maakProjectAan_delegeertNaarPoort() {
    service.maakProjectAan("nieuw-project");

    verify(projectPoort).maakProjectAan("nieuw-project");
}

@Test
void verwijderProject_verwijdertExtractieTakenEnDelegeerNaarPoort() {
    when(projectPoort.verwijderProject("oud-project")).thenReturn(true);

    boolean result = service.verwijderProject("oud-project");

    assertThat(result).isTrue();
    verify(extractieTaakRepository).deleteByProjectNaam("oud-project");
    verify(projectPoort).verwijderProject("oud-project");
}

@Test
void verwijderProject_geeftFalseAlsProjectNietBestaat() {
    when(projectPoort.verwijderProject("bestaat-niet")).thenReturn(false);

    boolean result = service.verwijderProject("bestaat-niet");

    assertThat(result).isFalse();
    verify(extractieTaakRepository).deleteByProjectNaam("bestaat-niet");
}
```

**Step 2: Run tests to verify they fail**

Run: `cd app && mvn test -pl . -Dtest="ProjectServiceTest#maakProjectAan_delegeertNaarPoort+verwijderProject_verwijdertExtractieTakenEnDelegeerNaarPoort+verwijderProject_geeftFalseAlsProjectNietBestaat" -Denforcer.skip=true`
Expected: FAIL — methods do not exist

**Step 3: Add `deleteByProjectNaam` to repository**

In `ExtractieTaakRepository.java`, voeg toe:

```java
/**
 * Verwijdert alle extractie-taken voor een project.
 *
 * @param projectNaam de naam van het project
 */
void deleteByProjectNaam(String projectNaam);
```

**Step 4: Write implementation**

In `ProjectService.java`, voeg toe:

```java
/**
 * Maakt een nieuw project aan.
 *
 * @param naam Naam van het project
 * @throws IllegalArgumentException Als het project al bestaat
 */
public void maakProjectAan(String naam) {
    projectPoort.maakProjectAan(naam);
}

/**
 * Verwijdert een project, inclusief alle extractie-taken.
 *
 * @param naam Naam van het project
 * @return true als het project verwijderd is
 */
@Transactional
public boolean verwijderProject(String naam) {
    extractieTaakRepository.deleteByProjectNaam(naam);
    return projectPoort.verwijderProject(naam);
}
```

**Step 5: Run tests to verify they pass**

Run: `cd app && mvn test -pl . -Dtest="ProjectServiceTest" -Denforcer.skip=true`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakRepository.java
git commit -m "feat: voeg maakProjectAan en verwijderProject toe aan ProjectService"
```

---

## Task 4: `ProjectService` — `geefProjectenMetAantalDocumenten`

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java`

**Step 1: Write failing test**

In `ProjectServiceTest.java`, voeg toe:

```java
@Test
void geeftProjectenMetAantalDocumenten() {
    when(projectPoort.geefProjecten()).thenReturn(List.of("windmolens", "leeg-project"));
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar1.txt", "bezwaar2.txt", "bijlage.pdf"));
    when(projectPoort.geefBestandsnamen("leeg-project"))
        .thenReturn(List.of());

    var resultaat = service.geefProjectenMetAantalDocumenten();

    assertThat(resultaat).hasSize(2);
    assertThat(resultaat.get(0).naam()).isEqualTo("windmolens");
    assertThat(resultaat.get(0).aantalDocumenten()).isEqualTo(3);
    assertThat(resultaat.get(1).naam()).isEqualTo("leeg-project");
    assertThat(resultaat.get(1).aantalDocumenten()).isEqualTo(0);
}
```

**Step 2: Run test to verify it fails**

Run: `cd app && mvn test -pl . -Dtest="ProjectServiceTest#geeftProjectenMetAantalDocumenten" -Denforcer.skip=true`
Expected: FAIL — method does not exist

**Step 3: Write implementation**

In `ProjectService.java`, voeg record en methode toe:

```java
/**
 * Projectoverzicht met documentaantal.
 */
public record ProjectOverzicht(String naam, int aantalDocumenten) {}

/**
 * Geeft alle projecten met het aantal documenten per project.
 *
 * @return Lijst van project-overzichten
 */
public List<ProjectOverzicht> geefProjectenMetAantalDocumenten() {
    return projectPoort.geefProjecten().stream()
        .map(naam -> new ProjectOverzicht(naam, projectPoort.geefBestandsnamen(naam).size()))
        .toList();
}
```

**Step 4: Run tests**

Run: `cd app && mvn test -pl . -Dtest="ProjectServiceTest" -Denforcer.skip=true`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java
git commit -m "feat: voeg geefProjectenMetAantalDocumenten toe aan ProjectService"
```

---

## Task 5: `ProjectController` — aangepast GET + POST + DELETE endpoints

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectControllerTest.java`

**Step 1: Write failing tests**

In `ProjectControllerTest.java`, voeg imports toe:

```java
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.http.MediaType;
```

Voeg tests toe:

```java
@Test
void geeftProjectenMetAantalDocumentenTerug() throws Exception {
    when(projectService.geefProjectenMetAantalDocumenten()).thenReturn(List.of(
        new ProjectService.ProjectOverzicht("windmolens", 42),
        new ProjectService.ProjectOverzicht("zonnepanelen", 7)
    ));

    mockMvc.perform(get("/api/v1/projects"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projecten[0].naam").value("windmolens"))
        .andExpect(jsonPath("$.projecten[0].aantalDocumenten").value(42))
        .andExpect(jsonPath("$.projecten[1].naam").value("zonnepanelen"))
        .andExpect(jsonPath("$.projecten[1].aantalDocumenten").value(7));
}

@Test
void maaktProjectAan() throws Exception {
    mockMvc.perform(post("/api/v1/projects")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"naam\": \"nieuw-project\"}"))
        .andExpect(status().isCreated());

    verify(projectService).maakProjectAan("nieuw-project");
}

@Test
void maakProjectAan_geeft400AlsProjectAlBestaat() throws Exception {
    doThrow(new IllegalArgumentException("Project bestaat al: bestaand"))
        .when(projectService).maakProjectAan("bestaand");

    mockMvc.perform(post("/api/v1/projects")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"naam\": \"bestaand\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messages[0].code").value("invalid.argument"));
}

@Test
void verwijdertProject() throws Exception {
    when(projectService.verwijderProject("oud-project")).thenReturn(true);

    mockMvc.perform(delete("/api/v1/projects/oud-project"))
        .andExpect(status().isNoContent());
}

@Test
void verwijderProject_geeft404AlsProjectNietBestaat() throws Exception {
    when(projectService.verwijderProject("bestaat-niet")).thenReturn(false);

    mockMvc.perform(delete("/api/v1/projects/bestaat-niet"))
        .andExpect(status().isNotFound());
}
```

**Step 2: Run tests to verify they fail**

Run: `cd app && mvn test -pl . -Dtest="ProjectControllerTest#geeftProjectenMetAantalDocumentenTerug+maaktProjectAan+maakProjectAan_geeft400AlsProjectAlBestaat+verwijdertProject+verwijderProject_geeft404AlsProjectNietBestaat" -Denforcer.skip=true`
Expected: FAIL

**Step 3: Update controller**

In `ProjectController.java`:

**3a.** Voeg import toe:

```java
import org.springframework.web.bind.annotation.RequestBody;
```

**3b.** Wijzig `geefProjecten()`:

```java
@GetMapping
public ResponseEntity<ProjectenResponse> geefProjecten() {
    var overzichten = projectService.geefProjectenMetAantalDocumenten();
    var dtos = overzichten.stream()
        .map(o -> new ProjectDto(o.naam(), o.aantalDocumenten()))
        .toList();
    return ResponseEntity.ok(new ProjectenResponse(dtos));
}
```

**3c.** Voeg POST endpoint toe:

```java
/**
 * Maakt een nieuw project aan.
 *
 * @param request Het verzoek met de projectnaam
 * @return 201 Created
 */
@PostMapping
public ResponseEntity<Void> maakProjectAan(@RequestBody ProjectAanmaakRequest request) {
    projectService.maakProjectAan(request.naam());
    return ResponseEntity.status(201).build();
}
```

**3d.** Voeg DELETE endpoint toe:

```java
/**
 * Verwijdert een project en alle bijhorende data.
 *
 * @param naam Projectnaam
 * @return 204 No Content bij succes, 404 als project niet gevonden
 */
@DeleteMapping("/{naam}")
public ResponseEntity<Void> verwijderProject(@PathVariable String naam) {
    boolean verwijderd = projectService.verwijderProject(naam);
    return verwijderd ? ResponseEntity.noContent().build()
        : ResponseEntity.notFound().build();
}
```

**3e.** Wijzig de `ProjectenResponse` record en voeg DTOs toe:

```java
/** DTO voor een enkel project in de response. */
record ProjectDto(String naam, int aantalDocumenten) {}

/** Response DTO voor projectenlijst. */
record ProjectenResponse(List<ProjectDto> projecten) {}

/** Request DTO voor project aanmaken. */
record ProjectAanmaakRequest(String naam) {}
```

**Step 4: Update de bestaande test `geeftProjectenTerug`**

De bestaande test `geeftProjectenTerug` en `geeftLegeProjectenLijstTerug` gebruiken `geefProjecten()` die nu `geefProjectenMetAantalDocumenten()` aanroept. Verwijder die twee bestaande tests (ze worden vervangen door `geeftProjectenMetAantalDocumentenTerug`).

**Step 5: Run tests**

Run: `cd app && mvn test -pl . -Dtest="ProjectControllerTest" -Denforcer.skip=true`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectControllerTest.java
git commit -m "feat: POST/DELETE endpoints + aangepaste GET response met aantalDocumenten"
```

---

## Task 6: Hash-routing in `bezwaarschriften-landingspagina`

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-landingspagina.js`

**Step 1: Refactor naar hash-routing**

Vervang de volledige inhoud van `bezwaarschriften-landingspagina.js`:

```javascript
import {
  BaseHTMLElement,
  defineWebComponent,
  registerWebComponents,
} from '@domg-wc/common';
import {VlContentHeaderComponent} from '@domg-wc/components/block/content-header/vl-content-header.component.js';
import {VlTemplate} from '@domg-wc/components/block/template/vl-template.component.js';
import {VlTypography} from '@domg-wc/components/block/typography/vl-typography.component.js';
import {vlGlobalStyles, vlGridStyles} from '@domg-wc/styles';
import './bezwaarschriften-projecten-overzicht.js';
import './bezwaarschriften-project-selectie.js';

registerWebComponents([VlTemplate, VlContentHeaderComponent, VlTypography]);

export class BezwaarschriftenLandingspagina extends BaseHTMLElement {
  constructor() {
    super(`
        <style>
          ${vlGlobalStyles}
          ${vlGridStyles}
        </style>
        <vl-template>
          <div slot="main">
            <vl-content-header>
              <img
                is="vl-image"
                slot="image"
                sizes="100vw"
                src="/img/banner.jpg"
              />
              <a slot="context-link" href="/">BEZWAARSCHRIFTEN</a>
            </vl-content-header>
            <section class="vl-region">
              <div class="vl-layout">
                <div class="vl-grid">
                  <div class="vl-column vl-column--2 vl-column--1--s"></div>
                  <div id="pagina-inhoud" class="vl-column vl-column--8 vl-column--10--s vl-column--12--xs">
                  </div>
                  <div class="vl-column vl-column--2 vl-column--1--s"></div>
                </div>
              </div>
            </section>
          </div>
        </vl-template>
    `);
    this.__addVlElementStyleSheetsToDocument();
    this._onHashChange = this._onHashChange.bind(this);
  }

  __addVlElementStyleSheetsToDocument() {
    document.adoptedStyleSheets = [
      ...vlGlobalStyles.map((style) => style.styleSheet),
      ...vlGridStyles.map((style) => style.styleSheet),
    ];
  }

  connectedCallback() {
    super.connectedCallback();
    window.addEventListener('hashchange', this._onHashChange);
    this._routeer();
  }

  disconnectedCallback() {
    window.removeEventListener('hashchange', this._onHashChange);
  }

  _onHashChange() {
    this._routeer();
  }

  _routeer() {
    const hash = window.location.hash.replace(/^#\/?/, '');
    const container = this.shadowRoot.querySelector('#pagina-inhoud');
    if (!container) return;

    container.innerHTML = '';

    if (hash.startsWith('project/')) {
      const projectNaam = decodeURIComponent(hash.substring('project/'.length));
      this._toonProjectDetail(container, projectNaam);
    } else {
      this._toonProjectenOverzicht(container);
    }
  }

  _toonProjectenOverzicht(container) {
    const typography = document.createElement('vl-typography');
    const h1 = document.createElement('h1');
    h1.className = 'vl-title vl-title--h1';
    h1.style.marginBottom = '3rem';
    h1.textContent = 'Bezwaarschriften';
    typography.appendChild(h1);
    const p = document.createElement('p');
    p.className = 'vl-introduction';
    p.textContent = 'Welkom op de toepassing Bezwaarschriften. Hier kan u bezwaarschriften automatisch laten verwerken.';
    typography.appendChild(p);
    container.appendChild(typography);

    const overzicht = document.createElement('bezwaarschriften-projecten-overzicht');
    container.appendChild(overzicht);
  }

  _toonProjectDetail(container, projectNaam) {
    const typography = document.createElement('vl-typography');
    const h1 = document.createElement('h1');
    h1.className = 'vl-title vl-title--h1';
    h1.style.marginBottom = '3rem';
    h1.textContent = projectNaam;
    typography.appendChild(h1);
    container.appendChild(typography);

    const selectie = document.createElement('bezwaarschriften-project-selectie');
    selectie.projectNaam = projectNaam;
    container.appendChild(selectie);
  }
}

defineWebComponent(
    BezwaarschriftenLandingspagina,
    'bezwaarschriften-landingspagina',
);
```

**Step 2: Verify handmatig**

Open de app in browser. Navigeer naar `#/` — moet leeg zijn (overzicht component bestaat nog niet). Navigeer naar `#/project/test` — moet project-selectie tonen (met fout omdat het component nog niet refactored is).

**Step 3: Commit**

```bash
git add webapp/src/js/bezwaarschriften-landingspagina.js
git commit -m "feat: hash-routing in landingspagina"
```

---

## Task 7: Refactor `bezwaarschriften-project-selectie` — verwijder dropdown

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js`

**Step 1: Refactor component**

Wijzigingen:

**1a.** Verwijder `VlSelectComponent` import (de bestand-level import, niet die van bezwaren-tabel). De `VlSelectComponent` zit alleen in de registerWebComponents array. Verwijder uit de import en uit `registerWebComponents`.

**1b.** Verwijder `__projecten` uit `static get properties()`.

**1c.** Verwijder in de constructor template de `<div id="selectie-wrapper">...</div>` sectie.

**1d.** In de constructor, verwijder `this.__projecten = [];` en verwijder `this.__geselecteerdProject = null;`.

**1e.** Voeg property toe:

```javascript
set projectNaam(naam) {
    this.__geselecteerdProject = naam;
    if (naam && this.isConnected) {
        this._laadBezwaren(naam);
    }
}

get projectNaam() {
    return this.__geselecteerdProject;
}
```

**1f.** In `connectedCallback()`, vervang `this._laadProjecten();` door:

```javascript
if (this.__geselecteerdProject) {
    this._laadBezwaren(this.__geselecteerdProject);
}
```

Verwijder ook `this._koppelEventListeners()` aanroep — die wordt nu inline in connectedCallback aangeroepen. Maar verplaats alle event listener koppelingen behalve de `vl-select` event listener:

```javascript
connectedCallback() {
    super.connectedCallback();
    if (this.__geselecteerdProject) {
        this._laadBezwaren(this.__geselecteerdProject);
    }
    this._koppelEventListeners();
    this._verbindWebSocket();
}
```

**1g.** In `_koppelEventListeners()`, verwijder de `selectEl` variabele en het `selectEl.addEventListener('vl-change', ...)` blok.

**1h.** Verwijder de `_laadProjecten()` methode volledig.

**1i.** Maak `#tabs-sectie` standaard **niet** hidden:

In de constructor template, verander `<div id="tabs-sectie" hidden>` naar `<div id="tabs-sectie">`.

**1j.** Verwijder de `_verbergTabsSectie()` methode.

**Step 2: Build en test handmatig**

Run: `cd webapp && npm run build`
Navigeer naar `#/project/project-zultewaregem` — tabs en bezwaren moeten laden.

**Step 3: Commit**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "refactor: verwijder project-dropdown, accepteer projectNaam als property"
```

---

## Task 8: Nieuw component `bezwaarschriften-projecten-overzicht`

**Files:**
- Create: `webapp/src/js/bezwaarschriften-projecten-overzicht.js`
- Modify: `webapp/src/js/index.js`

**Step 1: Maak het component**

Maak `webapp/src/js/bezwaarschriften-projecten-overzicht.js`:

```javascript
import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlRichDataTable} from '@domg-wc/components/block/rich-data-table/vl-rich-data-table.component.js';
import {VlRichDataField} from '@domg-wc/components/block/rich-data-table/vl-rich-data-field.component.js';
import {VlButtonComponent} from '@domg-wc/components/atom/button/vl-button.component.js';
import {VlModalComponent} from '@domg-wc/components/block/modal/vl-modal.component.js';
import {VlInputFieldComponent} from '@domg-wc/components/form/input-field/vl-input-field.component.js';
import {VlFormLabelComponent} from '@domg-wc/components/form/form-label/vl-form-label.component.js';
import {VlToasterComponent} from '@domg-wc/components/block/toaster/vl-toaster.component.js';
import {vlGlobalStyles} from '@domg-wc/styles';

registerWebComponents([
  VlRichDataTable, VlRichDataField, VlButtonComponent,
  VlModalComponent, VlInputFieldComponent, VlFormLabelComponent,
  VlToasterComponent,
]);

export class BezwaarschriftenProjectenOverzicht extends BaseHTMLElement {
  constructor() {
    super(`
      <style>
        ${vlGlobalStyles}
        .actieknoppen { margin-bottom: 1rem; display: flex; gap: 1rem; }
      </style>
      <div class="actieknoppen">
        <vl-button id="toevoegen-knop">Project toevoegen</vl-button>
        <vl-button id="verwijder-knop" error="" hidden>Project verwijderen</vl-button>
      </div>
      <vl-rich-data-table id="tabel">
        <vl-rich-data-field name="naam" label="Naam" sortable></vl-rich-data-field>
        <vl-rich-data-field name="aantalDocumenten" label="Aantal documenten" sortable></vl-rich-data-field>
      </vl-rich-data-table>
      <vl-modal id="toevoegen-modal" title="Project toevoegen" closable>
        <div slot="content">
          <vl-form-label for="project-naam-invoer" label="Projectnaam"></vl-form-label>
          <vl-input-field id="project-naam-invoer" type="text" name="naam" placeholder="Projectnaam..." block></vl-input-field>
          <p id="toevoegen-fout" hidden style="color: #db3434; margin-top: 0.5rem;"></p>
        </div>
        <div slot="button">
          <vl-button id="toevoegen-bevestig-knop">Toevoegen</vl-button>
        </div>
      </vl-modal>
      <vl-modal id="verwijder-modal" title="Project verwijderen" closable>
        <div slot="content">
          <p id="verwijder-bevestiging-tekst"></p>
        </div>
        <div slot="button">
          <vl-button id="verwijder-bevestig-knop" error="">Verwijderen</vl-button>
        </div>
      </vl-modal>
      <vl-toaster id="toaster"></vl-toaster>
    `);
    this.__projecten = [];
    this.__geselecteerdProject = null;
    this.__tabelKlaar = false;
  }

  connectedCallback() {
    super.connectedCallback();
    this._koppelEventListeners();

    customElements.whenDefined('vl-rich-data-table').then(() => {
      this.__tabelKlaar = true;
      this._configureerRenderers();
      this._laadProjecten();
    });
  }

  _configureerRenderers() {
    const velden = this.shadowRoot.querySelectorAll('vl-rich-data-field');
    velden.forEach((veld) => {
      if (veld.getAttribute('name') === 'naam') {
        veld.renderer = (td, rij) => {
          const a = document.createElement('a');
          a.href = `#/project/${encodeURIComponent(rij.naam)}`;
          a.textContent = rij.naam;
          td.appendChild(a);
        };
      }
    });
  }

  _koppelEventListeners() {
    const tabel = this.shadowRoot.querySelector('#tabel');
    if (tabel) {
      tabel.addEventListener('change', (e) => {
        const detail = e.detail || {};
        if (detail.sorting) {
          this._sorteerEnToon(detail.sorting);
        }
      });
    }

    const toevoegenKnop = this.shadowRoot.querySelector('#toevoegen-knop');
    if (toevoegenKnop) {
      toevoegenKnop.addEventListener('vl-click', () => {
        const invoer = this.shadowRoot.querySelector('#project-naam-invoer');
        if (invoer) invoer.value = '';
        const fout = this.shadowRoot.querySelector('#toevoegen-fout');
        if (fout) fout.hidden = true;
        const modal = this.shadowRoot.querySelector('#toevoegen-modal');
        if (modal) modal.open();
      });
    }

    const toevoegenBevestig = this.shadowRoot.querySelector('#toevoegen-bevestig-knop');
    if (toevoegenBevestig) {
      toevoegenBevestig.addEventListener('vl-click', () => this._voegProjectToe());
    }

    const verwijderKnop = this.shadowRoot.querySelector('#verwijder-knop');
    if (verwijderKnop) {
      verwijderKnop.addEventListener('vl-click', () => {
        if (!this.__geselecteerdProject) return;
        const project = this.__projecten.find((p) => p.naam === this.__geselecteerdProject);
        const aantal = project ? project.aantalDocumenten : 0;
        const tekst = this.shadowRoot.querySelector('#verwijder-bevestiging-tekst');
        if (tekst) {
          tekst.textContent = `Weet je zeker dat je project '${this.__geselecteerdProject}' wilt verwijderen? ${aantal} document(en) en bijhorende extractie-resultaten worden permanent verwijderd.`;
        }
        const modal = this.shadowRoot.querySelector('#verwijder-modal');
        if (modal) modal.open();
      });
    }

    const verwijderBevestig = this.shadowRoot.querySelector('#verwijder-bevestig-knop');
    if (verwijderBevestig) {
      verwijderBevestig.addEventListener('vl-click', () => this._verwijderProject());
    }
  }

  _laadProjecten() {
    fetch('/api/v1/projects')
        .then((response) => {
          if (!response.ok) throw new Error('Ophalen projecten mislukt');
          return response.json();
        })
        .then((data) => {
          this.__projecten = data.projecten;
          this._toonProjecten();
        })
        .catch(() => {
          this._toonToast('error', 'Projecten konden niet worden geladen.');
        });
  }

  _toonProjecten() {
    if (!this.__tabelKlaar) return;
    const tabel = this.shadowRoot.querySelector('#tabel');
    if (!tabel) return;

    tabel.data = {data: this.__projecten};

    requestAnimationFrame(() => this._configureerSelectie());
  }

  _configureerSelectie() {
    const tabel = this.shadowRoot.querySelector('#tabel');
    if (!tabel) return;
    const vlTable = tabel.shadowRoot && tabel.shadowRoot.querySelector('vl-table');
    if (!vlTable) return;
    const innerTable = vlTable.querySelector('table');
    if (!innerTable) return;

    const rijen = innerTable.querySelectorAll('tbody tr');
    rijen.forEach((rij) => {
      rij.style.cursor = 'pointer';
      rij.addEventListener('click', (e) => {
        if (e.target.tagName === 'A') return;
        const naamCel = rij.querySelector('td a');
        if (naamCel) {
          const naam = naamCel.textContent;
          this._selecteerRij(naam, rij, innerTable);
        }
      });
    });
  }

  _selecteerRij(naam, rij, tabel) {
    tabel.querySelectorAll('tbody tr').forEach((r) => {
      r.style.backgroundColor = '';
    });

    if (this.__geselecteerdProject === naam) {
      this.__geselecteerdProject = null;
    } else {
      this.__geselecteerdProject = naam;
      rij.style.backgroundColor = '#e8ebee';
    }

    const verwijderKnop = this.shadowRoot.querySelector('#verwijder-knop');
    if (verwijderKnop) {
      verwijderKnop.hidden = !this.__geselecteerdProject;
    }
  }

  _voegProjectToe() {
    const invoer = this.shadowRoot.querySelector('#project-naam-invoer');
    const naam = invoer ? invoer.value.trim() : '';
    if (!naam) {
      const fout = this.shadowRoot.querySelector('#toevoegen-fout');
      if (fout) {
        fout.textContent = 'Projectnaam is verplicht.';
        fout.hidden = false;
      }
      return;
    }

    fetch('/api/v1/projects', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({naam}),
    })
        .then((response) => {
          if (!response.ok) {
            return response.json().then((data) => {
              throw new Error(data.messages?.[0]?.parameters?.message || 'Aanmaken mislukt');
            });
          }
          const modal = this.shadowRoot.querySelector('#toevoegen-modal');
          if (modal) modal.close();
          this._toonToast('success', `Project '${naam}' aangemaakt.`);
          this._laadProjecten();
        })
        .catch((err) => {
          const fout = this.shadowRoot.querySelector('#toevoegen-fout');
          if (fout) {
            fout.textContent = err.message;
            fout.hidden = false;
          }
        });
  }

  _verwijderProject() {
    if (!this.__geselecteerdProject) return;
    const naam = this.__geselecteerdProject;

    fetch(`/api/v1/projects/${encodeURIComponent(naam)}`, {
      method: 'DELETE',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Verwijderen mislukt');
          const modal = this.shadowRoot.querySelector('#verwijder-modal');
          if (modal) modal.close();
          this.__geselecteerdProject = null;
          const verwijderKnop = this.shadowRoot.querySelector('#verwijder-knop');
          if (verwijderKnop) verwijderKnop.hidden = true;
          this._toonToast('success', `Project '${naam}' verwijderd.`);
          this._laadProjecten();
        })
        .catch(() => {
          this._toonToast('error', 'Verwijderen mislukt.');
        });
  }

  _sorteerEnToon(sorting) {
    if (!sorting || sorting.length === 0) {
      this._toonProjecten();
      return;
    }
    const gesorteerd = [...this.__projecten].sort((a, b) => {
      for (const sort of sorting) {
        let cmp = 0;
        if (sort.name === 'aantalDocumenten') {
          cmp = a.aantalDocumenten - b.aantalDocumenten;
        } else {
          cmp = String(a[sort.name] || '').localeCompare(String(b[sort.name] || ''), 'nl');
        }
        if (cmp !== 0) return sort.direction === 'asc' ? cmp : -cmp;
      }
      return 0;
    });

    const tabel = this.shadowRoot.querySelector('#tabel');
    if (tabel) {
      tabel.data = {data: gesorteerd};
      requestAnimationFrame(() => this._configureerSelectie());
    }
  }

  _toonToast(type, bericht) {
    const toaster = this.shadowRoot.querySelector('#toaster');
    if (!toaster) return;
    const alert = document.createElement('vl-alert');
    alert.setAttribute('type', type);
    alert.setAttribute('icon', type === 'success' ? 'check' : 'warning');
    alert.setAttribute('message', bericht);
    alert.setAttribute('closable', '');
    toaster.show(alert);
    if (type === 'success') {
      setTimeout(() => alert.remove(), 5000);
    }
  }
}

defineWebComponent(BezwaarschriftenProjectenOverzicht, 'bezwaarschriften-projecten-overzicht');
```

**Step 2: Update index.js**

In `webapp/src/js/index.js`, voeg import toe:

```javascript
import './bezwaarschriften-projecten-overzicht.js';
```

**Step 3: Build**

Run: `cd webapp && npm run build`
Expected: Build succeeds

**Step 4: Commit**

```bash
git add webapp/src/js/bezwaarschriften-projecten-overzicht.js webapp/src/js/index.js
git commit -m "feat: projecten-overzicht component met rich data table"
```

---

## Task 9: Integratie-test en afwerking

**Step 1: Build frontend + backend**

Run:
```bash
cd webapp && npm run build
cd .. && mvn process-resources -pl webapp -Denforcer.skip=true
```

**Step 2: Start de applicatie en test handmatig**

Run: `cd app && mvn spring-boot:run -Denforcer.skip=true`

Controleer:
1. `http://localhost:8080/` → Toont projectenoverzicht met tabel
2. Klik "Project toevoegen" → Modal opent, voer naam in, bevestig → Project verschijnt in tabel
3. Klik op projectnaam → Navigeert naar `#/project/{naam}`, toont detailpagina met tabs
4. Browser back → Terug naar overzicht
5. Selecteer rij, klik "Project verwijderen" → Bevestigingsmodal → Project verdwijnt

**Step 3: Run alle backend tests**

Run: `cd app && mvn test -Denforcer.skip=true`
Expected: ALL PASS

**Step 4: Commit eventuele fixes**

Als er fixes nodig zijn, commit ze apart.

**Step 5: Final commit message als alles werkt**

Geen extra commit nodig als alles al gecommit is.
