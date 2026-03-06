# DECIBEL-1706: Project Keuze UI — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Voeg een UI toe waarmee de gebruiker een project kan selecteren, de bijhorende bezwaarbestanden ziet en batchverwerking (via IngestiePoort) kan starten.

**Architecture:** Hexagonale architectuur: nieuw `project`-package met port (`ProjectPoort`) + filesystem adapter + in-memory statusregister. REST controller exposeert 3 endpoints. Twee nieuwe Lit web components in de frontend.

**Tech Stack:** Java 21, Spring Boot 3.4, JUnit 5 + AssertJ + Mockito, @MockBean, @WebMvcTest. Frontend: Lit + @domg-wc (VlSelectComponent, VlTableComponent, VlButtonComponent).

---

## Constraint: Google Checkstyle

De build valideert checkstyle (Google Java style). Regels:
- 2-spatie inspringing (niet 4)
- Accolade op dezelfde regel als statement
- Javadoc verplicht op public klassen en methodes
- Geen trailing whitespace
- Maximale regellengte: 100 tekens

---

## Task 1: BezwaarBestandStatus enum

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestandStatus.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestandStatusTest.java`

**Step 1: Write failing test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BezwaarBestandStatusTest {

  @Test
  void heeftVierStatussen() {
    assertThat(BezwaarBestandStatus.values()).hasSize(4);
  }

  @Test
  void bevatAlleVerwachteWaarden() {
    assertThat(BezwaarBestandStatus.TODO).isNotNull();
    assertThat(BezwaarBestandStatus.EXTRACTIE_KLAAR).isNotNull();
    assertThat(BezwaarBestandStatus.FOUT).isNotNull();
    assertThat(BezwaarBestandStatus.NIET_ONDERSTEUND).isNotNull();
  }
}
```

**Step 2: Run test to verify it fails**

```bash
cd app && mvn test -pl . -Dtest=BezwaarBestandStatusTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -20
```
Expected: FAIL — class not found

**Step 3: Write implementation**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Status van een bezwaarbestand in de verwerkingspipeline.
 */
public enum BezwaarBestandStatus {
  TODO,
  EXTRACTIE_KLAAR,
  FOUT,
  NIET_ONDERSTEUND
}
```

**Step 4: Run test to verify it passes**

```bash
cd app && mvn test -pl . -Dtest=BezwaarBestandStatusTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -10
```
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestandStatus.java \
        app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestandStatusTest.java
git commit -m "feat(DECIBEL-1706): add BezwaarBestandStatus enum"
```

---

## Task 2: BezwaarBestand record

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestand.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestandTest.java`

**Step 1: Write failing test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BezwaarBestandTest {

  @Test
  void maaktRecordAanMetBestandsnaamEnStatus() {
    var bestand = new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.TODO);

    assertThat(bestand.bestandsnaam()).isEqualTo("bezwaar-001.txt");
    assertThat(bestand.status()).isEqualTo(BezwaarBestandStatus.TODO);
  }

  @Test
  void maaktKopieMetAndereStatus() {
    var bestand = new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.TODO);
    var bijgewerkt = bestand.withStatus(BezwaarBestandStatus.EXTRACTIE_KLAAR);

    assertThat(bijgewerkt.bestandsnaam()).isEqualTo("bezwaar-001.txt");
    assertThat(bijgewerkt.status()).isEqualTo(BezwaarBestandStatus.EXTRACTIE_KLAAR);
    assertThat(bestand.status()).isEqualTo(BezwaarBestandStatus.TODO); // original unchanged
  }
}
```

**Step 2: Run to verify fail**

```bash
cd app && mvn test -pl . -Dtest=BezwaarBestandTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -20
```

**Step 3: Write implementation**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Representeert een bezwaarbestand met zijn verwerkingsstatus.
 *
 * @param bestandsnaam Naam van het bezwaarbestand
 * @param status Huidige verwerkingsstatus
 */
public record BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status) {

  /**
   * Geeft een nieuwe instantie terug met een bijgewerkte status.
   *
   * @param nieuweStatus De nieuwe status
   * @return Nieuwe BezwaarBestand instantie
   */
  public BezwaarBestand withStatus(BezwaarBestandStatus nieuweStatus) {
    return new BezwaarBestand(this.bestandsnaam, nieuweStatus);
  }
}
```

**Step 4: Run to verify pass**

```bash
cd app && mvn test -pl . -Dtest=BezwaarBestandTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -10
```

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestand.java \
        app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestandTest.java
git commit -m "feat(DECIBEL-1706): add BezwaarBestand record"
```

