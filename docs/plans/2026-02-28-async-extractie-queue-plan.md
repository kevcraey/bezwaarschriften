# Async Extractie Queue — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Vervang synchrone per-bestand extractie door een async queue met database-backed state, WebSocket live-updates, configureerbare concurrency en timer-display.

**Architecture:** Database als queue (ExtractieTaak entity). @Scheduled worker pikt taken op en voert ze uit op een thread pool. WebSocket pusht statuswijzigingen real-time naar de frontend. MockExtractieVerwerker simuleert LLM-doorlooptijd met configureerbare delay.

**Tech Stack:** Spring Boot 2.7 (javax.persistence), Spring Data JPA, Spring WebSocket (native, geen STOMP), Liquibase, PostgreSQL, H2 (tests), Mockito 5.14.2

**Design document:** `docs/plans/2026-02-28-async-extractie-queue-design.md`

---

## Task 1: ExtractieTaakStatus enum + Liquibase migration

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakStatus.java`
- Create: `app/src/main/resources/config/liquibase/changelog/20260228-extractie-taak.xml`
- Modify: `app/src/main/resources/config/liquibase/master.xml`

**Step 1: Create ExtractieTaakStatus enum**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Status van een extractie-taak in de verwerkingsqueue.
 */
public enum ExtractieTaakStatus {
  WACHTEND,
  BEZIG,
  KLAAR,
  FOUT
}
```

**Step 2: Create Liquibase changeset**

Create directory `app/src/main/resources/config/liquibase/changelog/` and file `20260228-extractie-taak.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">

  <changeSet id="20260228-1" author="kenzo">
    <createTable tableName="extractie_taak">
      <column name="id" type="bigserial" autoIncrement="true">
        <constraints primaryKey="true" nullable="false"/>
      </column>
      <column name="project_naam" type="varchar(255)">
        <constraints nullable="false"/>
      </column>
      <column name="bestandsnaam" type="varchar(255)">
        <constraints nullable="false"/>
      </column>
      <column name="status" type="varchar(50)">
        <constraints nullable="false"/>
      </column>
      <column name="aantal_pogingen" type="int" defaultValueNumeric="0">
        <constraints nullable="false"/>
      </column>
      <column name="max_pogingen" type="int" defaultValueNumeric="3">
        <constraints nullable="false"/>
      </column>
      <column name="aantal_woorden" type="int"/>
      <column name="aantal_bezwaren" type="int"/>
      <column name="foutmelding" type="text"/>
      <column name="aangemaakt_op" type="timestamp with time zone">
        <constraints nullable="false"/>
      </column>
      <column name="verwerking_gestart_op" type="timestamp with time zone"/>
      <column name="afgerond_op" type="timestamp with time zone"/>
      <column name="versie" type="int" defaultValueNumeric="0">
        <constraints nullable="false"/>
      </column>
    </createTable>
  </changeSet>

</databaseChangeLog>
```

**Step 3: Update master.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">

  <include file="config/liquibase/changelog/20260228-extractie-taak.xml"/>

</databaseChangeLog>
```

**Step 4: Commit**

```bash
git add -A && git commit -m "feat: ExtractieTaakStatus enum + Liquibase migration voor extractie_taak tabel"
```

---

## Task 2: ExtractieTaak JPA entity + repository

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaak.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakRepository.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakRepositoryTest.java`

**Step 1: Write the failing test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class ExtractieTaakRepositoryTest {

  @Autowired
  private ExtractieTaakRepository repository;

  @Test
  void slaatTaakOpEnVindtTerugOpId() {
    var taak = new ExtractieTaak();
    taak.setProjectNaam("windmolens");
    taak.setBestandsnaam("bezwaar-001.txt");
    taak.setStatus(ExtractieTaakStatus.WACHTEND);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    taak.setAangemaaktOp(Instant.now());

    var opgeslagen = repository.save(taak);
    var gevonden = repository.findById(opgeslagen.getId());

    assertThat(gevonden).isPresent();
    assertThat(gevonden.get().getProjectNaam()).isEqualTo("windmolens");
    assertThat(gevonden.get().getStatus()).isEqualTo(ExtractieTaakStatus.WACHTEND);
  }

  @Test
  void vindtTakenOpProjectNaam() {
    slaOp("windmolens", "a.txt", ExtractieTaakStatus.WACHTEND);
    slaOp("windmolens", "b.txt", ExtractieTaakStatus.BEZIG);
    slaOp("zonnepanelen", "c.txt", ExtractieTaakStatus.WACHTEND);

    var taken = repository.findByProjectNaam("windmolens");

    assertThat(taken).hasSize(2);
  }

  @Test
  void teltTakenOpStatus() {
    slaOp("p1", "a.txt", ExtractieTaakStatus.BEZIG);
    slaOp("p1", "b.txt", ExtractieTaakStatus.BEZIG);
    slaOp("p1", "c.txt", ExtractieTaakStatus.WACHTEND);

    assertThat(repository.countByStatus(ExtractieTaakStatus.BEZIG)).isEqualTo(2);
    assertThat(repository.countByStatus(ExtractieTaakStatus.WACHTEND)).isEqualTo(1);
  }

  @Test
  void vindtWachtendeTakenGesorteerdOpAangemaaktOp() {
    var nu = Instant.now();
    slaOp("p1", "nieuw.txt", ExtractieTaakStatus.WACHTEND, nu);
    slaOp("p1", "oud.txt", ExtractieTaakStatus.WACHTEND, nu.minusSeconds(60));
    slaOp("p1", "bezig.txt", ExtractieTaakStatus.BEZIG, nu.minusSeconds(120));

    var taken = repository.findByStatusOrderByAangemaaktOpAsc(ExtractieTaakStatus.WACHTEND);

    assertThat(taken).hasSize(2);
    assertThat(taken.get(0).getBestandsnaam()).isEqualTo("oud.txt");
    assertThat(taken.get(1).getBestandsnaam()).isEqualTo("nieuw.txt");
  }

  @Test
  void vindtLaatsteTaakVoorBestand() {
    var nu = Instant.now();
    slaOp("windmolens", "a.txt", ExtractieTaakStatus.FOUT, nu.minusSeconds(60));
    slaOp("windmolens", "a.txt", ExtractieTaakStatus.WACHTEND, nu);

    var laatste = repository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc("windmolens", "a.txt");

    assertThat(laatste).isPresent();
    assertThat(laatste.get().getStatus()).isEqualTo(ExtractieTaakStatus.WACHTEND);
  }

  private void slaOp(String project, String bestand, ExtractieTaakStatus status) {
    slaOp(project, bestand, status, Instant.now());
  }

  private void slaOp(String project, String bestand, ExtractieTaakStatus status, Instant moment) {
    var taak = new ExtractieTaak();
    taak.setProjectNaam(project);
    taak.setBestandsnaam(bestand);
    taak.setStatus(status);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    taak.setAangemaaktOp(moment);
    repository.save(taak);
  }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl app test -Dtest=ExtractieTaakRepositoryTest`
Expected: FAIL — class ExtractieTaak/ExtractieTaakRepository not found

**Step 3: Create ExtractieTaak entity**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * JPA entity voor een extractie-taak in de verwerkingsqueue.
 */
@Entity
@Table(name = "extractie_taak")
public class ExtractieTaak {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "project_naam", nullable = false)
  private String projectNaam;

  @Column(name = "bestandsnaam", nullable = false)
  private String bestandsnaam;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ExtractieTaakStatus status;

  @Column(name = "aantal_pogingen", nullable = false)
  private int aantalPogingen;

  @Column(name = "max_pogingen", nullable = false)
  private int maxPogingen;

  @Column(name = "aantal_woorden")
  private Integer aantalWoorden;

  @Column(name = "aantal_bezwaren")
  private Integer aantalBezwaren;

  @Column(name = "foutmelding", columnDefinition = "text")
  private String foutmelding;

  @Column(name = "aangemaakt_op", nullable = false)
  private Instant aangemaaktOp;

  @Column(name = "verwerking_gestart_op")
  private Instant verwerkingGestartOp;

  @Column(name = "afgerond_op")
  private Instant afgerondOp;

  @Version
  @Column(name = "versie", nullable = false)
  private int versie;

  // Getters en setters

  public Long getId() { return id; }

  public void setId(Long id) { this.id = id; }

  public String getProjectNaam() { return projectNaam; }

  public void setProjectNaam(String projectNaam) { this.projectNaam = projectNaam; }

  public String getBestandsnaam() { return bestandsnaam; }

  public void setBestandsnaam(String bestandsnaam) { this.bestandsnaam = bestandsnaam; }

  public ExtractieTaakStatus getStatus() { return status; }

  public void setStatus(ExtractieTaakStatus status) { this.status = status; }

  public int getAantalPogingen() { return aantalPogingen; }

  public void setAantalPogingen(int aantalPogingen) { this.aantalPogingen = aantalPogingen; }

  public int getMaxPogingen() { return maxPogingen; }

  public void setMaxPogingen(int maxPogingen) { this.maxPogingen = maxPogingen; }

  public Integer getAantalWoorden() { return aantalWoorden; }

  public void setAantalWoorden(Integer aantalWoorden) { this.aantalWoorden = aantalWoorden; }

  public Integer getAantalBezwaren() { return aantalBezwaren; }

  public void setAantalBezwaren(Integer aantalBezwaren) { this.aantalBezwaren = aantalBezwaren; }

  public String getFoutmelding() { return foutmelding; }

  public void setFoutmelding(String foutmelding) { this.foutmelding = foutmelding; }

  public Instant getAangemaaktOp() { return aangemaaktOp; }

  public void setAangemaaktOp(Instant aangemaaktOp) { this.aangemaaktOp = aangemaaktOp; }

  public Instant getVerwerkingGestartOp() { return verwerkingGestartOp; }

  public void setVerwerkingGestartOp(Instant verwerkingGestartOp) {
    this.verwerkingGestartOp = verwerkingGestartOp;
  }

  public Instant getAfgerondOp() { return afgerondOp; }

  public void setAfgerondOp(Instant afgerondOp) { this.afgerondOp = afgerondOp; }

  public int getVersie() { return versie; }

  public void setVersie(int versie) { this.versie = versie; }
}
```

**Step 4: Create ExtractieTaakRepository**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository voor ExtractieTaak entities.
 */
public interface ExtractieTaakRepository extends JpaRepository<ExtractieTaak, Long> {

  List<ExtractieTaak> findByProjectNaam(String projectNaam);

  long countByStatus(ExtractieTaakStatus status);

  List<ExtractieTaak> findByStatusOrderByAangemaaktOpAsc(ExtractieTaakStatus status);

  Optional<ExtractieTaak> findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
      String projectNaam, String bestandsnaam);
}
```

**Step 5: Run test to verify it passes**

Run: `mvn -pl app test -Dtest=ExtractieTaakRepositoryTest`
Expected: PASS — all 5 tests green

**Step 6: Commit**

```bash
git add -A && git commit -m "feat: ExtractieTaak entity + repository met custom queries"
```

---

## Task 3: ExtractieVerwerker interface + MockExtractieVerwerker

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieResultaat.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieVerwerker.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/MockExtractieVerwerker.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/MockExtractieVerwerkerTest.java`

