# Document Download Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Gebruikers kunnen bezwaar-documenten downloaden door op de bestandsnaam in de tabel te klikken.

**Architecture:** Nieuw endpoint in ProjectController dat via ProjectService en ProjectPoort het bestandspad resolveert, met path traversal beveiliging in de adapter. Frontend rendert bestandsnamen als download-links.

**Tech Stack:** Spring Boot 3.4, Spring MVC (ResponseEntity<Resource>), Lit web components

---

### Task 1: Port-methode voor bestandspad

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectPoort.java`

**Step 1: Voeg methode toe aan interface**

```java
Path geefBestandsPad(String projectNaam, String bestandsnaam);
```

Voeg toe na de bestaande `geefBestandsnamen` methode. Import `java.nio.file.Path`.

**Step 2: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectPoort.java
git commit -m "feat: voeg geefBestandsPad toe aan ProjectPoort"
```

---

### Task 2: Adapter-implementatie met path traversal beveiliging

**Files:**
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapterTest.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapter.java`

**Step 1: Schrijf falende test — happy path**

Maak een nieuw testbestand als dat nog niet bestaat. Gebruik een tijdelijke directory met een testbestand:

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BestandssysteemProjectAdapterTest {

  @TempDir
  Path tempDir;

  private BestandssysteemProjectAdapter adapter;

  @BeforeEach
  void setUp() throws IOException {
    adapter = new BestandssysteemProjectAdapter(tempDir.toString());
    Path bezwarenDir = tempDir.resolve("testproject/bezwaren");
    Files.createDirectories(bezwarenDir);
    Files.writeString(bezwarenDir.resolve("bezwaar1.txt"), "inhoud");
  }

  @Test
  void geefBestandsPad_geeftPadVoorBestaandBestand() {
    Path result = adapter.geefBestandsPad("testproject", "bezwaar1.txt");
    assertThat(result).isEqualTo(tempDir.resolve("testproject/bezwaren/bezwaar1.txt"));
  }
}
```

**Step 2: Run test, verwacht FAIL**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn test -pl app -Dtest=BestandssysteemProjectAdapterTest -Dspring.profiles.active=dev -DfailIfNoTests=false`
Verwacht: FAIL — methode bestaat nog niet

**Step 3: Schrijf falende tests — foutscenario's**

Voeg toe aan dezelfde testklasse:

```java
@Test
void geefBestandsPad_gooitExceptieBijPathTraversal() {
  assertThatThrownBy(() -> adapter.geefBestandsPad("testproject", "../etc/passwd"))
      .isInstanceOf(IllegalArgumentException.class);
}

@Test
void geefBestandsPad_gooitExceptieBijOnbekendBestand() {
  assertThatThrownBy(() -> adapter.geefBestandsPad("testproject", "bestaat-niet.txt"))
      .isInstanceOf(BestandNietGevondenException.class);
}

@Test
void geefBestandsPad_gooitExceptieBijOnbekendProject() {
  assertThatThrownBy(() -> adapter.geefBestandsPad("onbekend", "bezwaar1.txt"))
      .isInstanceOf(ProjectNietGevondenException.class);
}
```

**Step 4: Implementeer `geefBestandsPad` in adapter**

Voeg toe aan `BestandssysteemProjectAdapter.java`:

```java
@Override
public Path geefBestandsPad(String projectNaam, String bestandsnaam) {
  if (bestandsnaam.contains("..") || bestandsnaam.contains("/") || bestandsnaam.contains("\\")) {
    throw new IllegalArgumentException("Ongeldige bestandsnaam: " + bestandsnaam);
  }

  Path projectDir = inputFolder.resolve(projectNaam).normalize();
  if (!projectDir.startsWith(inputFolder) || !Files.isDirectory(projectDir)) {
    throw new ProjectNietGevondenException(projectNaam);
  }

  Path bestandsPad = projectDir.resolve("bezwaren").resolve(bestandsnaam).normalize();
  if (!bestandsPad.startsWith(inputFolder) || !Files.exists(bestandsPad)) {
    throw new BestandNietGevondenException(bestandsnaam);
  }

  return bestandsPad;
}
```

**Step 5: Maak `BestandNietGevondenException`**

Maak `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandNietGevondenException.java`:

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

public class BestandNietGevondenException extends RuntimeException {
  private final String bestandsnaam;

  public BestandNietGevondenException(String bestandsnaam) {
    super("Bestand '%s' bestaat niet".formatted(bestandsnaam));
    this.bestandsnaam = bestandsnaam;
  }

  public String getBestandsnaam() {
    return bestandsnaam;
  }
}
```