---

## Task 3: ProjectPoort interface + BestandssysteemProjectAdapter

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectPoort.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapter.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapterTest.java`

**Step 1: Write failing tests**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BestandssysteemProjectAdapterTest {

  @TempDir
  Path inputFolder;

  private BestandssysteemProjectAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new BestandssysteemProjectAdapter(inputFolder);
  }

  @Test
  void geeftProjectenTerug() throws Exception {
    Files.createDirectory(inputFolder.resolve("windmolens"));
    Files.createDirectory(inputFolder.resolve("zonnepanelen"));

    var projecten = adapter.geefProjecten();

    assertThat(projecten).containsExactlyInAnyOrder("windmolens", "zonnepanelen");
  }

  @Test
  void negeertLosseBestandenInInputFolder() throws Exception {
    Files.createDirectory(inputFolder.resolve("windmolens"));
    Files.writeString(inputFolder.resolve("readme.txt"), "inhoud");

    var projecten = adapter.geefProjecten();

    assertThat(projecten).containsExactly("windmolens");
  }

  @Test
  void geeftLegeProjectenlijstAlsInputFolderLeegIs() {
    var projecten = adapter.geefProjecten();

    assertThat(projecten).isEmpty();
  }

  @Test
  void geeftLegeProjectenlijstAlsInputFolderNietBestaat() throws Exception {
    var adapter = new BestandssysteemProjectAdapter(inputFolder.resolve("bestaat-niet"));

    var projecten = adapter.geefProjecten();

    assertThat(projecten).isEmpty();
  }

  @Test
  void geeftBestandsnamenVanProject() throws Exception {
    var bezwarenMap = inputFolder.resolve("windmolens").resolve("bezwaren");
    Files.createDirectories(bezwarenMap);
    Files.writeString(bezwarenMap.resolve("bezwaar-001.txt"), "inhoud");
    Files.writeString(bezwarenMap.resolve("bijlage.pdf"), "pdf inhoud");

    var bestandsnamen = adapter.geefBestandsnamen("windmolens");

    assertThat(bestandsnamen).containsExactlyInAnyOrder("bezwaar-001.txt", "bijlage.pdf");
  }

  @Test
  void geeftLegeBestandsnamenAlsBezwarenMapNietBestaat() throws Exception {
    Files.createDirectory(inputFolder.resolve("windmolens"));

    var bestandsnamen = adapter.geefBestandsnamen("windmolens");

    assertThat(bestandsnamen).isEmpty();
  }

  @Test
  void geeftLegeBestandsnamenAlsBezwarenMapLeegIs() throws Exception {
    Files.createDirectories(inputFolder.resolve("windmolens").resolve("bezwaren"));

    var bestandsnamen = adapter.geefBestandsnamen("windmolens");

    assertThat(bestandsnamen).isEmpty();
  }

  @Test
  void gooidExceptionVoorOnbekendProject() {
    var exception = assertThrows(
        ProjectNietGevondenException.class,
        () -> adapter.geefBestandsnamen("bestaat-niet")
    );

    assertThat(exception.getProjectNaam()).isEqualTo("bestaat-niet");
  }
}
```

**Step 2: Run to verify fail**

```bash
cd app && mvn test -pl . -Dtest=BestandssysteemProjectAdapterTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -20
```

**Step 3: Write implementation**

First, the `ProjectNietGevondenException`:

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Wordt gegooid wanneer een gevraagd project niet bestaat.
 */
public class ProjectNietGevondenException extends RuntimeException {

  private final String projectNaam;

  /**
   * Maakt een nieuwe ProjectNietGevondenException aan.
   *
   * @param projectNaam Naam van het niet-gevonden project
   */
  public ProjectNietGevondenException(String projectNaam) {
    super("Project '%s' bestaat niet".formatted(projectNaam));
    this.projectNaam = projectNaam;
  }

  /**
   * Geeft de naam van het niet-gevonden project terug.
   *
   * @return Projectnaam
   */
  public String getProjectNaam() {
    return projectNaam;
  }
}
```

Then the `ProjectPoort` interface:

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;

/**
 * Port interface voor het opvragen van projecten en bezwaarbestanden.
 */
public interface ProjectPoort {

  /**
   * Geeft de lijst van beschikbare projecten terug.
   *
   * @return Lijst van projectnamen
   */
  List<String> geefProjecten();

  /**
   * Geeft de bestandsnamen in de bezwaren-map van een project terug.
   *
   * @param projectNaam Naam van het project
   * @return Lijst van bestandsnamen
   * @throws ProjectNietGevondenException Als het project niet bestaat
   */
  List<String> geefBestandsnamen(String projectNaam);
}
```