**Step 1: Write the failing test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.Brondocument;
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockExtractieVerwerkerTest {

  @Mock
  private ProjectPoort projectPoort;

  @Mock
  private IngestiePoort ingestiePoort;

  private MockExtractieVerwerker verwerker;

  @BeforeEach
  void setUp() {
    verwerker = new MockExtractieVerwerker(projectPoort, ingestiePoort, "input", 0, 0);
  }

  @Test
  void eersteBestandGeeft3Bezwaren() throws Exception {
    when(projectPoort.geefBestandsnamen("wind"))
        .thenReturn(List.of("a.txt", "b.txt", "c.txt"));
    when(ingestiePoort.leesBestand(Path.of("input", "wind", "bezwaren", "a.txt")))
        .thenReturn(new Brondocument("drie woorden hier", "a.txt",
            "input/wind/bezwaren/a.txt", Instant.now()));

    var resultaat = verwerker.verwerk("wind", "a.txt", 0);

    assertThat(resultaat.aantalBezwaren()).isEqualTo(3);
    assertThat(resultaat.aantalWoorden()).isEqualTo(3);
  }

  @Test
  void derdeBestandGeeft5Bezwaren() throws Exception {
    when(projectPoort.geefBestandsnamen("wind"))
        .thenReturn(List.of("a.txt", "b.txt", "c.txt"));
    when(ingestiePoort.leesBestand(Path.of("input", "wind", "bezwaren", "c.txt")))
        .thenReturn(new Brondocument("tekst", "c.txt",
            "input/wind/bezwaren/c.txt", Instant.now()));

    var resultaat = verwerker.verwerk("wind", "c.txt", 0);

    assertThat(resultaat.aantalBezwaren()).isEqualTo(5);
  }

  @Test
  void tweedeBestandFaaltBijEerstePoging() throws Exception {
    when(projectPoort.geefBestandsnamen("wind"))
        .thenReturn(List.of("a.txt", "b.txt", "c.txt"));
    when(ingestiePoort.leesBestand(Path.of("input", "wind", "bezwaren", "b.txt")))
        .thenReturn(new Brondocument("tekst", "b.txt",
            "input/wind/bezwaren/b.txt", Instant.now()));

    assertThatThrownBy(() -> verwerker.verwerk("wind", "b.txt", 0))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void tweedeBestandSlaagdBijTweedePoging() throws Exception {
    when(projectPoort.geefBestandsnamen("wind"))
        .thenReturn(List.of("a.txt", "b.txt", "c.txt"));
    when(ingestiePoort.leesBestand(Path.of("input", "wind", "bezwaren", "b.txt")))
        .thenReturn(new Brondocument("tekst", "b.txt",
            "input/wind/bezwaren/b.txt", Instant.now()));

    var resultaat = verwerker.verwerk("wind", "b.txt", 1);

    assertThat(resultaat.aantalBezwaren()).isEqualTo(4);
  }

  @Test
  void overigeBestandenGeven2Bezwaren() throws Exception {
    when(projectPoort.geefBestandsnamen("wind"))
        .thenReturn(List.of("a.txt", "b.txt", "c.txt", "d.txt"));
    when(ingestiePoort.leesBestand(Path.of("input", "wind", "bezwaren", "d.txt")))
        .thenReturn(new Brondocument("tekst", "d.txt",
            "input/wind/bezwaren/d.txt", Instant.now()));

    var resultaat = verwerker.verwerk("wind", "d.txt", 0);

    assertThat(resultaat.aantalBezwaren()).isEqualTo(2);
  }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl app test -Dtest=MockExtractieVerwerkerTest`
Expected: FAIL — classes not found

**Step 3: Create ExtractieResultaat record**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Resultaat van een extractie-verwerking.
 */
public record ExtractieResultaat(int aantalWoorden, int aantalBezwaren) {}
```

**Step 4: Create ExtractieVerwerker interface**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Port voor het uitvoeren van bezwaar-extractie op een bestand.
 */
public interface ExtractieVerwerker {

  /**
   * Verwerkt een bestand en extraheert bezwaren.
   *
   * @param projectNaam Naam van het project
   * @param bestandsnaam Naam van het bestand
   * @param poging Huidige pogingnummer (0-based)
   * @return Extractieresultaat met aantallen
   * @throws RuntimeException bij verwerkingsfout
   */
  ExtractieResultaat verwerk(String projectNaam, String bestandsnaam, int poging);
}
```

**Step 5: Create MockExtractieVerwerker**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mock-implementatie van ExtractieVerwerker die LLM-doorlooptijd simuleert.
 */
@Component
public class MockExtractieVerwerker implements ExtractieVerwerker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ProjectPoort projectPoort;
  private final IngestiePoort ingestiePoort;
  private final Path inputFolder;
  private final int minDelaySeconden;
  private final int maxDelaySeconden;
  private final Random random = new Random();

  /**
   * Maakt een nieuwe MockExtractieVerwerker aan.
   */
  public MockExtractieVerwerker(
      ProjectPoort projectPoort,
      IngestiePoort ingestiePoort,
      @Value("${bezwaarschriften.input.folder}") String inputFolderString,
      @Value("${bezwaarschriften.extractie.mock.min-delay-seconden:5}") int minDelaySeconden,
      @Value("${bezwaarschriften.extractie.mock.max-delay-seconden:30}") int maxDelaySeconden) {
    this.projectPoort = projectPoort;
    this.ingestiePoort = ingestiePoort;
    this.inputFolder = Path.of(inputFolderString);
    this.minDelaySeconden = minDelaySeconden;
    this.maxDelaySeconden = maxDelaySeconden;
  }

  @Override
  public ExtractieResultaat verwerk(String projectNaam, String bestandsnaam, int poging) {
    var bestandsPad = inputFolder.resolve(projectNaam).resolve("bezwaren").resolve(bestandsnaam);
    var brondocument = ingestiePoort.leesBestand(bestandsPad);
    var aantalWoorden = telWoorden(brondocument.tekst());

    simuleerDelay();

    var txtBestanden = projectPoort.geefBestandsnamen(projectNaam).stream()
        .filter(n -> n.toLowerCase().endsWith(".txt"))
        .toList();
    int index = txtBestanden.indexOf(bestandsnaam);

    var aantalBezwaren = bepaalAantalBezwaren(index, poging, bestandsnaam);

    LOGGER.info("Mock extractie '{}': {} woorden, {} bezwaren (poging {})",
        bestandsnaam, aantalWoorden, aantalBezwaren, poging);

    return new ExtractieResultaat(aantalWoorden, aantalBezwaren);
  }

  private int bepaalAantalBezwaren(int index, int poging, String bestandsnaam) {
    return switch (index) {
      case 0 -> 3;
      case 1 -> {
        if (poging == 0) {
          throw new RuntimeException(
              "Mock fout bij eerste poging voor '%s'".formatted(bestandsnaam));
        }
        yield 4;
      }
      case 2 -> 5;
      default -> 2;
    };
  }

  private void simuleerDelay() {
    if (maxDelaySeconden <= 0) {
      return;
    }
    try {
      int delayMs = (minDelaySeconden + random.nextInt(maxDelaySeconden - minDelaySeconden + 1))
          * 1000;
      Thread.sleep(delayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private int telWoorden(String tekst) {
    if (tekst == null || tekst.isBlank()) {
      return 0;
    }
    return tekst.strip().split("\\s+").length;
  }
}
```

**Step 6: Run test to verify it passes**

Run: `mvn -pl app test -Dtest=MockExtractieVerwerkerTest`
Expected: PASS — all 5 tests green

**Step 7: Commit**

```bash
git add -A && git commit -m "feat: ExtractieVerwerker interface + MockExtractieVerwerker met configureerbare delay"
```

---

## Task 4: ExtractieTaakDto + ExtractieNotificatie + ExtractieTaakService

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakDto.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieNotificatie.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java`

**Step 1: Create ExtractieTaakDto (shared DTO voor REST + WebSocket)**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * DTO voor een extractie-taak, gebruikt in REST responses en WebSocket berichten.
 */
public record ExtractieTaakDto(
    Long id,
    String projectNaam,
    String bestandsnaam,
    String status,
    int aantalPogingen,
    String aangemaaktOp,
    String verwerkingGestartOp,
    Integer aantalWoorden,
    Integer aantalBezwaren,
    String foutmelding) {

  /**
   * Converteert een entity naar DTO.
   */
  static ExtractieTaakDto van(ExtractieTaak taak) {
    return new ExtractieTaakDto(
        taak.getId(),
        taak.getProjectNaam(),
        taak.getBestandsnaam(),
        taak.getStatus().name().toLowerCase(),
        taak.getAantalPogingen(),
        taak.getAangemaaktOp().toString(),
        taak.getVerwerkingGestartOp() != null
            ? taak.getVerwerkingGestartOp().toString() : null,
        taak.getAantalWoorden(),
        taak.getAantalBezwaren(),
        taak.getFoutmelding());
  }
}
```

**Step 2: Create ExtractieNotificatie interface**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Port voor het notificeren van clients over taak-statuswijzigingen.
 */
public interface ExtractieNotificatie {

  /**
   * Notificeert alle verbonden clients over een taakwijziging.
   */
  void taakGewijzigd(ExtractieTaakDto taak);
}
```

**Step 3: Write failing tests for ExtractieTaakService**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExtractieTaakServiceTest {

  @Mock
  private ExtractieTaakRepository repository;

  @Mock
  private ExtractieNotificatie notificatie;

  private ExtractieTaakService service;

  @BeforeEach
  void setUp() {
    service = new ExtractieTaakService(repository, notificatie, 3);
  }

  @Test
  void dienTakenInMetStatusWachtend() {
    when(repository.save(any(ExtractieTaak.class))).thenAnswer(invocation -> {
      var taak = invocation.getArgument(0, ExtractieTaak.class);
      taak.setId(1L);
      return taak;
    });

    var taken = service.indienen("windmolens", List.of("a.txt", "b.txt"));

    assertThat(taken).hasSize(2);
    var captor = ArgumentCaptor.forClass(ExtractieTaak.class);
    verify(repository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues()).allSatisfy(taak -> {
      assertThat(taak.getStatus()).isEqualTo(ExtractieTaakStatus.WACHTEND);
      assertThat(taak.getProjectNaam()).isEqualTo("windmolens");
      assertThat(taak.getAantalPogingen()).isZero();
      assertThat(taak.getAangemaaktOp()).isNotNull();
    });
    verify(notificatie, times(2)).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void geefTakenVoorProject() {
    var taak = maakTaak(1L, "windmolens", "a.txt", ExtractieTaakStatus.WACHTEND);
    when(repository.findByProjectNaam("windmolens")).thenReturn(List.of(taak));

    var taken = service.geefTaken("windmolens");

    assertThat(taken).hasSize(1);
    assertThat(taken.get(0).bestandsnaam()).isEqualTo("a.txt");
  }

  @Test
  void pakOpVoorVerwerkingZetStatusOpBezig() {
    var taak = maakTaak(1L, "p1", "a.txt", ExtractieTaakStatus.WACHTEND);
    when(repository.countByStatus(ExtractieTaakStatus.BEZIG)).thenReturn(0L);
    when(repository.findByStatusOrderByAangemaaktOpAsc(ExtractieTaakStatus.WACHTEND))
        .thenReturn(List.of(taak));
    when(repository.save(any(ExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    var opgepakt = service.pakOpVoorVerwerking();

    assertThat(opgepakt).hasSize(1);
    assertThat(opgepakt.get(0).getStatus()).isEqualTo(ExtractieTaakStatus.BEZIG);
    assertThat(opgepakt.get(0).getVerwerkingGestartOp()).isNotNull();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void pakOpRespecteerMaxConcurrent() {
    when(repository.countByStatus(ExtractieTaakStatus.BEZIG)).thenReturn(3L);

    var opgepakt = service.pakOpVoorVerwerking();

    assertThat(opgepakt).isEmpty();
  }

  @Test
  void markeerKlaarZetResultaat() {
    var taak = maakTaak(1L, "p1", "a.txt", ExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(ExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.markeerKlaar(1L, 100, 3);

    assertThat(taak.getStatus()).isEqualTo(ExtractieTaakStatus.KLAAR);
    assertThat(taak.getAantalWoorden()).isEqualTo(100);
    assertThat(taak.getAantalBezwaren()).isEqualTo(3);
    assertThat(taak.getAfgerondOp()).isNotNull();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void markeerFoutMetRetryZetTerugNaarWachtend() {
    var taak = maakTaak(1L, "p1", "a.txt", ExtractieTaakStatus.BEZIG);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(ExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.markeerFout(1L, "timeout");

    assertThat(taak.getStatus()).isEqualTo(ExtractieTaakStatus.WACHTEND);
    assertThat(taak.getAantalPogingen()).isEqualTo(1);
    assertThat(taak.getVerwerkingGestartOp()).isNull();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void markeerFoutDefinitiefBijMaxPogingen() {
    var taak = maakTaak(1L, "p1", "a.txt", ExtractieTaakStatus.BEZIG);
    taak.setAantalPogingen(2);
    taak.setMaxPogingen(3);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(ExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.markeerFout(1L, "timeout");

    assertThat(taak.getStatus()).isEqualTo(ExtractieTaakStatus.FOUT);
    assertThat(taak.getAantalPogingen()).isEqualTo(3);
    assertThat(taak.getFoutmelding()).isEqualTo("timeout");
    assertThat(taak.getAfgerondOp()).isNotNull();
  }

  private ExtractieTaak maakTaak(Long id, String project, String bestand,
      ExtractieTaakStatus status) {
    var taak = new ExtractieTaak();
    taak.setId(id);
    taak.setProjectNaam(project);
    taak.setBestandsnaam(bestand);
    taak.setStatus(status);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    taak.setAangemaaktOp(Instant.now());
    return taak;
  }
}
```

**Step 4: Run test to verify it fails**

Run: `mvn -pl app test -Dtest=ExtractieTaakServiceTest`
Expected: FAIL — ExtractieTaakService not found

**Step 5: Create ExtractieTaakService**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service voor het beheren van extractie-taken in de queue.
 */
@Service
public class ExtractieTaakService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ExtractieTaakRepository repository;
  private final ExtractieNotificatie notificatie;
  private final int maxConcurrent;

  /**
   * Maakt een nieuwe ExtractieTaakService aan.
   */
  public ExtractieTaakService(
      ExtractieTaakRepository repository,
      ExtractieNotificatie notificatie,
      @Value("${bezwaarschriften.extractie.max-concurrent:3}") int maxConcurrent) {
    this.repository = repository;
    this.notificatie = notificatie;
    this.maxConcurrent = maxConcurrent;
  }

  /**
   * Dient extractie-taken in voor de gegeven bestanden.
   *
   * @return Lijst van aangemaakte taken als DTOs
   */
  @Transactional
  public List<ExtractieTaakDto> indienen(String projectNaam, List<String> bestandsnamen) {
    return bestandsnamen.stream()
        .map(bestandsnaam -> {
          var taak = new ExtractieTaak();
          taak.setProjectNaam(projectNaam);
          taak.setBestandsnaam(bestandsnaam);
          taak.setStatus(ExtractieTaakStatus.WACHTEND);
          taak.setAantalPogingen(0);
          taak.setMaxPogingen(3);
          taak.setAangemaaktOp(Instant.now());
          var opgeslagen = repository.save(taak);
          var dto = ExtractieTaakDto.van(opgeslagen);
          notificatie.taakGewijzigd(dto);
          LOGGER.info("Taak ingediend: {} / {}", projectNaam, bestandsnaam);
          return dto;
        })
        .toList();
  }

  /**
   * Geeft alle taken voor een project als DTOs.
   */
  public List<ExtractieTaakDto> geefTaken(String projectNaam) {
    return repository.findByProjectNaam(projectNaam).stream()
        .map(ExtractieTaakDto::van)
        .toList();
  }

  /**
   * Pikt wachtende taken op voor verwerking, respecteert max-concurrent limiet.
   *
   * @return Lijst van opgepakte taken (status gewijzigd naar BEZIG)
   */
  @Transactional
  public List<ExtractieTaak> pakOpVoorVerwerking() {
    long bezigCount = repository.countByStatus(ExtractieTaakStatus.BEZIG);
    long beschikbaar = maxConcurrent - bezigCount;
    if (beschikbaar <= 0) {
      return List.of();
    }

    var wachtend = repository.findByStatusOrderByAangemaaktOpAsc(ExtractieTaakStatus.WACHTEND);
    var opTePakken = wachtend.stream().limit(beschikbaar).toList();

    for (var taak : opTePakken) {
      taak.setStatus(ExtractieTaakStatus.BEZIG);
      taak.setVerwerkingGestartOp(Instant.now());
      repository.save(taak);
      notificatie.taakGewijzigd(ExtractieTaakDto.van(taak));
      LOGGER.info("Taak opgepakt: {} / {} (poging {})",
          taak.getProjectNaam(), taak.getBestandsnaam(), taak.getAantalPogingen());
    }

    return opTePakken;
  }

  /**
   * Markeert een taak als succesvol afgerond.
   */
  @Transactional
  public void markeerKlaar(Long taakId, int aantalWoorden, int aantalBezwaren) {
    var taak = repository.findById(taakId).orElseThrow();
    taak.setStatus(ExtractieTaakStatus.KLAAR);
    taak.setAantalWoorden(aantalWoorden);
    taak.setAantalBezwaren(aantalBezwaren);
    taak.setAfgerondOp(Instant.now());
    repository.save(taak);
    notificatie.taakGewijzigd(ExtractieTaakDto.van(taak));
    LOGGER.info("Taak klaar: {} / {} ({} woorden, {} bezwaren)",
        taak.getProjectNaam(), taak.getBestandsnaam(), aantalWoorden, aantalBezwaren);
  }

  /**
   * Markeert een taak als mislukt. Zet terug naar WACHTEND als retries beschikbaar.
   */
  @Transactional
  public void markeerFout(Long taakId, String foutmelding) {
    var taak = repository.findById(taakId).orElseThrow();
    taak.setAantalPogingen(taak.getAantalPogingen() + 1);

    if (taak.getAantalPogingen() < taak.getMaxPogingen()) {
      taak.setStatus(ExtractieTaakStatus.WACHTEND);
      taak.setVerwerkingGestartOp(null);
      LOGGER.info("Taak terug in wachtrij: {} / {} (poging {}/{})",
          taak.getProjectNaam(), taak.getBestandsnaam(),
          taak.getAantalPogingen(), taak.getMaxPogingen());
    } else {
      taak.setStatus(ExtractieTaakStatus.FOUT);
      taak.setFoutmelding(foutmelding);
      taak.setAfgerondOp(Instant.now());
      LOGGER.warn("Taak definitief mislukt: {} / {} na {} pogingen",
          taak.getProjectNaam(), taak.getBestandsnaam(), taak.getAantalPogingen());
    }

    repository.save(taak);
    notificatie.taakGewijzigd(ExtractieTaakDto.van(taak));
  }

  /**
   * Geeft de meest recente taak voor een bestand (voor status-afleiding).
   */
  public ExtractieTaak geefLaatsteTaak(String projectNaam, String bestandsnaam) {
    return repository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(projectNaam, bestandsnaam)
        .orElse(null);
  }
}
```

**Step 6: Run test to verify it passes**

Run: `mvn -pl app test -Dtest=ExtractieTaakServiceTest`
Expected: PASS — all 7 tests green

**Step 7: Commit**

```bash
git add -A && git commit -m "feat: ExtractieTaakService met queue-operaties en notificatie"
```

---

## Task 5: ExtractieWorker + ExtractieConfig

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieWorker.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/ExtractieConfig.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieWorkerTest.java`

**Step 1: Write the failing test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ExtendWith(MockitoExtension.class)
class ExtractieWorkerTest {

  @Mock
  private ExtractieTaakService service;

  @Mock
  private ExtractieVerwerker verwerker;

  private ExtractieWorker worker;

  @BeforeEach
  void setUp() {
    // Gebruik een synchrone executor voor testen
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.initialize();
    worker = new ExtractieWorker(service, verwerker, executor);
  }

  @Test
  void paktTakenOpEnVoertUit() throws Exception {
    var taak = maakTaak(1L, "wind", "a.txt", 0);
    when(service.pakOpVoorVerwerking()).thenReturn(List.of(taak));
    when(verwerker.verwerk("wind", "a.txt", 0))
        .thenReturn(new ExtractieResultaat(100, 3));

    worker.verwerkTaken();
    Thread.sleep(200); // wacht op thread pool

    verify(service).markeerKlaar(1L, 100, 3);
  }

  @Test
  void markeertFoutBijException() throws Exception {
    var taak = maakTaak(1L, "wind", "a.txt", 0);
    when(service.pakOpVoorVerwerking()).thenReturn(List.of(taak));
    when(verwerker.verwerk("wind", "a.txt", 0))
        .thenThrow(new RuntimeException("Mock fout"));

    worker.verwerkTaken();
    Thread.sleep(200);

    verify(service).markeerFout(1L, "Mock fout");
  }

  @Test
  void doetNietsAlsGeenTakenBeschikbaar() {
    when(service.pakOpVoorVerwerking()).thenReturn(List.of());

    worker.verwerkTaken();

    verify(verwerker, never()).verwerk(anyString(), anyString(), anyInt());
    verify(service, never()).markeerKlaar(anyLong(), anyInt(), anyInt());
  }

  private ExtractieTaak maakTaak(Long id, String project, String bestand, int pogingen) {
    var taak = new ExtractieTaak();
    taak.setId(id);
    taak.setProjectNaam(project);
    taak.setBestandsnaam(bestand);
    taak.setStatus(ExtractieTaakStatus.BEZIG);
    taak.setAantalPogingen(pogingen);
    taak.setMaxPogingen(3);
    taak.setAangemaaktOp(Instant.now());
    taak.setVerwerkingGestartOp(Instant.now());
    return taak;
  }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl app test -Dtest=ExtractieWorkerTest`
Expected: FAIL — ExtractieWorker not found

**Step 3: Create ExtractieConfig**

```java
package be.vlaanderen.omgeving.bezwaarschriften.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuratie voor async extractie: scheduling + thread pool.
 */
@Configuration
@EnableScheduling
public class ExtractieConfig {

  @Bean
  public ThreadPoolTaskExecutor extractieExecutor(
      @Value("${bezwaarschriften.extractie.max-concurrent:3}") int maxConcurrent) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(maxConcurrent);
    executor.setMaxPoolSize(maxConcurrent);
    executor.setThreadNamePrefix("extractie-");
    executor.initialize();
    return executor;
  }
}
```

**Step 4: Create ExtractieWorker**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Scheduled worker die extractie-taken uit de queue oppikt en uitvoert.
 */
@Component
public class ExtractieWorker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ExtractieTaakService service;
  private final ExtractieVerwerker verwerker;
  private final ThreadPoolTaskExecutor executor;

  /**
   * Maakt een nieuwe ExtractieWorker aan.
   */
  public ExtractieWorker(
      ExtractieTaakService service,
      ExtractieVerwerker verwerker,
      ThreadPoolTaskExecutor executor) {
    this.service = service;
    this.verwerker = verwerker;
    this.executor = executor;
  }

  /**
   * Pollt elke seconde voor beschikbare taken en voert ze uit op de thread pool.
   */
  @Scheduled(fixedDelay = 1000)
  public void verwerkTaken() {
    var taken = service.pakOpVoorVerwerking();
    for (var taak : taken) {
      executor.submit(() -> verwerkTaak(taak));
    }
  }

  private void verwerkTaak(ExtractieTaak taak) {
    try {
      var resultaat = verwerker.verwerk(
          taak.getProjectNaam(), taak.getBestandsnaam(), taak.getAantalPogingen());
      service.markeerKlaar(taak.getId(), resultaat.aantalWoorden(), resultaat.aantalBezwaren());
    } catch (Exception e) {
      LOGGER.warn("Extractie mislukt voor taak {}: {}", taak.getId(), e.getMessage());
      service.markeerFout(taak.getId(), e.getMessage());
    }
  }
}
```

**Step 5: Run test to verify it passes**

Run: `mvn -pl app test -Dtest=ExtractieWorkerTest`
Expected: PASS — all 3 tests green

**Step 6: Commit**

```bash
git add -A && git commit -m "feat: ExtractieWorker met scheduled polling en thread pool"
```

---

## Task 6: WebSocket infrastructure

**Files:**
- Modify: `app/pom.xml` (add websocket dependency)
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieWebSocketHandler.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/WebSocketConfig.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/SecurityConfiguration.java`
- Modify: `webapp/webpack.config.js`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieWebSocketHandlerTest.java`

**Step 1: Add spring-boot-starter-websocket to pom.xml**

Add after the `spring-boot-starter-webflux` dependency in `app/pom.xml`:

```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-websocket</artifactId>
		</dependency>
```

**Step 2: Write the failing test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class ExtractieWebSocketHandlerTest {

  private ExtractieWebSocketHandler handler;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    handler = new ExtractieWebSocketHandler(objectMapper);
  }

  @Test
  void broadcastNaarVerbondenSessies() throws Exception {
    var sessie = mock(WebSocketSession.class);
    when(sessie.isOpen()).thenReturn(true);
    handler.afterConnectionEstablished(sessie);

    var dto = new ExtractieTaakDto(1L, "wind", "a.txt", "bezig", 0,
        "2026-02-28T14:30:00Z", "2026-02-28T14:30:23Z", null, null, null);
    handler.taakGewijzigd(dto);

    var captor = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
    org.mockito.Mockito.verify(sessie).sendMessage(captor.capture());
    var json = captor.getValue().getPayload();
    var node = objectMapper.readTree(json);
    assertThat(node.get("type").asText()).isEqualTo("taak-update");
    assertThat(node.get("taak").get("bestandsnaam").asText()).isEqualTo("a.txt");
    assertThat(node.get("taak").get("status").asText()).isEqualTo("bezig");
  }

  @Test
  void broadcastNietNaarGeslotenSessies() throws Exception {
    var sessie = mock(WebSocketSession.class);
    when(sessie.isOpen()).thenReturn(true);
    handler.afterConnectionEstablished(sessie);
    handler.afterConnectionClosed(sessie, CloseStatus.NORMAL);

    var dto = new ExtractieTaakDto(1L, "wind", "a.txt", "klaar", 0,
        "2026-02-28T14:30:00Z", null, 100, 3, null);
    handler.taakGewijzigd(dto);

    org.mockito.Mockito.verify(sessie, org.mockito.Mockito.never())
        .sendMessage(org.mockito.ArgumentMatchers.any());
  }
}
```

**Step 3: Run test to verify it fails**

Run: `mvn -pl app test -Dtest=ExtractieWebSocketHandlerTest`
Expected: FAIL — class not found

**Step 4: Create ExtractieWebSocketHandler**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket handler die taak-statuswijzigingen broadcast naar alle verbonden clients.
 */
@Component
public class ExtractieWebSocketHandler extends TextWebSocketHandler
    implements ExtractieNotificatie {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
  private final ObjectMapper objectMapper;

  public ExtractieWebSocketHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.add(session);
    LOGGER.info("WebSocket verbinding geopend: {}", session.getId());
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session);
    LOGGER.info("WebSocket verbinding gesloten: {}", session.getId());
  }

  @Override
  public void taakGewijzigd(ExtractieTaakDto taak) {
    try {
      var json = objectMapper.writeValueAsString(Map.of("type", "taak-update", "taak", taak));
      var message = new TextMessage(json);
      for (var session : sessions) {
        if (session.isOpen()) {
          try {
            session.sendMessage(message);
          } catch (Exception e) {
            LOGGER.warn("Kon bericht niet sturen naar sessie {}: {}",
                session.getId(), e.getMessage());
          }
        }
      }
    } catch (Exception e) {
      LOGGER.warn("WebSocket broadcast mislukt: {}", e.getMessage());
    }
  }
}
```

