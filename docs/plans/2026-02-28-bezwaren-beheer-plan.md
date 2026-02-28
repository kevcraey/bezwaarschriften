# Bezwaren Beheer (Upload + Verwijderen) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Gebruikers kunnen bezwaarbestanden uploaden en verwijderen binnen een project, met client-side validatie, server-side beveiliging, bevestigingsmodaliteit, en live tabel-updates.

**Architecture:** Fullstack feature. Backend: nieuwe write/delete methodes op ProjectPoort + adapter, nieuwe endpoints op ProjectController. Frontend: vl-upload dropzone (verborgen achter knop) + vl-modal bevestiging + verwijderknop in bezwaarschriften-project-selectie.js.

**Tech Stack:** Spring Boot 3.4, MultipartFile, @domg-wc/components (vl-upload, vl-modal, vl-button), Shadow DOM web components

---

### Context: Key Files

**Backend:**
- `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectPoort.java` -- Port interface (momenteel alleen lees-methodes)
- `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapter.java` -- Filesystem adapter
- `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java` -- Business logica
- `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java` -- REST controller
- `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakRepository.java` -- JPA repository
- `app/src/main/resources/application.yml` -- Spring config

**Frontend:**
- `webapp/src/js/bezwaarschriften-project-selectie.js` -- Parent component met tabs, extractie-knop, WebSocket

### Context: vl-upload API

```html
<vl-upload
  accepted-files=".txt"
  max-files="100"
  max-size="50"
  main-title="Selecteer bestanden"
  sub-title="Sleep bestanden hierheen of klik om te bladeren"
  disallow-duplicates
></vl-upload>
```

- `auto-process` attribute: automatisch uploaden (default: false)
- `url` attribute: upload endpoint
- `getFiles()`: geeft DropzoneFile[] terug
- `removeAllFiles()`: wis alle bestanden
- Events: `vl-addedfile`, `vl-removedfile`, `vl-error`, `vl-success`, `vl-queuecomplete`
- Import: `import {VlUploadComponent} from '@domg-wc/components/form/upload/vl-upload.component.js';`

### Context: vl-modal API

```html
<vl-modal id="verwijder-modal" title="Bestanden verwijderen" closable>
  <div slot="content"><p>Bevestigingstekst</p></div>
  <div slot="button"><vl-button>Verwijderen</vl-button></div>
</vl-modal>
```

- `open()` / `close()` methodes
- Slots: `content`, `button`
- `closable` attribute: toont X-knop
- Cancel-knop wordt automatisch toegevoegd
- Import: `import {VlModalComponent} from '@domg-wc/components/block/modal/vl-modal.component.js';`

### Context: Bestaande ProjectController endpoints

```
GET  /api/v1/projects                     -- projectenlijst
GET  /api/v1/projects/{naam}/bezwaren     -- bezwaarbestanden met status
POST /api/v1/projects/{naam}/extracties   -- extracties indienen
GET  /api/v1/projects/{naam}/extracties   -- extractie-taken opvragen
```

---

### Task 1: Extend ProjectPoort + BestandssysteemProjectAdapter met write/delete

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectPoort.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapter.java`

**Step 1: Add methods to ProjectPoort interface**

Add two new methods to `ProjectPoort.java`:

```java
/**
 * Slaat een bestand op in de bezwaren-map van een project.
 *
 * @param projectNaam Naam van het project
 * @param bestandsnaam Naam van het bestand
 * @param inhoud Bestandsinhoud als byte-array
 * @throws ProjectNietGevondenException Als het project niet bestaat
 */
void slaBestandOp(String projectNaam, String bestandsnaam, byte[] inhoud);

/**
 * Verwijdert een bestand uit de bezwaren-map van een project.
 *
 * @param projectNaam Naam van het project
 * @param bestandsnaam Naam van het bestand
 * @return true als het bestand is verwijderd, false als het niet bestond
 * @throws ProjectNietGevondenException Als het project niet bestaat
 */