Then the `BestandssysteemProjectAdapter`:

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adapter die projecten en bezwaarbestanden leest van het bestandssysteem.
 */
@Component
public class BestandssysteemProjectAdapter implements ProjectPoort {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Path inputFolder;

  /**
   * Maakt een nieuwe BestandssysteemProjectAdapter aan.
   *
   * @param inputFolder Pad naar de input-folder met projecten
   */
  public BestandssysteemProjectAdapter(
      @Value("${bezwaarschriften.input.folder}") Path inputFolder) {
    this.inputFolder = inputFolder;
  }

  @Override
  public List<String> geefProjecten() {
    if (!Files.exists(inputFolder)) {
      return Collections.emptyList();
    }
    try (var stream = Files.list(inputFolder)) {
      return stream
          .filter(Files::isDirectory)
          .map(pad -> pad.getFileName().toString())
          .toList();
    } catch (IOException e) {
      LOGGER.warn("Kon input-folder niet lezen: {}", inputFolder, e);
      return Collections.emptyList();
    }
  }

  @Override
  public List<String> geefBestandsnamen(String projectNaam) {
    var projectPad = inputFolder.resolve(projectNaam);
    if (!Files.isDirectory(projectPad)) {
      throw new ProjectNietGevondenException(projectNaam);
    }
    var bezwarenPad = projectPad.resolve("bezwaren");
    if (!Files.isDirectory(bezwarenPad)) {
      return Collections.emptyList();
    }
    try (var stream = Files.list(bezwarenPad)) {
      return stream
          .filter(pad -> !Files.isDirectory(pad))
          .map(pad -> pad.getFileName().toString())
          .toList();
    } catch (IOException e) {
      LOGGER.warn("Kon bezwaren-map niet lezen voor project '{}': {}", projectNaam, bezwarenPad, e);
      return Collections.emptyList();
    }
  }
}
```

**Step 4: Add property to application.yml**

Add to `app/src/main/resources/application.yml` (under the root section):

```yaml
bezwaarschriften:
  input:
    folder: input
```

**Step 5: Run tests to verify pass**

```bash
cd app && mvn test -pl . -Dtest=BestandssysteemProjectAdapterTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -10
```

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ \
        app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapterTest.java \
        app/src/main/resources/application.yml
git commit -m "feat(DECIBEL-1706): add ProjectPoort + BestandssysteemProjectAdapter"
```

---