**Step 5: Create WebSocketConfig**

```java
package be.vlaanderen.omgeving.bezwaarschriften.config;

import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registreert WebSocket endpoints.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final ExtractieWebSocketHandler handler;

  public WebSocketConfig(ExtractieWebSocketHandler handler) {
    this.handler = handler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws/extracties").setAllowedOrigins("*");
  }
}
```

**Step 6: Update SecurityConfiguration — add /ws/** to permitAll**

In `SecurityConfiguration.java`, update the antMatchers line:

```java
.antMatchers("/admin/health/**", "/admin/info", "/api/v1/**", "/ws/**").permitAll()
```

**Step 7: Update webpack.config.js — add WebSocket proxy**

Add `/ws` proxy to the devServer config, next to the existing `/api` proxy:

```javascript
'/ws': {
  target: 'http://localhost:8080',
  ws: true,
},
```

**Step 8: Run test to verify it passes**

Run: `mvn -pl app test -Dtest=ExtractieWebSocketHandlerTest`
Expected: PASS — all 2 tests green

**Step 9: Commit**

```bash
git add -A && git commit -m "feat: WebSocket infrastructure voor real-time extractie-updates"
```

---

## Task 7: BezwaarBestandStatus uitbreiden + ProjectService refactor

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestandStatus.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java`

**Step 1: Voeg WACHTEND en BEZIG toe aan BezwaarBestandStatus**

```java
public enum BezwaarBestandStatus {
  TODO,
  WACHTEND,
  BEZIG,
  EXTRACTIE_KLAAR,
  FOUT,
  NIET_ONDERSTEUND
}
```

**Step 2: Refactor ProjectService.geefBezwaren() — leid status af uit database**

De methode `geefBezwaren()` moet nu de `ExtractieTaakRepository` raadplegen voor de actuele status. Verwijder het in-memory `statusRegister` en `tweedeBestandToggle`.

Nieuwe `ProjectService`:

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.lang.invoke.MethodHandles;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service die projecten en bezwaarbestanden orkestreert.
 */