boolean verwijderBestand(String projectNaam, String bestandsnaam);
```

**Step 2: Implement in BestandssysteemProjectAdapter**

Add implementations after the existing `geefBestandsnamen` method. Important: validate against path traversal (normalize + startsWith check, same as in `geefBestandsnamen`).

```java
@Override
public void slaBestandOp(String projectNaam, String bestandsnaam, byte[] inhoud) {
  var bezwarenPad = resolveEnValideerBezwarenPad(projectNaam);
  var doelPad = bezwarenPad.resolve(bestandsnaam).normalize();
  if (!doelPad.startsWith(bezwarenPad)) {
    throw new IllegalArgumentException("Ongeldige bestandsnaam: " + bestandsnaam);
  }
  try {
    Files.createDirectories(bezwarenPad);
    Files.write(doelPad, inhoud);
    LOGGER.info("Bestand '{}' opgeslagen voor project '{}'", bestandsnaam, projectNaam);
  } catch (IOException e) {
    throw new RuntimeException("Kon bestand niet opslaan: " + bestandsnaam, e);
  }
}

@Override
public boolean verwijderBestand(String projectNaam, String bestandsnaam) {
  var bezwarenPad = resolveEnValideerBezwarenPad(projectNaam);
  var doelPad = bezwarenPad.resolve(bestandsnaam).normalize();
  if (!doelPad.startsWith(bezwarenPad)) {
    throw new IllegalArgumentException("Ongeldige bestandsnaam: " + bestandsnaam);
  }
  try {
    boolean verwijderd = Files.deleteIfExists(doelPad);
    if (verwijderd) {
      LOGGER.info("Bestand '{}' verwijderd voor project '{}'", bestandsnaam, projectNaam);
    }
    return verwijderd;
  } catch (IOException e) {
    throw new RuntimeException("Kon bestand niet verwijderen: " + bestandsnaam, e);
  }
}

private Path resolveEnValideerBezwarenPad(String projectNaam) {
  var projectPad = inputFolder.resolve(projectNaam).normalize();
  if (!projectPad.startsWith(inputFolder.normalize())) {
    throw new ProjectNietGevondenException(projectNaam);
  }
  if (!Files.isDirectory(projectPad)) {
    throw new ProjectNietGevondenException(projectNaam);
  }
  return projectPad.resolve("bezwaren");
}
```

Refactor `geefBestandsnamen` to use `resolveEnValideerBezwarenPad` (extract the common validation logic).

**Step 3: Compile**

```bash
cd app && mvn compile -q
```

Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectPoort.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapter.java
git commit -m "feat: ProjectPoort uitgebreid met slaBestandOp en verwijderBestand"
```

---

### Task 2: Extend ExtractieTaakRepository + ProjectService met delete/upload logica

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakRepository.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java`

**Step 1: Add delete query to ExtractieTaakRepository**

```java
/**
 * Verwijdert alle extractie-taken voor een bepaald bestand binnen een project.
 *
 * @param projectNaam de naam van het project
 * @param bestandsnaam de naam van het bestand
 */
void deleteByProjectNaamAndBestandsnaam(String projectNaam, String bestandsnaam);
```

Spring Data JPA genereert de delete-query automatisch op basis van de methodenaam.

**Step 2: Add upload and delete methods to ProjectService**

Add `uploadBezwaren` method:

```java
/**
 * Uploadt bezwaarbestanden naar een project.
 *
 * @param projectNaam Naam van het project
 * @param bestanden Map van bestandsnaam naar byte-inhoud
 * @return Upload-resultaat met geslaagde en gefaalde bestanden
 */
@Transactional
public UploadResultaat uploadBezwaren(String projectNaam,
    Map<String, byte[]> bestanden) {
  var geupload = new ArrayList<String>();
  var fouten = new ArrayList<UploadFout>();

  for (var entry : bestanden.entrySet()) {
    var bestandsnaam = entry.getKey();
    var inhoud = entry.getValue();

    if (!isTxtBestand(bestandsnaam)) {
      fouten.add(new UploadFout(bestandsnaam, "Niet-ondersteund formaat"));
      continue;
    }

    var bestaandeNamen = projectPoort.geefBestandsnamen(projectNaam);
    if (bestaandeNamen.contains(bestandsnaam)) {
      fouten.add(new UploadFout(bestandsnaam, "Bestand bestaat al"));
      continue;
    }

    projectPoort.slaBestandOp(projectNaam, bestandsnaam, inhoud);
    geupload.add(bestandsnaam);
  }

  return new UploadResultaat(geupload, fouten);
}
```

Add `verwijderBezwaar` method:

```java
/**
 * Verwijdert een bezwaarbestand en bijhorende extractie-taken.
 *
 * @param projectNaam Naam van het project
 * @param bestandsnaam Naam van het te verwijderen bestand
 * @return true als het bestand is verwijderd
 */