## Task 4: ProjectService (orchestrates status + filesystem + ingestie)

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java`

**Step 1: Write failing tests**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.FileIngestionException;
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

  @Mock
  private ProjectPoort projectPoort;

  @Mock
  private IngestiePoort ingestiePoort;

  private ProjectService service;

  @BeforeEach
  void setUp() {
    service = new ProjectService(projectPoort, ingestiePoort, Path.of("input"));
  }

  @Test
  void geeftProjectenTerug() {
    when(projectPoort.geefProjecten()).thenReturn(List.of("windmolens", "zonnepanelen"));

    var projecten = service.geefProjecten();

    assertThat(projecten).containsExactly("windmolens", "zonnepanelen");
  }

  @Test
  void geeftBezwarenMetInitieleStatussen() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.txt", "bijlage.pdf"));

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren).hasSize(2);
    assertThat(bezwaren).anySatisfy(b -> {
      assertThat(b.bestandsnaam()).isEqualTo("bezwaar-001.txt");
      assertThat(b.status()).isEqualTo(BezwaarBestandStatus.TODO);
    });
    assertThat(bezwaren).anySatisfy(b -> {
      assertThat(b.bestandsnaam()).isEqualTo("bijlage.pdf");
      assertThat(b.status()).isEqualTo(BezwaarBestandStatus.NIET_ONDERSTEUND);
    });
  }

  @Test
  void verwerkingZetStatusOpExtractieKlaarBijSucces() throws Exception {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.txt"));

    var resultaat = service.verwerk("windmolens");

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).status()).isEqualTo(BezwaarBestandStatus.EXTRACTIE_KLAAR);
    verify(ingestiePoort, times(1))
        .leesBestand(Path.of("input", "windmolens", "bezwaren", "bezwaar-001.txt"));
  }

  @Test
  void verwerkingGaatDoorBijFoutOpEnkelBestand() throws Exception {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-goed.txt", "bezwaar-kapot.txt"));
    when(ingestiePoort.leesBestand(Path.of("input", "windmolens", "bezwaren", "bezwaar-kapot.txt")))
        .thenThrow(new FileIngestionException("Kan niet lezen"));

    var resultaat = service.verwerk("windmolens");

    assertThat(resultaat).hasSize(2);
    assertThat(resultaat).anySatisfy(b -> {
      assertThat(b.bestandsnaam()).isEqualTo("bezwaar-goed.txt");
      assertThat(b.status()).isEqualTo(BezwaarBestandStatus.EXTRACTIE_KLAAR);
    });
    assertThat(resultaat).anySatisfy(b -> {
      assertThat(b.bestandsnaam()).isEqualTo("bezwaar-kapot.txt");
      assertThat(b.status()).isEqualTo(BezwaarBestandStatus.FOUT);
    });
  }

  @Test
  void slaatNietOndersteundeBestandenOverBijVerwerking() throws Exception {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar.txt", "bijlage.pdf"));

    var resultaat = service.verwerk("windmolens");

    verify(ingestiePoort, never())
        .leesBestand(Path.of("input", "windmolens", "bezwaren", "bijlage.pdf"));
    assertThat(resultaat).anySatisfy(b -> {
      assertThat(b.bestandsnaam()).isEqualTo("bijlage.pdf");
      assertThat(b.status()).isEqualTo(BezwaarBestandStatus.NIET_ONDERSTEUND);
    });
  }

  @Test
  void herverwerktNietWatAlExtractieKlaarIs() throws Exception {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar.txt"));

    service.verwerk("windmolens"); // eerste verwerking
    service.verwerk("windmolens"); // tweede verwerking

    // IngestiePoort slechts één keer aangeroepen
    verify(ingestiePoort, times(1))
        .leesBestand(Path.of("input", "windmolens", "bezwaren", "bezwaar.txt"));
  }

  @Test
  void gooidExceptionVoorOnbekendProjectBijGeefBezwaren() {
    when(projectPoort.geefBestandsnamen("bestaat-niet"))
        .thenThrow(new ProjectNietGevondenException("bestaat-niet"));

    assertThrows(
        ProjectNietGevondenException.class,
        () -> service.geefBezwaren("bestaat-niet")
    );
  }
}
```

**Step 2: Run to verify fail**

```bash
cd app && mvn test -pl . -Dtest=ProjectServiceTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -20
```

**Step 3: Write implementation**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service die projecten en bezwaarverwerking orkestreert.
 *
 * <p>Beheert in-memory verwerkingsstatussen per sessie.
 */
@Service
public class ProjectService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ProjectPoort projectPoort;
  private final IngestiePoort ingestiePoort;
  private final Path inputFolder;

  /** In-memory statusregister: projectNaam → bestandsnaam → status. */
  private final Map<String, Map<String, BezwaarBestandStatus>> statusRegister =
      new ConcurrentHashMap<>();

  /**
   * Maakt een nieuwe ProjectService aan.
   *
   * @param projectPoort Port voor filesystem toegang
   * @param ingestiePoort Port voor bestandsingestie
   * @param inputFolder Root input folder
   */
  public ProjectService(
      ProjectPoort projectPoort,
      IngestiePoort ingestiePoort,
      @Value("${bezwaarschriften.input.folder}") Path inputFolder) {
    this.projectPoort = projectPoort;
    this.ingestiePoort = ingestiePoort;
    this.inputFolder = inputFolder;
  }

  /**
   * Geeft de lijst van beschikbare projecten terug.
   *
   * @return Lijst van projectnamen
   */
  public List<String> geefProjecten() {
    return projectPoort.geefProjecten();
  }

  /**
   * Geeft de bezwaarbestanden van een project terug met hun huidige status.
   *
   * @param projectNaam Naam van het project
   * @return Lijst van bezwaarbestanden met status
   * @throws ProjectNietGevondenException Als het project niet bestaat
   */
  public List<BezwaarBestand> geefBezwaren(String projectNaam) {
    var bestandsnamen = projectPoort.geefBestandsnamen(projectNaam);
    return bestandsnamen.stream()
        .map(naam -> new BezwaarBestand(naam, bepaalStatus(projectNaam, naam)))
        .toList();
  }

  /**
   * Start de batchverwerking voor alle openstaande .txt-bestanden van een project.
   *
   * @param projectNaam Naam van het project
   * @return Bijgewerkte lijst van bezwaarbestanden met status
   * @throws ProjectNietGevondenException Als het project niet bestaat
   */
  public List<BezwaarBestand> verwerk(String projectNaam) {
    var bezwaren = geefBezwaren(projectNaam);
    var projectStatussen = statusRegister.computeIfAbsent(projectNaam,
        k -> new ConcurrentHashMap<>());

    for (var bestand : bezwaren) {
      if (bestand.status() != BezwaarBestandStatus.TODO) {
        continue;
      }
      var bestandsPad = inputFolder.resolve(projectNaam).resolve("bezwaren")
          .resolve(bestand.bestandsnaam());
      try {
        ingestiePoort.leesBestand(bestandsPad);
        projectStatussen.put(bestand.bestandsnaam(), BezwaarBestandStatus.EXTRACTIE_KLAAR);
        LOGGER.info("Bestand '{}' succesvol verwerkt voor project '{}'",
            bestand.bestandsnaam(), projectNaam);
      } catch (Exception e) {
        projectStatussen.put(bestand.bestandsnaam(), BezwaarBestandStatus.FOUT);
        LOGGER.warn("Fout bij verwerking van '{}' voor project '{}': {}",
            bestand.bestandsnaam(), projectNaam, e.getMessage());
      }
    }

    return geefBezwaren(projectNaam);
  }

  private BezwaarBestandStatus bepaalStatus(String projectNaam, String bestandsnaam) {
    var projectStatussen = statusRegister.get(projectNaam);
    if (projectStatussen != null && projectStatussen.containsKey(bestandsnaam)) {
      return projectStatussen.get(bestandsnaam);
    }
    return isTxtBestand(bestandsnaam)
        ? BezwaarBestandStatus.TODO
        : BezwaarBestandStatus.NIET_ONDERSTEUND;
  }

  private boolean isTxtBestand(String bestandsnaam) {
    return bestandsnaam.toLowerCase().endsWith(".txt");
  }
}
```

**Step 4: Run tests to verify pass**

```bash
cd app && mvn test -pl . -Dtest=ProjectServiceTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -10
```

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java \
        app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java
git commit -m "feat(DECIBEL-1706): add ProjectService with in-memory status register"
```