@Service
public class ProjectService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ProjectPoort projectPoort;
  private final ExtractieTaakRepository extractieTaakRepository;

  public ProjectService(ProjectPoort projectPoort,
      ExtractieTaakRepository extractieTaakRepository) {
    this.projectPoort = projectPoort;
    this.extractieTaakRepository = extractieTaakRepository;
  }

  public List<String> geefProjecten() {
    return projectPoort.geefProjecten();
  }

  /**
   * Geeft bezwaarbestanden van een project met status afgeleid uit de extractie-taak database.
   */
  public List<BezwaarBestand> geefBezwaren(String projectNaam) {
    var bestandsnamen = projectPoort.geefBestandsnamen(projectNaam);
    return bestandsnamen.stream()
        .map(naam -> {
          if (!isTxtBestand(naam)) {
            return new BezwaarBestand(naam, BezwaarBestandStatus.NIET_ONDERSTEUND);
          }
          var laatsteTaak = extractieTaakRepository
              .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(projectNaam, naam)
              .orElse(null);
          if (laatsteTaak == null) {
            return new BezwaarBestand(naam, BezwaarBestandStatus.TODO);
          }
          return new BezwaarBestand(naam,
              vanExtractieTaakStatus(laatsteTaak.getStatus()),
              laatsteTaak.getAantalWoorden(),
              laatsteTaak.getAantalBezwaren());
        })
        .toList();
  }

  private boolean isTxtBestand(String bestandsnaam) {
    return bestandsnaam.toLowerCase().endsWith(".txt");
  }

  private BezwaarBestandStatus vanExtractieTaakStatus(ExtractieTaakStatus status) {
    return switch (status) {
      case WACHTEND -> BezwaarBestandStatus.WACHTEND;
      case BEZIG -> BezwaarBestandStatus.BEZIG;
      case KLAAR -> BezwaarBestandStatus.EXTRACTIE_KLAAR;
      case FOUT -> BezwaarBestandStatus.FOUT;
    };
  }
}
```

**Step 3: Update statusNaarString in ProjectController — voeg nieuwe statussen toe**

```java
  private static String statusNaarString(BezwaarBestandStatus status) {
    return switch (status) {
      case TODO -> "todo";
      case WACHTEND -> "wachtend";
      case BEZIG -> "bezig";
      case EXTRACTIE_KLAAR -> "extractie-klaar";
      case FOUT -> "fout";
      case NIET_ONDERSTEUND -> "niet ondersteund";
    };
  }