**Step 6: Run tests, verwacht PASS**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn test -pl app -Dtest=BestandssysteemProjectAdapterTest -Dspring.profiles.active=dev`
Verwacht: 4 tests PASS

**Step 7: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapter.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandNietGevondenException.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapterTest.java
git commit -m "feat: geefBestandsPad in adapter met path traversal beveiliging"
```

---

### Task 3: Service-methode voor download

**Files:**
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java` (als die bestaat, anders maak nieuw)
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java`

**Step 1: Schrijf falende test**

Voeg toe aan ProjectServiceTest (of maak nieuw):

```java
@Test
void geefBestandsPad_delegeertNaarPoort() {
  Path verwacht = Path.of("/tmp/test/bezwaren/bezwaar1.txt");
  when(projectPoort.geefBestandsPad("project1", "bezwaar1.txt")).thenReturn(verwacht);

  Path result = projectService.geefBestandsPad("project1", "bezwaar1.txt");

  assertThat(result).isEqualTo(verwacht);
  verify(projectPoort).geefBestandsPad("project1", "bezwaar1.txt");
}
```

**Step 2: Implementeer in ProjectService**

```java
public Path geefBestandsPad(String projectNaam, String bestandsnaam) {
  return projectPoort.geefBestandsPad(projectNaam, bestandsnaam);
}
```

**Step 3: Run tests, verwacht PASS**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn test -pl app -Dtest=ProjectServiceTest -Dspring.profiles.active=dev`
Verwacht: PASS

**Step 4: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java
git commit -m "feat: geefBestandsPad in ProjectService"
```

---

### Task 4: Download endpoint in controller

**Files:**
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectControllerTest.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java`

**Step 1: Schrijf falende test — happy path**

Voeg toe aan `ProjectControllerTest.java`:

```java
@Test
@WithMockUser
void downloadBestand_stuurtBestandAlsBijlage() throws Exception {
  Path tempFile = Files.createTempFile("test", ".txt");
  Files.writeString(tempFile, "testinhoud");
  when(projectService.geefBestandsPad("project1", "bezwaar1.txt")).thenReturn(tempFile);

  mockMvc.perform(get("/api/v1/projects/project1/bezwaren/bezwaar1.txt/download"))
      .andExpect(status().isOk())
      .andExpect(header().string("Content-Disposition", "attachment; filename=\"bezwaar1.txt\""))
      .andExpect(content().string("testinhoud"));

  Files.deleteIfExists(tempFile);
}
```

**Step 2: Schrijf falende test — bestand niet gevonden**

```java
@Test
@WithMockUser
void downloadBestand_geeft404VoorOnbekendBestand() throws Exception {
  when(projectService.geefBestandsPad("project1", "onbekend.txt"))
      .thenThrow(new BestandNietGevondenException("onbekend.txt"));

  mockMvc.perform(get("/api/v1/projects/project1/bezwaren/onbekend.txt/download"))
      .andExpect(status().isNotFound());
}
```

**Step 3: Schrijf falende test — path traversal**

```java
@Test
@WithMockUser
void downloadBestand_geeft400BijPathTraversal() throws Exception {
  when(projectService.geefBestandsPad(eq("project1"), anyString()))
      .thenThrow(new IllegalArgumentException("Ongeldige bestandsnaam"));

  mockMvc.perform(get("/api/v1/projects/project1/bezwaren/..%2Fetc%2Fpasswd/download"))
      .andExpect(status().isBadRequest());
}
```

**Step 4: Run tests, verwacht FAIL**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn test -pl app -Dtest=ProjectControllerTest -Dspring.profiles.active=dev`
Verwacht: FAIL — endpoint bestaat nog niet