---

## Task 5: REST controller + exception handler

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/GlobalExceptionHandler.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectControllerTest.java`

**Step 1: Write failing tests**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectController.class)
@WithMockUser
class ProjectControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ProjectService projectService;

  @Test
  void geeftProjectenTerug() throws Exception {
    when(projectService.geefProjecten()).thenReturn(List.of("windmolens", "zonnepanelen"));

    mockMvc.perform(get("/api/v1/projects"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projecten[0]").value("windmolens"))
        .andExpect(jsonPath("$.projecten[1]").value("zonnepanelen"));
  }

  @Test
  void geeftLegeProjectenLijstTerug() throws Exception {
    when(projectService.geefProjecten()).thenReturn(List.of());

    mockMvc.perform(get("/api/v1/projects"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projecten").isEmpty());
  }

  @Test
  void geeftBezwarenTerugVoorProject() throws Exception {
    when(projectService.geefBezwaren("windmolens")).thenReturn(List.of(
        new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.TODO),
        new BezwaarBestand("bijlage.pdf", BezwaarBestandStatus.NIET_ONDERSTEUND)
    ));

    mockMvc.perform(get("/api/v1/projects/windmolens/bezwaren"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bezwaren[0].bestandsnaam").value("bezwaar-001.txt"))
        .andExpect(jsonPath("$.bezwaren[0].status").value("todo"))
        .andExpect(jsonPath("$.bezwaren[1].bestandsnaam").value("bijlage.pdf"))
        .andExpect(jsonPath("$.bezwaren[1].status").value("niet ondersteund"));
  }

  @Test
  void geeft404VoorOnbekendProject() throws Exception {
    when(projectService.geefBezwaren("bestaat-niet"))
        .thenThrow(new ProjectNietGevondenException("bestaat-niet"));

    mockMvc.perform(get("/api/v1/projects/bestaat-niet/bezwaren"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messages[0].code").value("project.not-found"))
        .andExpect(jsonPath("$.messages[0].parameters.naam").value("bestaat-niet"));
  }

  @Test
  void starktBatchverwerkingEnGeeftStatusTerug() throws Exception {
    when(projectService.verwerk("windmolens")).thenReturn(List.of(
        new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.EXTRACTIE_KLAAR)
    ));

    mockMvc.perform(post("/api/v1/projects/windmolens/verwerk"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bezwaren[0].bestandsnaam").value("bezwaar-001.txt"))
        .andExpect(jsonPath("$.bezwaren[0].status").value("extractie-klaar"));
  }

  @Test
  void geeft404VoorOnbekendProjectBijVerwerk() throws Exception {
    when(projectService.verwerk("bestaat-niet"))
        .thenThrow(new ProjectNietGevondenException("bestaat-niet"));

    mockMvc.perform(post("/api/v1/projects/bestaat-niet/verwerk"))
        .andExpect(status().isNotFound());
  }
}
```