```

**Step 4: Verwijder `verwerk()` en `extraheer()` endpoints uit ProjectController**

Verwijder de methodes `verwerk()` (lijn 58-62) en `extraheer()` (lijn 74-81) uit `ProjectController.java`.

**Step 5: Update ProjectServiceTest**

Herschrijf de tests. De service heeft nu `ProjectPoort` en `ExtractieTaakRepository` als dependencies (niet meer `IngestiePoort` en `@Value`). Verwijder alle tests die `verwerk()`, `extraheer()`, en het in-memory statusregister testen.

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
  private ExtractieTaakRepository extractieTaakRepository;

  private ProjectService service;

  @BeforeEach
  void setUp() {
    service = new ProjectService(projectPoort, extractieTaakRepository);
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
    when(extractieTaakRepository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.empty());

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
  void leidtStatusAfUitExtractieTaak() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.txt"));
    var taak = new ExtractieTaak();
    taak.setStatus(ExtractieTaakStatus.KLAAR);
    taak.setAantalWoorden(150);
    taak.setAantalBezwaren(3);
    when(extractieTaakRepository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(taak));

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren.get(0).status()).isEqualTo(BezwaarBestandStatus.EXTRACTIE_KLAAR);
    assertThat(bezwaren.get(0).aantalWoorden()).isEqualTo(150);
    assertThat(bezwaren.get(0).aantalBezwaren()).isEqualTo(3);
  }

  @Test
  void leidtWachtendStatusAfUitExtractieTaak() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.txt"));
    var taak = new ExtractieTaak();
    taak.setStatus(ExtractieTaakStatus.WACHTEND);
    when(extractieTaakRepository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(taak));

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren.get(0).status()).isEqualTo(BezwaarBestandStatus.WACHTEND);
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

**Step 6: Update ProjectControllerTest — verwijder verwerk/extraheer tests**

Verwijder de tests: `starktBatchverwerkingEnGeeftStatusTerug`, `geeft404VoorOnbekendProjectBijVerwerk`, `extraheertBezwarenVoorEnkelBestand`, `geeft404VoorOnbekendProjectBijExtraheer`.

Houd: `geeftProjectenTerug`, `geeftLegeProjectenLijstTerug`, `geeftBezwarenTerugVoorProject`, `geeft404VoorOnbekendProject`.

Update `geeftBezwarenTerugVoorProject` om de nieuwe statussen te bevatten (voeg `"wachtend"` en `"bezig"` toe als geldige status-waarden).

**Step 7: Run all tests**

Run: `mvn -pl app test`
Expected: PASS — alle tests groen

**Step 8: Commit**

```bash
git add -A && git commit -m "refactor: ProjectService leidt status af uit database, verwijder sync endpoints"
```

---

## Task 8: ExtractieController

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieController.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieControllerTest.java`