@Transactional
public boolean verwijderBezwaar(String projectNaam, String bestandsnaam) {
  extractieTaakRepository.deleteByProjectNaamAndBestandsnaam(projectNaam, bestandsnaam);
  return projectPoort.verwijderBestand(projectNaam, bestandsnaam);
}
```

Add helper records in ProjectService (or as separate files):

```java
record UploadResultaat(List<String> geupload, List<UploadFout> fouten) {}
record UploadFout(String bestandsnaam, String reden) {}
```

**Step 3: Compile**

```bash
cd app && mvn compile -q
```

Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakRepository.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java
git commit -m "feat: upload en verwijder logica in ProjectService + delete query in repository"
```

---

### Task 3: Add upload/delete endpoints to ProjectController + multipart config

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java`
- Modify: `app/src/main/resources/application.yml`

**Step 1: Configure multipart in application.yml**

Add under `spring:` section:

```yaml
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 500MB
```

**Step 2: Add upload endpoint**

Add imports for `MultipartFile`, `RequestParam`, `PostMapping`, `DeleteMapping`, `Map`, `LinkedHashMap`.

```java
/**
 * Uploadt bezwaarbestanden naar een project.
 *
 * @param naam Projectnaam
 * @param bestanden Ge-uploade bestanden
 * @return Upload-resultaat
 */
@PostMapping("/{naam}/bezwaren/upload")
public ResponseEntity<UploadResponse> uploadBezwaren(
    @PathVariable String naam,
    @RequestParam("bestanden") MultipartFile[] bestanden) {
  if (bestanden.length > 100) {
    return ResponseEntity.badRequest().body(
        new UploadResponse(List.of(),
            List.of(new UploadFoutDto("", "Maximum 100 bestanden per upload"))));
  }

  var bestandenMap = new LinkedHashMap<String, byte[]>();
  var fouten = new ArrayList<UploadFoutDto>();

  for (var bestand : bestanden) {
    try {
      bestandenMap.put(bestand.getOriginalFilename(), bestand.getBytes());
    } catch (IOException e) {
      fouten.add(new UploadFoutDto(bestand.getOriginalFilename(),
          "Kon bestand niet lezen"));
    }
  }

  var resultaat = projectService.uploadBezwaren(naam, bestandenMap);
  resultaat.fouten().forEach(f ->
      fouten.add(new UploadFoutDto(f.bestandsnaam(), f.reden())));

  return ResponseEntity.ok(new UploadResponse(resultaat.geupload(), fouten));
}
```

**Step 3: Add delete endpoint**

```java
/**
 * Verwijdert een bezwaarbestand en bijhorende extractie-taken.
 *
 * @param naam Projectnaam
 * @param bestandsnaam Bestandsnaam
 * @return 204 No Content bij succes, 404 als bestand niet gevonden
 */
@DeleteMapping("/{naam}/bezwaren/{bestandsnaam}")
public ResponseEntity<Void> verwijderBezwaar(
    @PathVariable String naam,
    @PathVariable String bestandsnaam) {
  boolean verwijderd = projectService.verwijderBezwaar(naam, bestandsnaam);
  return verwijderd ? ResponseEntity.noContent().build()
      : ResponseEntity.notFound().build();
}
```

**Step 4: Add response DTOs** (inner records in ProjectController)

```java
record UploadResponse(List<String> geupload, List<UploadFoutDto> fouten) {}
record UploadFoutDto(String bestandsnaam, String reden) {}
```

**Step 5: Compile**

```bash
cd app && mvn compile -q
```

Expected: BUILD SUCCESS

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java \
       app/src/main/resources/application.yml
git commit -m "feat: upload en delete REST endpoints + multipart configuratie"
```

---