**Step 2: Run to verify fail**

```bash
cd app && mvn test -pl . -Dtest=ProjectControllerTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -20
```

**Step 3: Write the GlobalExceptionHandler**

```java
package be.vlaanderen.omgeving.bezwaarschriften.config;

import be.vlaanderen.omgeving.bezwaarschriften.project.ProjectNietGevondenException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Gecentraliseerde exception handling voor REST controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Handelt ProjectNietGevondenException af met HTTP 404.
   *
   * @param e De exception
   * @return Gestructureerde foutrespons
   */
  @ExceptionHandler(ProjectNietGevondenException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, List<Map<String, Object>>> handleProjectNietGevonden(
      ProjectNietGevondenException e) {
    return Map.of("messages", List.of(
        Map.of("code", "project.not-found",
            "parameters", Map.of("naam", e.getProjectNaam()))
    ));
  }
}
```

**Step 4: Write the ProjectController**

Note: enum serialization — use `@JsonValue` annotation on status via a custom serializer, OR map status to lowercase string in a response DTO.

Use response DTOs to control serialization:

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller voor project- en bezwaarbeheer.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

  private final ProjectService projectService;

  /**
   * Maakt een nieuwe ProjectController aan.
   *
   * @param projectService Service voor projectbeheer
   */
  public ProjectController(ProjectService projectService) {
    this.projectService = projectService;
  }

  /**
   * Geeft de lijst van beschikbare projecten terug.
   *
   * @return Projectenlijst
   */
  @GetMapping
  public ResponseEntity<ProjectenResponse> geefProjecten() {
    var projecten = projectService.geefProjecten();
    return ResponseEntity.ok(new ProjectenResponse(projecten));
  }

  /**
   * Geeft de bezwaarbestanden van een project terug met hun status.
   *
   * @param naam Projectnaam
   * @return Bezwarenlijst met statussen
   */
  @GetMapping("/{naam}/bezwaren")
  public ResponseEntity<BezwarenResponse> geefBezwaren(@PathVariable String naam) {
    var bezwaren = projectService.geefBezwaren(naam);
    return ResponseEntity.ok(BezwarenResponse.van(bezwaren));
  }

  /**
   * Start de batchverwerking voor alle openstaande bezwaren van een project.
   *
   * @param naam Projectnaam
   * @return Bijgewerkte bezwarenlijst met statussen
   */
  @PostMapping("/{naam}/verwerk")
  public ResponseEntity<BezwarenResponse> verwerk(@PathVariable String naam) {
    var bezwaren = projectService.verwerk(naam);
    return ResponseEntity.ok(BezwarenResponse.van(bezwaren));
  }

  /** Response DTO voor projectenlijst. */
  record ProjectenResponse(List<String> projecten) {}

  /** Response DTO voor bezwarenlijst. */
  record BezwarenResponse(List<BezwaarBestandDto> bezwaren) {

    static BezwarenResponse van(List<BezwaarBestand> bezwaren) {
      return new BezwarenResponse(bezwaren.stream()
          .map(b -> new BezwaarBestandDto(b.bestandsnaam(), statusNaarString(b.status())))
          .toList());
    }

    private static String statusNaarString(BezwaarBestandStatus status) {
      return switch (status) {
        case TODO -> "todo";
        case EXTRACTIE_KLAAR -> "extractie-klaar";
        case FOUT -> "fout";
        case NIET_ONDERSTEUND -> "niet ondersteund";
      };
    }
  }

  /** DTO voor een enkel bezwaarbestand in de response. */
  record BezwaarBestandDto(String bestandsnaam, String status) {}
}
```

**Step 5: Run tests to verify pass**

```bash
cd app && mvn test -pl . -Dtest=ProjectControllerTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -10
```

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java \
        app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/GlobalExceptionHandler.java \
        app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectControllerTest.java
git commit -m "feat(DECIBEL-1706): add ProjectController + GlobalExceptionHandler"
```

---