**Step 1: Write failing tests**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExtractieController.class)
@WithMockUser
class ExtractieControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private ExtractieTaakService extractieTaakService;

  @Test
  void dientExtractieTakenIn() throws Exception {
    when(extractieTaakService.indienen(eq("windmolens"), eq(List.of("a.txt", "b.txt"))))
        .thenReturn(List.of(
            new ExtractieTaakDto(1L, "windmolens", "a.txt", "wachtend", 0,
                "2026-02-28T14:30:00Z", null, null, null, null),
            new ExtractieTaakDto(2L, "windmolens", "b.txt", "wachtend", 0,
                "2026-02-28T14:30:00Z", null, null, null, null)
        ));

    mockMvc.perform(post("/api/v1/projects/windmolens/extracties")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"bestandsnamen\":[\"a.txt\",\"b.txt\"]}")
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taken[0].bestandsnaam").value("a.txt"))
        .andExpect(jsonPath("$.taken[0].status").value("wachtend"))
        .andExpect(jsonPath("$.taken[1].bestandsnaam").value("b.txt"));
  }

  @Test
  void geeftExtractieTakenVoorProject() throws Exception {
    when(extractieTaakService.geefTaken("windmolens"))
        .thenReturn(List.of(
            new ExtractieTaakDto(1L, "windmolens", "a.txt", "klaar", 1,
                "2026-02-28T14:30:00Z", "2026-02-28T14:30:10Z", 150, 3, null)
        ));

    mockMvc.perform(get("/api/v1/projects/windmolens/extracties"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taken[0].bestandsnaam").value("a.txt"))
        .andExpect(jsonPath("$.taken[0].status").value("klaar"))
        .andExpect(jsonPath("$.taken[0].aantalWoorden").value(150))
        .andExpect(jsonPath("$.taken[0].aantalBezwaren").value(3));
  }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl app test -Dtest=ExtractieControllerTest`
Expected: FAIL — ExtractieController not found

**Step 3: Create ExtractieController**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller voor extractie-taken.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ExtractieController {

  private final ExtractieTaakService extractieTaakService;

  public ExtractieController(ExtractieTaakService extractieTaakService) {
    this.extractieTaakService = extractieTaakService;
  }

  /**
   * Dient extractie-taken in voor de opgegeven bestanden.
   */
  @PostMapping("/{naam}/extracties")
  public ResponseEntity<ExtractieTakenResponse> indienen(
      @PathVariable String naam, @RequestBody ExtractiesRequest request) {
    var taken = extractieTaakService.indienen(naam, request.bestandsnamen());
    return ResponseEntity.ok(new ExtractieTakenResponse(taken));
  }

  /**
   * Geeft alle extractie-taken voor een project.
   */
  @GetMapping("/{naam}/extracties")
  public ResponseEntity<ExtractieTakenResponse> geefTaken(@PathVariable String naam) {
    var taken = extractieTaakService.geefTaken(naam);
    return ResponseEntity.ok(new ExtractieTakenResponse(taken));
  }

  /** Request DTO voor het indienen van extracties. */
  record ExtractiesRequest(List<String> bestandsnamen) {}

  /** Response DTO voor extractie-taken. */
  record ExtractieTakenResponse(List<ExtractieTaakDto> taken) {}
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -pl app test -Dtest=ExtractieControllerTest`
Expected: PASS — all 2 tests green

**Step 5: Run all tests**

Run: `mvn -pl app test`
Expected: PASS — alle tests groen

**Step 6: Commit**

```bash
git add -A && git commit -m "feat: ExtractieController met POST/GET /extracties endpoints"
```

---

## Task 9: Frontend — WebSocket, timers, extractie flow

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js`
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

**Step 1: Update bezwaarschriften-bezwaren-tabel.js**

Voeg timer-support, nieuwe status-labels, en taak-data toe:

```javascript
import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlTableComponent} from '@domg-wc/components/block/table/vl-table.component.js';
import {vlGlobalStyles} from '@domg-wc/styles';

registerWebComponents([VlTableComponent]);

const STATUS_LABELS = {
  'todo': 'Te verwerken',
  'wachtend': 'Wachtend',
  'bezig': 'Bezig',
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
              <th><input type="checkbox" id="selecteer-alles" title="Selecteer alles"></th>
              <th>Bestandsnaam</th>
              <th>Aantal bezwaren</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody id="tabel-body"></tbody>
        </table>
      </vl-table>
    `);
    this.__bezwaren = [];
    this.__takenData = {}; // bestandsnaam -> { aangemaaktOp, verwerkingGestartOp }
    this.__timerInterval = null;
  }

  set bezwaren(waarde) {
    this.__bezwaren = waarde || [];
    this._renderRijen();
  }

  get bezwaren() {
    return this.__bezwaren;
  }

  /**
   * Update een enkel bestand met taak-data (vanuit WebSocket of REST).
   */
  werkBijMetTaakUpdate(taak) {
    this.__takenData[taak.bestandsnaam] = {
      aangemaaktOp: taak.aangemaaktOp,
      verwerkingGestartOp: taak.verwerkingGestartOp,
    };
    this.__bezwaren = this.__bezwaren.map((b) =>
      b.bestandsnaam === taak.bestandsnaam ? {
        bestandsnaam: taak.bestandsnaam,
        status: taak.status,
        aantalWoorden: taak.aantalWoorden,
        aantalBezwaren: taak.aantalBezwaren,
      } : b,
    );
    this._renderRijen();
  }

  geefGeselecteerdeBestandsnamen() {
    const checkboxes = this.shadowRoot.querySelectorAll('.rij-checkbox:checked');
    return Array.from(checkboxes).map((cb) => cb.dataset.bestandsnaam);
  }

  connectedCallback() {
    super.connectedCallback();
    this._renderRijen();

    const selecteerAlles = this.shadowRoot.querySelector('#selecteer-alles');
    if (selecteerAlles) {
      selecteerAlles.addEventListener('change', (e) => {
        const checked = e.target.checked;
        this.shadowRoot.querySelectorAll('.rij-checkbox:not([disabled])').forEach((cb) => {
          cb.checked = checked;
        });
        this._dispatchSelectieGewijzigd();
      });
    }
  }

  disconnectedCallback() {
    this._stopTimer();
  }

  _renderRijen() {
    const tbody = this.shadowRoot && this.shadowRoot.querySelector('#tabel-body');
    if (!tbody) return;

    const selecteerAlles = this.shadowRoot.querySelector('#selecteer-alles');
    if (selecteerAlles) selecteerAlles.checked = false;

    if (this.__bezwaren.length === 0) {
      tbody.innerHTML = '<tr><td colspan="4">Geen bestanden gevonden</td></tr>';
      this._dispatchSelectieGewijzigd();
      this._stopTimer();
      return;
    }

    tbody.innerHTML = this.__bezwaren
        .map((b) => {
          const disabled = this._isDisabled(b.status) ? 'disabled' : '';
          const aantalBezwaren = b.aantalBezwaren != null ? b.aantalBezwaren : '';
          return `<tr>
            <td><input type="checkbox" class="rij-checkbox" data-bestandsnaam="${this._escapeHtml(b.bestandsnaam)}" ${disabled}></td>
            <td>${this._escapeHtml(b.bestandsnaam)}</td>
            <td>${aantalBezwaren}</td>
            <td class="status-cel" data-bestandsnaam="${this._escapeHtml(b.bestandsnaam)}">${this._formatStatus(b)}</td>
          </tr>`;
        })
        .join('');

    tbody.querySelectorAll('.rij-checkbox').forEach((cb) => {
      cb.addEventListener('change', () => this._dispatchSelectieGewijzigd());
    });

    this._dispatchSelectieGewijzigd();
    this._beheerTimer();
  }

  _isDisabled(status) {
    return status === 'niet ondersteund' || status === 'wachtend' || status === 'bezig';
  }

  _beheerTimer() {
    const heeftActief = this.__bezwaren.some(
        (b) => b.status === 'wachtend' || b.status === 'bezig',
    );
    if (heeftActief && !this.__timerInterval) {
      this.__timerInterval = setInterval(() => this._updateTimers(), 1000);
    } else if (!heeftActief && this.__timerInterval) {
      this._stopTimer();
    }
  }

  _stopTimer() {
    if (this.__timerInterval) {
      clearInterval(this.__timerInterval);
      this.__timerInterval = null;
    }
  }

  _updateTimers() {
    const nu = Date.now();
    this.__bezwaren.forEach((b) => {
      if (b.status !== 'wachtend' && b.status !== 'bezig') return;
      const cel = this.shadowRoot.querySelector(
          `.status-cel[data-bestandsnaam="${CSS.escape(b.bestandsnaam)}"]`,
      );
      if (!cel) return;
      cel.textContent = this._formatStatus(b, nu);
    });
  }

  _formatStatus(b, nu) {
    nu = nu || Date.now();
    const taakData = this.__takenData[b.bestandsnaam];

    if (b.status === 'wachtend' && taakData && taakData.aangemaaktOp) {
      const wachtMs = nu - new Date(taakData.aangemaaktOp).getTime();
      return `Wachtend (${this._formatTijd(wachtMs)})`;
    }

    if (b.status === 'bezig' && taakData) {
      const wachtMs = taakData.verwerkingGestartOp && taakData.aangemaaktOp
        ? new Date(taakData.verwerkingGestartOp).getTime() -
            new Date(taakData.aangemaaktOp).getTime()
        : 0;
      const verwerkMs = taakData.verwerkingGestartOp
        ? nu - new Date(taakData.verwerkingGestartOp).getTime()
        : 0;
      return `Bezig (${this._formatTijd(wachtMs)} + ${this._formatTijd(verwerkMs)})`;
    }

    const label = STATUS_LABELS[b.status] || this._escapeHtml(b.status);
    if (b.status === 'extractie-klaar' && b.aantalWoorden != null) {
      return `${label} (${b.aantalWoorden} woorden)`;
    }
    return label;
  }

  _formatTijd(ms) {
    const totaalSeconden = Math.floor(ms / 1000);
    const minuten = Math.floor(totaalSeconden / 60);
    const seconden = totaalSeconden % 60;
    return `${minuten}:${String(seconden).padStart(2, '0')}`;
  }

  _dispatchSelectieGewijzigd() {
    const geselecteerd = this.geefGeselecteerdeBestandsnamen();
    this.dispatchEvent(new CustomEvent('selectie-gewijzigd', {
      detail: {geselecteerd},
      bubbles: true,
      composed: true,
    }));
  }

  _escapeHtml(str) {
    const div = document.createElement('div');
    div.appendChild(document.createTextNode(str));
    return div.innerHTML;
  }
}

defineWebComponent(BezwaarschriftenBezwarenTabel, 'bezwaarschriften-bezwaren-tabel');
```

**Step 2: Update bezwaarschriften-project-selectie.js**

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
      </div>
      <div id="bezwaren-sectie" hidden>
        <h2>Bezwaarschriften</h2>
        <vl-button id="extraheer-knop" disabled>Extraheer geselecteerde</vl-button>
        <p id="fout-melding" hidden></p>
        <bezwaarschriften-bezwaren-tabel id="bezwaren-tabel"></bezwaarschriften-bezwaren-tabel>
      </div>
    `);
    this.__projecten = [];
    this.__geselecteerdProject = null;
    this.__bezwaren = [];
    this.__bezig = false;
    this.__fout = null;
    this._ws = null;
    this._wsReconnectDelay = 1000;
  }

  connectedCallback() {
    super.connectedCallback();
    this._laadProjecten();
    this._koppelEventListeners();
    this._verbindWebSocket();
  }

  disconnectedCallback() {
    if (this._ws) {
      this._ws.close();
      this._ws = null;
    }
  }

  _verbindWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${protocol}//${window.location.host}/ws/extracties`;
    this._ws = new WebSocket(url);

    this._ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      if (data.type === 'taak-update') {
        this._verwerkTaakUpdate(data.taak);
      }
    };

    this._ws.onclose = () => {
      setTimeout(() => {
        this._wsReconnectDelay = Math.min(this._wsReconnectDelay * 2, 30000);
        this._verbindWebSocket();
        if (this.__geselecteerdProject) {
          this._syncExtracties(this.__geselecteerdProject);
        }
      }, this._wsReconnectDelay);
    };

    this._ws.onopen = () => {
      this._wsReconnectDelay = 1000;
    };
  }

  _verwerkTaakUpdate(taak) {
    if (!this.__geselecteerdProject || taak.projectNaam !== this.__geselecteerdProject) {
      return;
    }
    const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
    if (tabel) {
      tabel.werkBijMetTaakUpdate(taak);
    }
  }

  _syncExtracties(projectNaam) {
    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/extracties`)
        .then((response) => response.json())
        .then((data) => {
          const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
          if (tabel && data.taken) {
            data.taken.forEach((taak) => tabel.werkBijMetTaakUpdate(taak));
          }
        })
        .catch(() => { /* stille fout bij sync */ });
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
    const extraheerKnop = this.shadowRoot && this.shadowRoot.querySelector('#extraheer-knop');

    if (selectEl) {
      selectEl.addEventListener('vl-change', (e) => {
        this._verbergFout();
        const naam = e.detail.value;
        this.__geselecteerdProject = naam || null;
        if (naam) {
          this._laadBezwaren(naam);
        } else {
          this.__bezwaren = [];
          this._verbergBezwarenSectie();
        }
      });
    }

    this.shadowRoot.addEventListener('selectie-gewijzigd', (e) => {
      if (extraheerKnop) {
        extraheerKnop.disabled = this.__bezig || e.detail.geselecteerd.length === 0;
      }
    });

    if (extraheerKnop) {
      extraheerKnop.addEventListener('vl-click', () => {
        if (this.__bezig || !this.__geselecteerdProject) return;
        const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
        if (!tabel) return;
        const geselecteerd = tabel.geefGeselecteerdeBestandsnamen();
        if (geselecteerd.length === 0) return;
        this._dienExtractiesIn(this.__geselecteerdProject, geselecteerd);
      });
    }
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
          this._syncExtracties(projectNaam);
        })
        .catch(() => {
          this._toonFout('Bezwaren konden niet worden geladen.');
        });
  }

  _dienExtractiesIn(projectNaam, bestandsnamen) {
    this._verbergFout();
    this._zetBezig(true);

    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/extracties`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({bestandsnamen}),
    })
        .then((response) => {
          if (!response.ok) throw new Error('Indienen extracties mislukt');
          return response.json();
        })
        .then((data) => {
          const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
          if (tabel && data.taken) {
            data.taken.forEach((taak) => tabel.werkBijMetTaakUpdate(taak));
          }
        })
        .catch(() => {
          this._toonFout('Extracties konden niet worden ingediend.');
        })
        .finally(() => {
          this._zetBezig(false);
        });
  }

  _werkTabelBij() {
    const sectie = this.shadowRoot && this.shadowRoot.querySelector('#bezwaren-sectie');
    const tabel = this.shadowRoot && this.shadowRoot.querySelector('#bezwaren-tabel');
    if (tabel) {
      tabel.bezwaren = this.__bezwaren;
    }
    if (sectie) {
      sectie.hidden = false;
    }
  }

  _verbergBezwarenSectie() {
    const sectie = this.shadowRoot && this.shadowRoot.querySelector('#bezwaren-sectie');
    if (sectie) sectie.hidden = true;
  }

  _zetBezig(bezig) {
    this.__bezig = bezig;
    const extraheerKnop = this.shadowRoot && this.shadowRoot.querySelector('#extraheer-knop');
    if (extraheerKnop) extraheerKnop.disabled = bezig;
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

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: frontend WebSocket verbinding, live timers en async extractie flow"
```

---

## Task 10: End-to-end verificatie

**Step 1: Run alle backend tests**

```bash
mvn -pl app test
```

Expected: PASS — alle tests groen

**Step 2: Build frontend**

```bash
cd webapp && npm run build
```

**Step 3: Start database**

```bash
docker compose up -d
```

**Step 4: Start applicatie**

```bash
mvn -pl app spring-boot:run -Dspring-boot.run.profiles=dev
```

**Step 5: Open browser en valideer**

1. Ga naar `http://localhost:3000` (webpack devserver) of `http://localhost:8080`
2. Selecteer een project uit de dropdown
3. De tabel verschijnt met bestanden (status: "Te verwerken")
4. Selecteer meerdere bestanden via checkboxes
5. Klik "Extraheer geselecteerde"
6. Observeer:
   - Status wisselt naar "Wachtend (0:XX)" met oplopende timer
   - Na 1s pollt de worker en pikt taken op (max 3 tegelijk)
   - Status wisselt naar "Bezig (0:XX + 0:XX)" met twee timers
   - Na random 5-30s verschijnt "Extractie klaar (N woorden)"
   - Het 2e bestand faalt bij de eerste poging, gaat terug naar "Wachtend", en slaagt bij de 2e poging
   - Checkboxes zijn disabled zolang bestanden WACHTEND of BEZIG zijn
7. Ververs de pagina — status blijft behouden (database-backed)

**Step 6: Commit finale state**

```bash
git add -A && git commit -m "feat: async extractie queue met WebSocket, timers en mock verwerker"
```

---

## Configuratie samenvatting

Voeg toe aan `application-dev.yml`:

```yaml
bezwaarschriften:
  extractie:
    max-concurrent: 3
    max-pogingen: 3
    mock:
      min-delay-seconden: 5
      max-delay-seconden: 30
```