### Task 4: Frontend -- upload zone, verwijderknop en bevestigingsmodal

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js`

**Step 1: Add imports**

Add na de bestaande imports:

```javascript
import {VlUploadComponent} from '@domg-wc/components/form/upload/vl-upload.component.js';
import {VlModalComponent} from '@domg-wc/components/block/modal/vl-modal.component.js';
```

Update `registerWebComponents`:

```javascript
registerWebComponents([VlSelectComponent, VlButtonComponent, VlTabsComponent, VlTabsPaneComponent, VlUploadComponent, VlModalComponent]);
```

**Step 2: Extend template in constructor**

In de Documenten `vl-tabs-pane`, voeg toe na de bestaande `extraheer-knop`:

```html
<vl-button id="verwijder-knop" disabled>Verwijder geselecteerde</vl-button>
<vl-button id="toevoegen-knop">Bestanden toevoegen</vl-button>
<div id="upload-zone" hidden>
  <vl-upload id="bestand-upload"
    accepted-files=".txt"
    max-files="100"
    max-size="50"
    main-title="Bezwaarbestanden toevoegen"
    sub-title="Sleep .txt bestanden hierheen of klik om te bladeren"
    disallow-duplicates>
  </vl-upload>
  <vl-button id="upload-verzend-knop">Uploaden</vl-button>
</div>
```

Voeg ook een modal toe buiten de tabs (direct na `#tabs-sectie`):

```html
<vl-modal id="verwijder-modal" title="Bestanden verwijderen" closable>
  <div slot="content">
    <p id="verwijder-bevestiging-tekst"></p>
  </div>
  <div slot="button">
    <vl-button id="verwijder-bevestig-knop">Verwijderen</vl-button>
  </div>
</vl-modal>
```

**Step 3: Add event listeners in `_koppelEventListeners()`**

Add handlers for:

1. **Toevoegen-knop**: toggle upload zone visibility

```javascript
const toevoegenKnop = this.shadowRoot.querySelector('#toevoegen-knop');
if (toevoegenKnop) {
  toevoegenKnop.addEventListener('vl-click', () => {
    const uploadZone = this.shadowRoot.querySelector('#upload-zone');
    if (uploadZone) uploadZone.hidden = !uploadZone.hidden;
  });
}
```

2. **Upload verzend-knop**: collect files from vl-upload, POST to backend

```javascript
const uploadVerzendKnop = this.shadowRoot.querySelector('#upload-verzend-knop');
if (uploadVerzendKnop) {
  uploadVerzendKnop.addEventListener('vl-click', () => {
    this._verzendUpload();
  });
}
```

3. **Verwijder-knop**: open modal with confirmation text

```javascript
const verwijderKnop = this.shadowRoot.querySelector('#verwijder-knop');
if (verwijderKnop) {
  verwijderKnop.addEventListener('vl-click', () => {
    const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
    if (!tabel) return;
    const geselecteerd = tabel.geefGeselecteerdeBestandsnamen();
    if (geselecteerd.length === 0) return;
    this._teVerwijderenBestanden = geselecteerd;
    const tekst = this.shadowRoot.querySelector('#verwijder-bevestiging-tekst');
    if (tekst) {
      tekst.textContent = `Weet je zeker dat je ${geselecteerd.length} bestand(en) wilt verwijderen? Bestanden en bijhorende extractie-resultaten worden permanent verwijderd.`;
    }
    const modal = this.shadowRoot.querySelector('#verwijder-modal');
    if (modal) modal.open();
  });
}
```

4. **Verwijder bevestig-knop**: execute deletion

```javascript
const verwijderBevestigKnop = this.shadowRoot.querySelector('#verwijder-bevestig-knop');
if (verwijderBevestigKnop) {
  verwijderBevestigKnop.addEventListener('vl-click', () => {
    this._verwijderBestanden(this._teVerwijderenBestanden);
  });
}
```

5. **Selectie-gewijzigd event**: update both extraheer-knop AND verwijder-knop disabled state

```javascript
this.shadowRoot.addEventListener('selectie-gewijzigd', (e) => {
  const heeftSelectie = e.detail.geselecteerd.length > 0;
  if (extraheerKnop) extraheerKnop.disabled = this.__bezig || !heeftSelectie;
  if (verwijderKnop) verwijderKnop.disabled = this.__bezig || !heeftSelectie;
});
```

**Step 4: Add `_verzendUpload()` method**