## Task 6: Security config — permit /api/v1/**

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/SecurityConfiguration.java`

**Step 1: Update SecurityConfiguration**

Huidige code:
```java
.requestMatchers("/admin/health/**", "/admin/info").permitAll()
.anyRequest().authenticated()
```

Vervang door:
```java
.requestMatchers("/admin/health/**", "/admin/info", "/api/v1/**").permitAll()
.anyRequest().authenticated()
```

**Step 2: Run all tests**

```bash
cd app && mvn test 2>&1 | tail -20
```
Expected: BUILD SUCCESS

**Step 3: Verify checkstyle passes**

```bash
cd app && mvn checkstyle:check 2>&1 | tail -20
```

**Step 4: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/SecurityConfiguration.java
git commit -m "feat(DECIBEL-1706): permit /api/v1/** in security config"
```

---

## Task 7: Frontend — bezwaarschriften-bezwaren-tabel component

**Files:**
- Create: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

**Step 1: Write the component**

```js
import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlTableComponent} from '@domg-wc/components/block/table/vl-table.component.js';
import {vlGlobalStyles} from '@domg-wc/styles';

registerWebComponents([VlTableComponent]);

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
      <style>${vlGlobalStyles}</style>
      <vl-table>
        <table>
          <thead>
            <tr>
              <th>Bestandsnaam</th>
              <th>Status</th>
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
    this.#renderRijen();
  }

  get bezwaren() {
    return this.__bezwaren;
  }

  connectedCallback() {
    super.connectedCallback();
    this.#renderRijen();
  }

  #renderRijen() {
    const tbody = this.shadowRoot?.querySelector('#tabel-body');
    if (!tbody) return;

    if (this.__bezwaren.length === 0) {
      tbody.innerHTML = '<tr><td colspan="2">Geen bestanden gevonden</td></tr>';
      return;
    }

    tbody.innerHTML = this.__bezwaren
        .map(b => `<tr>
          <td>${this.#escapeHtml(b.bestandsnaam)}</td>
          <td>${STATUS_LABELS[b.status] || b.status}</td>
        </tr>`)
        .join('');
  }

  #escapeHtml(str) {
    const div = document.createElement('div');
    div.appendChild(document.createTextNode(str));
    return div.innerHTML;
  }
}

defineWebComponent(BezwaarschriftenBezwarenTabel, 'bezwaarschriften-bezwaren-tabel');
```

**Step 2: Verify it builds**

```bash
cd webapp && npm run build 2>&1 | tail -20
```
Expected: build succeeds

**Step 3: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "feat(DECIBEL-1706): add bezwaarschriften-bezwaren-tabel component"
```

---

## Task 8: Frontend — bezwaarschriften-project-selectie component

**Files:**
- Create: `webapp/src/js/bezwaarschriften-project-selectie.js`

**Step 1: Write the component**

```js
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
        <vl-button-component id="verwerk-knop" disabled>Verwerk alles</vl-button-component>
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
    this.#laadProjecten();
    this.#koppelEventListeners();
  }

  async #laadProjecten() {
    try {
      const response = await fetch('/api/v1/projects');
      if (!response.ok) throw new Error('Ophalen projecten mislukt');
      const data = await response.json();
      this.__projecten = data.projecten;
      const selectEl = this.shadowRoot?.querySelector('#project-select');
      if (selectEl) {
        selectEl.options = this.__projecten.map(naam => ({value: naam, label: naam}));
      }
    } catch (e) {
      this.#toonFout('Projecten konden niet worden geladen.');
    }
  }

  #koppelEventListeners() {
    const selectEl = this.shadowRoot?.querySelector('#project-select');
    const verwerkKnop = this.shadowRoot?.querySelector('#verwerk-knop');

    if (selectEl) {
      selectEl.addEventListener('change', async (e) => {
        const naam = e.target?.value || selectEl.value;
        if (naam) {
          this.__geselecteerdProject = naam;
          await this.#laadBezwaren(naam);
        }
      });
    }

    if (verwerkKnop) {
      verwerkKnop.addEventListener('click', async () => {
        if (this.__geselecteerdProject && !this.__bezig) {
          await this.#verwerkBezwaren(this.__geselecteerdProject);
        }
      });
    }
  }

  async #laadBezwaren(projectNaam) {
    this.#verbergFout();
    try {
      const response = await fetch(
          `/api/v1/projects/${encodeURIComponent(projectNaam)}/bezwaren`);
      if (!response.ok) throw new Error('Ophalen bezwaren mislukt');
      const data = await response.json();
      this.__bezwaren = data.bezwaren;
      this.#werkTabelBij();
      this.#werkKnopBij();
    } catch (e) {
      this.#toonFout('Bezwaren konden niet worden geladen.');
    }
  }

  async #verwerkBezwaren(projectNaam) {
    this.#verbergFout();
    this.#zetBezig(true);
    try {
      const response = await fetch(
          `/api/v1/projects/${encodeURIComponent(projectNaam)}/verwerk`,
          {method: 'POST'});
      if (!response.ok) throw new Error('Verwerking mislukt');
      const data = await response.json();
      this.__bezwaren = data.bezwaren;
      this.#werkTabelBij();
    } catch (e) {
      this.#toonFout('Verwerking kon niet worden gestart.');
    } finally {
      this.#zetBezig(false);
    }
  }

  #werkTabelBij() {
    const tabel = this.shadowRoot?.querySelector('#bezwaren-tabel');
    if (tabel) {
      tabel.bezwaren = this.__bezwaren;
      tabel.hidden = false;
    }
  }

  #werkKnopBij() {
    const knop = this.shadowRoot?.querySelector('#verwerk-knop');
    if (knop) {
      const heeftTodoItems = this.__bezwaren.some(b => b.status === 'todo');
      knop.disabled = !heeftTodoItems;
    }
  }

  #zetBezig(bezig) {
    this.__bezig = bezig;
    const knop = this.shadowRoot?.querySelector('#verwerk-knop');
    if (knop) knop.disabled = bezig;
  }

  #toonFout(bericht) {
    const foutEl = this.shadowRoot?.querySelector('#fout-melding');
    if (foutEl) {
      foutEl.textContent = bericht;
      foutEl.hidden = false;
    }
  }

  #verbergFout() {
    const foutEl = this.shadowRoot?.querySelector('#fout-melding');
    if (foutEl) foutEl.hidden = true;
  }
}