**Step 5: Implementeer download endpoint**

Voeg toe aan `ProjectController.java`:

```java
@GetMapping("/{naam}/bezwaren/{bestandsnaam}/download")
public ResponseEntity<Resource> downloadBestand(
    @PathVariable String naam,
    @PathVariable String bestandsnaam) throws IOException {
  Path pad = projectService.geefBestandsPad(naam, bestandsnaam);
  Resource resource = new UrlResource(pad.toUri());

  String contentType = Files.probeContentType(pad);
  if (contentType == null) {
    contentType = "application/octet-stream";
  }

  return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(contentType))
      .header(HttpHeaders.CONTENT_DISPOSITION,
          "attachment; filename=\"" + bestandsnaam + "\"")
      .body(resource);
}
```

Voeg exception handlers toe (in dezelfde controller of een bestaande `@ControllerAdvice`):

```java
@ExceptionHandler(BestandNietGevondenException.class)
public ResponseEntity<Map<String, String>> handleBestandNietGevonden(BestandNietGevondenException e) {
  return ResponseEntity.status(HttpStatus.NOT_FOUND)
      .body(Map.of("error", "bestand.not-found", "bestandsnaam", e.getBestandsnaam()));
}

@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<Map<String, String>> handleOngeldigeBestandsnaam(IllegalArgumentException e) {
  return ResponseEntity.badRequest()
      .body(Map.of("error", "invalid.filename", "message", e.getMessage()));
}
```

Imports toe te voegen:
```java
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
```

**Step 6: Run tests, verwacht PASS**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn test -pl app -Dtest=ProjectControllerTest -Dspring.profiles.active=dev`
Verwacht: alle tests PASS

**Step 7: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectControllerTest.java
git commit -m "feat: download endpoint voor bezwaar-documenten"
```

---

### Task 5: Frontend — bestandsnaam als download-link

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js`

**Step 1: Voeg project property toe aan tabel-component**

In `bezwaarschriften-bezwaren-tabel.js`, voeg een `projectNaam` property toe aan de klasse. In de setter, sla de waarde op:

```javascript
set projectNaam(naam) {
  this._projectNaam = naam;
}

get projectNaam() {
  return this._projectNaam;
}
```

**Step 2: Render bestandsnaam als download-link**

In `_renderRijen()`, vervang de cel waar bestandsnaam als tekst getoond wordt door een `<a>` tag:

Zoek de regel die de bestandsnaam in een `<td>` zet (in de `_renderRijen` methode). Vervang:
```javascript
// Waar nu staat iets als:
td.textContent = bezwaar.bestandsnaam;
```

Door:
```javascript
if (this._projectNaam) {
  const link = document.createElement('a');
  link.href = `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/bezwaren/${encodeURIComponent(bezwaar.bestandsnaam)}/download`;
  link.download = bezwaar.bestandsnaam;
  link.textContent = bezwaar.bestandsnaam;
  td.appendChild(link);
} else {
  td.textContent = bezwaar.bestandsnaam;
}
```

**Step 3: Geef projectnaam door vanuit parent**

In `bezwaarschriften-project-selectie.js`, in `_laadBezwaren()` (regel ~201), na `tabel.bezwaren = this.__bezwaren;`:

```javascript
tabel.projectNaam = projectNaam;
```

**Step 4: Test handmatig**

Start de applicatie met `mvn spring-boot:run -pl app -Dspring-boot.run.profiles=dev` en de webapp met `npm start` in de webapp directory. Selecteer een project, klik op een bestandsnaam — het bestand moet downloaden.

**Step 5: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js \
       webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "feat: bestandsnamen als download-links in documententabel"
```

---

### Task 6: Volledige test-suite draaien

**Step 1: Run alle backend tests**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn test -pl app -Dspring.profiles.active=dev`
Verwacht: alle tests PASS

**Step 2: Fix eventuele fouten**

Als er fouten zijn, fix ze en commit.

**Step 3: Build webapp**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften/webapp && npm run build`
Verwacht: geen fouten