```javascript
_verzendUpload() {
  const uploadEl = this.shadowRoot.querySelector('#bestand-upload');
  if (!uploadEl || !this.__geselecteerdProject) return;

  const bestanden = uploadEl.getFiles();
  if (!bestanden || bestanden.length === 0) return;

  const formData = new FormData();
  bestanden.forEach((f) => formData.append('bestanden', f));

  this._zetBezig(true);
  this._verbergFout();

  fetch(`/api/v1/projects/${encodeURIComponent(this.__geselecteerdProject)}/bezwaren/upload`, {
    method: 'POST',
    body: formData,
  })
      .then((response) => {
        if (!response.ok) throw new Error('Upload mislukt');
        return response.json();
      })
      .then((data) => {
        uploadEl.removeAllFiles();
        const uploadZone = this.shadowRoot.querySelector('#upload-zone');
        if (uploadZone) uploadZone.hidden = true;

        if (data.fouten && data.fouten.length > 0) {
          const foutTekst = data.fouten.map((f) => `${f.bestandsnaam}: ${f.reden}`).join(', ');
          this._toonFout(`Sommige bestanden konden niet worden geupload: ${foutTekst}`);
        }

        this._laadBezwaren(this.__geselecteerdProject);
      })
      .catch(() => {
        this._toonFout('Upload mislukt.');
      })
      .finally(() => {
        this._zetBezig(false);
      });
}
```

**Step 5: Add `_verwijderBestanden()` method**

```javascript
_verwijderBestanden(bestandsnamen) {
  if (!bestandsnamen || bestandsnamen.length === 0 || !this.__geselecteerdProject) return;

  this._zetBezig(true);
  this._verbergFout();

  const verwijderPromises = bestandsnamen.map((naam) =>
    fetch(`/api/v1/projects/${encodeURIComponent(this.__geselecteerdProject)}/bezwaren/${encodeURIComponent(naam)}`, {
      method: 'DELETE',
    }),
  );

  Promise.all(verwijderPromises)
      .then(() => {
        this._laadBezwaren(this.__geselecteerdProject);
      })
      .catch(() => {
        this._toonFout('Verwijdering mislukt.');
      })
      .finally(() => {
        this._zetBezig(false);
      });
}
```

**Step 6: Update `_zetBezig` to also disable verwijder-knop and toevoegen-knop**

```javascript
_zetBezig(bezig) {
  this.__bezig = bezig;
  const extraheerKnop = this.shadowRoot && this.shadowRoot.querySelector('#extraheer-knop');
  const verwijderKnop = this.shadowRoot && this.shadowRoot.querySelector('#verwijder-knop');
  if (extraheerKnop) extraheerKnop.disabled = bezig;
  if (verwijderKnop) verwijderKnop.disabled = bezig;
}
```

**Step 7: Build frontend**

```bash
cd webapp && npm run build
```

Expected: Build succeeds.

**Step 8: Commit**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "feat: upload zone, verwijderknop en bevestigingsmodal in Documenten tab"
```

---

### Task 5: End-to-end verification

**Step 1: Build frontend**

```bash
cd webapp && npm run build
```

Expected: Clean build.

**Step 2: Compile backend**

```bash
cd app && mvn compile -q
```

Expected: BUILD SUCCESS.

**Step 3: Review final state**

Read all modified files and verify:
- `ProjectPoort` has `slaBestandOp` and `verwijderBestand`
- `BestandssysteemProjectAdapter` implements both with path traversal prevention
- `ExtractieTaakRepository` has `deleteByProjectNaamAndBestandsnaam`
- `ProjectService` has `uploadBezwaren` and `verwijderBezwaar` with `@Transactional`
- `ProjectController` has `POST /{naam}/bezwaren/upload` and `DELETE /{naam}/bezwaren/{bestandsnaam}`
- `application.yml` has multipart config (50MB file, 500MB request)
- Frontend has vl-upload import, vl-modal import, upload zone, verwijder-knop, modal
- Upload zone is hidden until "Bestanden toevoegen" is clicked
- Modal shows confirmation text with file count
- After upload/delete: bezwaren-tabel reloads
- Error handling: foutmelding shown for partial failures

**Step 4: Commit if fixes needed**

```bash
git add -A && git commit -m "fix: correcties na verificatie bezwaren beheer"
```