defineWebComponent(BezwaarschriftenProjectSelectie, 'bezwaarschriften-project-selectie');
```

**Step 2: Verify build**

```bash
cd webapp && npm run build 2>&1 | tail -20
```

**Step 3: Commit**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "feat(DECIBEL-1706): add bezwaarschriften-project-selectie component"
```

---

## Task 9: Update landingspagina + index.js

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-landingspagina.js`
- Modify: `webapp/src/js/index.js`

**Step 1: Update index.js**

Add imports before the existing import:

```js
import './bezwaarschriften-bezwaren-tabel.js';
import './bezwaarschriften-project-selectie.js';
import './bezwaarschriften-landingspagina';
```

**Step 2: Update bezwaarschriften-landingspagina.js**

Voeg `bezwaarschriften-project-selectie` toe na de introductietekst. Vervang de inhoud van de sectie:

```html
<vl-typography>
  <h1 class="vl-title vl-title--h1" style="margin-bottom: 3rem">Bezwaarschriften</h1>
  <p class="vl-introduction">
    Welkom op de toepassing Bezwaarschriften. Hier kan u bezwaarschriften automatisch laten verwerken.
  </p>
</vl-typography>
<bezwaarschriften-project-selectie></bezwaarschriften-project-selectie>
```

**Step 3: Verify build**

```bash
cd webapp && npm run build 2>&1 | tail -20
```

**Step 4: Run all backend tests**

```bash
cd app && mvn test 2>&1 | tail -20
```
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add webapp/src/js/index.js webapp/src/js/bezwaarschriften-landingspagina.js
git commit -m "feat(DECIBEL-1706): integrate project-selectie in landingspagina"
```

---

## Task 10: Full build verification

**Step 1: Run full Maven build from root**

```bash
mvn clean package -DskipTests 2>&1 | tail -30
```
Expected: BUILD SUCCESS

**Step 2: Run all tests**

```bash
mvn test 2>&1 | tail -20
```
Expected: BUILD SUCCESS, all tests pass

**Step 3: Update workflow checklist in spec**

Mark step 4 as done in `specs/DECIBEL-1706.md`:
```
- [x] 4. Implementatie-Agent (TDD)
```

---

## Notes for Executor

- **Checkstyle**: Run `mvn checkstyle:check` after each task if there are compile errors. The Google style guide requires 2-space indent in Java files.
- **@MockitoBean**: Spring Boot 3.4 uses `@MockitoBean` instead of the deprecated `@MockBean`.
- **Path injection**: The `BestandssysteemProjectAdapter` constructor receives `@Value("${bezwaarschriften.input.folder}") Path inputFolder`. In tests, use the `@TempDir` and inject directly via constructor.
- **Frontend tests**: No automated test framework configured. Manual testing via `npm start` or `npm run build`.
- **Security**: The `/api/v1/**` permit is intentional for this internal tool. Document in technical-debt.md if required.
