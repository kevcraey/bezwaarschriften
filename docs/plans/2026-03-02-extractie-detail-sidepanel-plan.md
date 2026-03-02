# Extractie-detail side-panel Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Persist extraction details (passages + bezwaren) and show them in a side-panel when clicking a search icon on a document row.

**Architecture:** Two new database tables (`extractie_passage`, `geextraheerd_bezwaar`) with FK to `extractie_taak`. A new REST endpoint returns the details per bestandsnaam. The frontend adds a search button to the action column and a `vl-side-sheet` to the bezwaren-tabel component, following the existing kernbezwaren side-panel pattern.

**Tech Stack:** Java 21, Spring Boot, JPA/Hibernate, Liquibase, @domg-wc web components, vanilla JS

---

### Task 1: Liquibase migration voor nieuwe tabellen

**Files:**
- Create: `app/src/main/resources/config/liquibase/changelog/20260302-extractie-details.xml`
- Modify: `app/src/main/resources/config/liquibase/master.xml:10` (add include)

**Step 1: Schrijf de Liquibase changelog**

Maak `app/src/main/resources/config/liquibase/changelog/20260302-extractie-details.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">

  <changeSet id="20260302-2" author="kenzo">
    <createTable tableName="extractie_passage">
      <column name="id" type="bigserial" autoIncrement="true">
        <constraints primaryKey="true" nullable="false"/>
      </column>
      <column name="taak_id" type="bigint">
        <constraints nullable="false"/>
      </column>
      <column name="passage_nr" type="int">
        <constraints nullable="false"/>
      </column>
      <column name="tekst" type="text">
        <constraints nullable="false"/>
      </column>
    </createTable>

    <addForeignKeyConstraint
        baseTableName="extractie_passage"
        baseColumnNames="taak_id"
        constraintName="fk_extractie_passage_taak"
        referencedTableName="extractie_taak"
        referencedColumnNames="id"
        onDelete="CASCADE"/>

    <createTable tableName="geextraheerd_bezwaar">
      <column name="id" type="bigserial" autoIncrement="true">
        <constraints primaryKey="true" nullable="false"/>
      </column>
      <column name="taak_id" type="bigint">
        <constraints nullable="false"/>
      </column>
      <column name="passage_nr" type="int">
        <constraints nullable="false"/>
      </column>
      <column name="samenvatting" type="text">
        <constraints nullable="false"/>
      </column>
      <column name="categorie" type="varchar(50)">
        <constraints nullable="false"/>
      </column>
    </createTable>

    <addForeignKeyConstraint
        baseTableName="geextraheerd_bezwaar"
        baseColumnNames="taak_id"
        constraintName="fk_geextraheerd_bezwaar_taak"
        referencedTableName="extractie_taak"
        referencedColumnNames="id"
        onDelete="CASCADE"/>
  </changeSet>

</databaseChangeLog>
```

**Step 2: Voeg include toe aan master.xml**

In `app/src/main/resources/config/liquibase/master.xml`, voeg na regel 10 (`consolidatie-taak.xml`) toe:

```xml
  <include file="config/liquibase/changelog/20260302-extractie-details.xml"/>
```

**Step 3: Verifieer dat de applicatie start**

Run: `cd app && mvn spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments="--server.port=0" &` en controleer dat er geen Liquibase-fout optreedt in de logs. Stop de applicatie daarna.

**Step 4: Commit**

```bash
git add app/src/main/resources/config/liquibase/changelog/20260302-extractie-details.xml app/src/main/resources/config/liquibase/master.xml
git commit -m "feat: liquibase migration voor extractie_passage en geextraheerd_bezwaar tabellen"
```

---

### Task 2: JPA entiteiten en repositories

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractiePassageEntiteit.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarEntiteit.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractiePassageRepository.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarRepository.java`

**Step 1: Schrijf ExtractiePassageEntiteit**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "extractie_passage")
public class ExtractiePassageEntiteit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "taak_id", nullable = false)
  private Long taakId;

  @Column(name = "passage_nr", nullable = false)
  private int passageNr;

  @Column(name = "tekst", columnDefinition = "text", nullable = false)
  private String tekst;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getTaakId() { return taakId; }
  public void setTaakId(Long taakId) { this.taakId = taakId; }
  public int getPassageNr() { return passageNr; }
  public void setPassageNr(int passageNr) { this.passageNr = passageNr; }
  public String getTekst() { return tekst; }
  public void setTekst(String tekst) { this.tekst = tekst; }
}
```

**Step 2: Schrijf GeextraheerdBezwaarEntiteit**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "geextraheerd_bezwaar")
public class GeextraheerdBezwaarEntiteit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "taak_id", nullable = false)
  private Long taakId;

  @Column(name = "passage_nr", nullable = false)
  private int passageNr;

  @Column(name = "samenvatting", columnDefinition = "text", nullable = false)
  private String samenvatting;

  @Column(name = "categorie", length = 50, nullable = false)
  private String categorie;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getTaakId() { return taakId; }
  public void setTaakId(Long taakId) { this.taakId = taakId; }
  public int getPassageNr() { return passageNr; }
  public void setPassageNr(int passageNr) { this.passageNr = passageNr; }
  public String getSamenvatting() { return samenvatting; }
  public void setSamenvatting(String samenvatting) { this.samenvatting = samenvatting; }
  public String getCategorie() { return categorie; }
  public void setCategorie(String categorie) { this.categorie = categorie; }
}
```

**Step 3: Schrijf ExtractiePassageRepository**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExtractiePassageRepository
    extends JpaRepository<ExtractiePassageEntiteit, Long> {

  List<ExtractiePassageEntiteit> findByTaakId(Long taakId);
}
```

**Step 4: Schrijf GeextraheerdBezwaarRepository**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GeextraheerdBezwaarRepository
    extends JpaRepository<GeextraheerdBezwaarEntiteit, Long> {

  List<GeextraheerdBezwaarEntiteit> findByTaakId(Long taakId);
}
```

**Step 5: Verifieer compilatie**

Run: `cd app && mvn compile -q`
Expected: BUILD SUCCESS

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractiePassageEntiteit.java \
  app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarEntiteit.java \
  app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractiePassageRepository.java \
  app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarRepository.java
git commit -m "feat: JPA entiteiten en repositories voor extractie-details"
```

---

### Task 3: Persistentie van extractie-details in service en worker

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieWorker.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java`

**Step 1: Schrijf de failing test voor markeerKlaar met ExtractieResultaat**

Voeg in `ExtractieTaakServiceTest.java` twee nieuwe `@Mock` velden toe:

```java
@Mock
private ExtractiePassageRepository passageRepository;

@Mock
private GeextraheerdBezwaarRepository bezwaarRepository;
```

Pas `setUp()` aan:

```java
@BeforeEach
void setUp() {
  service = new ExtractieTaakService(repository, notificatie, projectService,
      passageRepository, bezwaarRepository, 3, 3);
}
```

Voeg een nieuwe test toe:

```java
@Test
void markeerKlaarSlaatPassagesEnBezwarenOp() {
  var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
  when(repository.findById(1L)).thenReturn(Optional.of(taak));
  when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

  var resultaat = new ExtractieResultaat(500, 2,
      List.of(new Passage(1, "Passage een"), new Passage(2, "Passage twee")),
      List.of(
          new GeextraheerdBezwaar(1, "Samenvatting een", "milieu"),
          new GeextraheerdBezwaar(2, "Samenvatting twee", "mobiliteit")),
      "Docsamenvatting");

  service.markeerKlaar(1L, resultaat);

  assertThat(taak.getStatus()).isEqualTo(ExtractieTaakStatus.KLAAR);
  assertThat(taak.getAantalWoorden()).isEqualTo(500);
  assertThat(taak.getAantalBezwaren()).isEqualTo(2);

  var passageCaptor = ArgumentCaptor.forClass(ExtractiePassageEntiteit.class);
  verify(passageRepository, times(2)).save(passageCaptor.capture());
  assertThat(passageCaptor.getAllValues().get(0).getTekst()).isEqualTo("Passage een");
  assertThat(passageCaptor.getAllValues().get(0).getTaakId()).isEqualTo(1L);

  var bezwaarCaptor = ArgumentCaptor.forClass(GeextraheerdBezwaarEntiteit.class);
  verify(bezwaarRepository, times(2)).save(bezwaarCaptor.capture());
  assertThat(bezwaarCaptor.getAllValues().get(0).getSamenvatting()).isEqualTo("Samenvatting een");
  assertThat(bezwaarCaptor.getAllValues().get(0).getTaakId()).isEqualTo(1L);
}
```

**Step 2: Run test om te verifieren dat het faalt**

Run: `cd app && mvn test -pl . -Dtest=ExtractieTaakServiceTest#markeerKlaarSlaatPassagesEnBezwarenOp -q`
Expected: FAIL (constructor signature matcht niet)

**Step 3: Pas ExtractieTaakService aan**

In `ExtractieTaakService.java`:

1. Voeg nieuwe velden en constructor-parameters toe:

```java
private final ExtractiePassageRepository passageRepository;
private final GeextraheerdBezwaarRepository bezwaarRepository;
```

2. Breid constructor uit met deze twee parameters (na `projectService`, voor `maxConcurrent`).

3. Voeg nieuwe `markeerKlaar(Long taakId, ExtractieResultaat resultaat)` methode toe:

```java
@Transactional
public void markeerKlaar(Long taakId, ExtractieResultaat resultaat) {
  var taak = repository.findById(taakId)
      .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
  taak.setStatus(ExtractieTaakStatus.KLAAR);
  taak.setAantalWoorden(resultaat.aantalWoorden());
  taak.setAantalBezwaren(resultaat.aantalBezwaren());
  taak.setAfgerondOp(Instant.now());
  repository.save(taak);

  for (var passage : resultaat.passages()) {
    var entiteit = new ExtractiePassageEntiteit();
    entiteit.setTaakId(taakId);
    entiteit.setPassageNr(passage.id());
    entiteit.setTekst(passage.tekst());
    passageRepository.save(entiteit);
  }

  for (var bezwaar : resultaat.bezwaren()) {
    var entiteit = new GeextraheerdBezwaarEntiteit();
    entiteit.setTaakId(taakId);
    entiteit.setPassageNr(bezwaar.passageId());
    entiteit.setSamenvatting(bezwaar.samenvatting());
    entiteit.setCategorie(bezwaar.categorie());
    bezwaarRepository.save(entiteit);
  }

  notificatie.taakGewijzigd(ExtractieTaakDto.van(taak));
  LOGGER.info("Taak {} afgerond: {} woorden, {} bezwaren, {} passages opgeslagen",
      taakId, resultaat.aantalWoorden(), resultaat.aantalBezwaren(),
      resultaat.passages().size());
}
```

4. Behoud de bestaande `markeerKlaar(Long, int, int)` methode -- die wordt nog gebruikt in bestaande tests. Laat deze delegeren naar de nieuwe:

```java
@Transactional
public void markeerKlaar(Long taakId, int aantalWoorden, int aantalBezwaren) {
  markeerKlaar(taakId, new ExtractieResultaat(aantalWoorden, aantalBezwaren));
}
```

**Step 4: Pas bestaande test setUp() aan**

Alle bestaande tests in `ExtractieTaakServiceTest` die `markeerKlaar(Long, int, int)` aanroepen blijven werken omdat die methode nu delegeert. De setUp moet alleen de nieuwe constructor-parameters krijgen. Voeg `lenient()` toe aan de passage/bezwaar repository mocks als Mockito strict mode klaagt over ongebruikte stubs.

**Step 5: Run alle tests**

Run: `cd app && mvn test -pl . -Dtest=ExtractieTaakServiceTest -q`
Expected: ALL PASS

**Step 6: Pas ExtractieWorker aan**

In `ExtractieWorker.java`, wijzig `verwerkTaak()` methode regel 78 van:

```java
service.markeerKlaar(taak.getId(), resultaat.aantalWoorden(), resultaat.aantalBezwaren());
```

naar:

```java
service.markeerKlaar(taak.getId(), resultaat);
```

**Step 7: Run worker tests**

Run: `cd app && mvn test -pl . -Dtest=ExtractieWorkerTest -q`
Expected: ALL PASS

**Step 8: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java \
  app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieWorker.java \
  app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java
git commit -m "feat: persisteer extractie-details (passages + bezwaren) bij markeerKlaar"
```

---

### Task 4: API endpoint voor extractie-details

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieDetailDto.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieController.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieControllerTest.java`

**Step 1: Schrijf de failing controller test**

Voeg in `ExtractieControllerTest.java` toe:

```java
@MockBean
private ExtractiePassageRepository extractiePassageRepository;

@MockBean
private GeextraheerdBezwaarRepository geextraheerdBezwaarRepository;
```

En de test:

```java
@Test
void geeftExtractieDetailsVoorBestand() throws Exception {
  var detail = new ExtractieDetailDto("bezwaar-001.txt", 2, List.of(
      new ExtractieDetailDto.BezwaarDetail("Geluidshinder door evenementen", "De geluidsoverlast zal..."),
      new ExtractieDetailDto.BezwaarDetail("Parkeertekort", "Er zijn onvoldoende parkeerplaatsen...")));

  when(extractieTaakService.geefExtractieDetails("windmolens", "bezwaar-001.txt"))
      .thenReturn(detail);

  mockMvc.perform(get("/api/v1/projects/windmolens/extracties/bezwaar-001.txt/details"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.bestandsnaam").value("bezwaar-001.txt"))
      .andExpect(jsonPath("$.aantalBezwaren").value(2))
      .andExpect(jsonPath("$.bezwaren[0].samenvatting").value("Geluidshinder door evenementen"))
      .andExpect(jsonPath("$.bezwaren[0].passage").value("De geluidsoverlast zal..."))
      .andExpect(jsonPath("$.bezwaren[1].samenvatting").value("Parkeertekort"));
}

@Test
void geeftExtractieDetails404AlsGeenResultaat() throws Exception {
  when(extractieTaakService.geefExtractieDetails("windmolens", "onbekend.txt"))
      .thenReturn(null);

  mockMvc.perform(get("/api/v1/projects/windmolens/extracties/onbekend.txt/details"))
      .andExpect(status().isNotFound());
}
```

**Step 2: Run test om te verifieren dat het faalt**

Run: `cd app && mvn test -pl . -Dtest=ExtractieControllerTest#geeftExtractieDetailsVoorBestand -q`
Expected: FAIL

**Step 3: Schrijf ExtractieDetailDto**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;

public record ExtractieDetailDto(
    String bestandsnaam,
    int aantalBezwaren,
    List<BezwaarDetail> bezwaren) {

  public record BezwaarDetail(String samenvatting, String passage) {}
}
```

**Step 4: Voeg geefExtractieDetails toe aan ExtractieTaakService**

```java
public ExtractieDetailDto geefExtractieDetails(String projectNaam, String bestandsnaam) {
  var taak = repository
      .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(projectNaam, bestandsnaam)
      .orElse(null);
  if (taak == null || taak.getStatus() != ExtractieTaakStatus.KLAAR) {
    return null;
  }

  var passages = passageRepository.findByTaakId(taak.getId());
  var bezwaren = bezwaarRepository.findByTaakId(taak.getId());

  var passageMap = new java.util.HashMap<Integer, String>();
  for (var p : passages) {
    passageMap.put(p.getPassageNr(), p.getTekst());
  }

  var details = bezwaren.stream()
      .map(b -> new ExtractieDetailDto.BezwaarDetail(
          b.getSamenvatting(),
          passageMap.getOrDefault(b.getPassageNr(), "")))
      .toList();

  return new ExtractieDetailDto(bestandsnaam, details.size(), details);
}
```

Voeg de benodigde import toe: `import java.util.HashMap;`

**Step 5: Voeg endpoint toe aan ExtractieController**

```java
@GetMapping("/{naam}/extracties/{bestandsnaam}/details")
public ResponseEntity<ExtractieDetailDto> geefDetails(
    @PathVariable String naam, @PathVariable String bestandsnaam) {
  var detail = extractieTaakService.geefExtractieDetails(naam, bestandsnaam);
  if (detail == null) {
    return ResponseEntity.notFound().build();
  }
  return ResponseEntity.ok(detail);
}
```

**Step 6: Run alle controller tests**

Run: `cd app && mvn test -pl . -Dtest=ExtractieControllerTest -q`
Expected: ALL PASS

**Step 7: Schrijf unit test voor geefExtractieDetails in service**

Voeg toe aan `ExtractieTaakServiceTest.java`:

```java
@Test
void geefExtractieDetailsJointPassagesMetBezwaren() {
  var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.KLAAR);
  when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
      "windmolens", "bezwaar-001.txt")).thenReturn(Optional.of(taak));

  var passage = new ExtractiePassageEntiteit();
  passage.setTaakId(1L);
  passage.setPassageNr(1);
  passage.setTekst("De geluidsoverlast zal onze nachtrust verstoren.");
  when(passageRepository.findByTaakId(1L)).thenReturn(List.of(passage));

  var bezwaar = new GeextraheerdBezwaarEntiteit();
  bezwaar.setTaakId(1L);
  bezwaar.setPassageNr(1);
  bezwaar.setSamenvatting("Geluidshinder");
  bezwaar.setCategorie("milieu");
  when(bezwaarRepository.findByTaakId(1L)).thenReturn(List.of(bezwaar));

  var result = service.geefExtractieDetails("windmolens", "bezwaar-001.txt");

  assertThat(result).isNotNull();
  assertThat(result.bestandsnaam()).isEqualTo("bezwaar-001.txt");
  assertThat(result.aantalBezwaren()).isEqualTo(1);
  assertThat(result.bezwaren().get(0).samenvatting()).isEqualTo("Geluidshinder");
  assertThat(result.bezwaren().get(0).passage()).isEqualTo("De geluidsoverlast zal onze nachtrust verstoren.");
}

@Test
void geefExtractieDetailsGeeftNullAlsTaakNietKlaar() {
  var taak = maakTaak(1L, "windmolens", "bezig.txt", ExtractieTaakStatus.BEZIG);
  when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
      "windmolens", "bezig.txt")).thenReturn(Optional.of(taak));

  var result = service.geefExtractieDetails("windmolens", "bezig.txt");
  assertThat(result).isNull();
}

@Test
void geefExtractieDetailsGeeftNullAlsTaakNietBestaat() {
  when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
      "windmolens", "onbekend.txt")).thenReturn(Optional.empty());

  var result = service.geefExtractieDetails("windmolens", "onbekend.txt");
  assertThat(result).isNull();
}
```

**Step 8: Run alle service tests**

Run: `cd app && mvn test -pl . -Dtest=ExtractieTaakServiceTest -q`
Expected: ALL PASS

**Step 9: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieDetailDto.java \
  app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java \
  app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieController.java \
  app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieControllerTest.java \
  app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java
git commit -m "feat: GET endpoint voor extractie-details per bestandsnaam"
```

---

### Task 5: Vergrootglas-knop in bezwaren-tabel

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

**Step 1: Voeg de vergrootglas-knop toe aan de actie-kolom renderer**

In `bezwaarschriften-bezwaren-tabel.js`, in de `_configureerRenderers()` methode, vervang de `case 'acties':` renderer (regels 265-281) met:

```javascript
case 'acties':
  veld.renderer = (td, rij) => {
    td.style.verticalAlign = 'middle';
    td.style.whiteSpace = 'nowrap';
    if (rij.status === 'extractie-klaar') {
      const zoekBtn = document.createElement('vl-button');
      zoekBtn.setAttribute('icon', 'search');
      zoekBtn.setAttribute('ghost', '');
      zoekBtn.setAttribute('label', 'Extractie-details bekijken');
      zoekBtn.addEventListener('vl-click', (e) => {
        e.stopPropagation();
        this.dispatchEvent(new CustomEvent('toon-extractie-detail', {
          detail: {bestandsnaam: rij.bestandsnaam},
          bubbles: true, composed: true,
        }));
      });
      td.appendChild(zoekBtn);
    }
    const btn = document.createElement('vl-button');
    btn.setAttribute('icon', 'bin');
    btn.setAttribute('error', '');
    btn.setAttribute('ghost', '');
    btn.setAttribute('label', 'Bestand verwijderen');
    btn.addEventListener('vl-click', (e) => {
      e.stopPropagation();
      this.dispatchEvent(new CustomEvent('verwijder-bezwaar', {
        detail: {bestandsnaam: rij.bestandsnaam},
        bubbles: true, composed: true,
      }));
    });
    td.appendChild(btn);
  };
  break;
```

**Step 2: Verifieer visueel**

Start de app (`cd app && mvn spring-boot:run -Dspring-boot.run.profiles=dev`) en open de browser. Verwerk een document. Controleer dat na extractie een vergrootglas-icoon verschijnt naast de delete-knop.

**Step 3: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "feat: vergrootglas-knop in actie-kolom voor extractie-details"
```

---

### Task 6: Side-panel in bezwaren-tabel

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

**Step 1: Voeg vl-side-sheet import toe**

Voeg bovenaan het bestand toe aan de imports:

```javascript
import {VlSideSheet} from '@domg-wc/components/block/side-sheet/vl-side-sheet.component.js';
```

En voeg `VlSideSheet` toe aan de `registerWebComponents` array.

**Step 2: Voeg CSS toe voor side-sheet**

In de constructor, breid de `<style>` sectie uit. Vervang:

```javascript
super(`
  <style>
    ${vlGlobalStyles}
  </style>
```

Met:

```javascript
super(`
  <style>
    ${vlGlobalStyles}
    :host { display: block; transition: margin-right 0.2s ease; }
    :host(.side-sheet-open) { margin-right: 33.3%; }
    .side-sheet-wrapper {
      display: flex;
      flex-direction: column;
      height: calc(100vh - 43px);
      margin: -1.5rem;
      padding: 0;
      overflow: hidden;
    }
    .side-sheet-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      padding: 1rem 1.5rem;
      border-bottom: 2px solid #e8ebee;
      flex-shrink: 0;
      background: white;
    }
    .side-sheet-titel {
      font-weight: bold;
      flex: 1;
      margin-right: 1rem;
    }
    .side-sheet-sluit-knop {
      background: none;
      border: none;
      font-size: 1.5rem;
      cursor: pointer;
      padding: 0;
      color: #333;
      line-height: 1;
      flex-shrink: 0;
    }
    .side-sheet-sluit-knop:hover { color: #000; }
    .side-sheet-body {
      flex: 1;
      overflow-y: auto;
      padding: 1rem 1.5rem;
    }
    .bezwaar-item {
      margin-bottom: 1.5rem;
      padding-bottom: 1rem;
      border-bottom: 1px solid #e8ebee;
    }
    .bezwaar-item:last-child { border-bottom: none; }
    .bezwaar-samenvatting {
      font-weight: bold;
      margin-bottom: 0.25rem;
    }
    .bezwaar-passage {
      font-style: italic;
      line-height: 1.5;
      color: #687483;
    }
  </style>
```

**Step 3: Voeg side-sheet HTML toe**

In de constructor, voeg na de afsluitende `</vl-rich-data-table>` tag toe:

```html
<vl-side-sheet id="extractie-side-sheet" hide-toggle-button>
  <div class="side-sheet-wrapper">
    <div class="side-sheet-header">
      <div id="extractie-side-sheet-titel" class="side-sheet-titel"></div>
      <button id="extractie-side-sheet-sluit" class="side-sheet-sluit-knop"
          aria-label="Sluiten">&times;</button>
    </div>
    <div id="extractie-side-sheet-inhoud" class="side-sheet-body"></div>
  </div>
</vl-side-sheet>
```

**Step 4: Koppel sluit-knop in connectedCallback**

In `connectedCallback()`, voeg toe na het bestaande tabel event listener blok:

```javascript
const sluitKnop = this.shadowRoot.querySelector('#extractie-side-sheet-sluit');
const sideSheet = this.shadowRoot.querySelector('#extractie-side-sheet');
if (sluitKnop && sideSheet) {
  sluitKnop.addEventListener('click', () => {
    sideSheet.close();
    this.classList.remove('side-sheet-open');
  });
}
```

**Step 5: Voeg methode toe om extractie-details te tonen**

Voeg een publieke methode toe aan de klasse:

```javascript
toonExtractieDetails(projectNaam, bestandsnaam) {
  const sideSheet = this.shadowRoot.querySelector('#extractie-side-sheet');
  const inhoud = this.shadowRoot.querySelector('#extractie-side-sheet-inhoud');
  const titelEl = this.shadowRoot.querySelector('#extractie-side-sheet-titel');
  if (!sideSheet || !inhoud) return;

  inhoud.innerHTML = '<p>Laden...</p>';
  if (titelEl) titelEl.textContent = bestandsnaam;
  sideSheet.open();
  this.classList.add('side-sheet-open');

  fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/extracties/${encodeURIComponent(bestandsnaam)}/details`)
      .then((response) => {
        if (!response.ok) throw new Error('Ophalen details mislukt');
        return response.json();
      })
      .then((data) => {
        inhoud.innerHTML = '';
        if (titelEl) {
          titelEl.textContent = `${data.bestandsnaam} - ${data.aantalBezwaren} bezwar${data.aantalBezwaren === 1 ? '' : 'en'} gevonden`;
        }

        data.bezwaren.forEach((bezwaar) => {
          const item = document.createElement('div');
          item.className = 'bezwaar-item';

          const samenvatting = document.createElement('div');
          samenvatting.className = 'bezwaar-samenvatting';
          samenvatting.textContent = bezwaar.samenvatting;

          const passage = document.createElement('div');
          passage.className = 'bezwaar-passage';
          passage.textContent = `"${bezwaar.passage}"`;

          item.appendChild(samenvatting);
          item.appendChild(passage);
          inhoud.appendChild(item);
        });
      })
      .catch(() => {
        inhoud.innerHTML = '<p>Kon extractie-details niet laden.</p>';
      });
}
```

**Step 6: Verifieer visueel**

Open de app in de browser. Klik op het vergrootglas bij een verwerkt document. Controleer dat het side-panel opent met "Laden...", dan de details toont, en dat de tabel verschuift naar links.

**Step 7: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "feat: side-panel met extractie-details in bezwaren-tabel"
```

---

### Task 7: Event-afhandeling in project-selectie

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js`

**Step 1: Koppel toon-extractie-detail event**

In `_koppelEventListeners()`, voeg toe na het bestaande `herstart-taak` event listener blok:

```javascript
this.shadowRoot.addEventListener('toon-extractie-detail', (e) => {
  const {bestandsnaam} = e.detail;
  const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
  if (tabel && this.__geselecteerdProject) {
    tabel.toonExtractieDetails(this.__geselecteerdProject, bestandsnaam);
  }
});
```

**Step 2: Build frontend**

Run: `cd webapp && npm run build`
Run: `cd .. && mvn process-resources -pl webapp -Denforcer.skip=true`

**Step 3: Verifieer end-to-end**

Start de app, verwerk een document, klik op het vergrootglas. Controleer:
- Side-panel opent met bestandsnaam als titel
- Toont het juiste aantal bezwaren
- Per bezwaar: vetgedrukte samenvatting + italic passage
- Tabel verschuift naar links (margin-right 33.3%)
- Sluit-knop werkt

**Step 4: Commit**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "feat: koppel toon-extractie-detail event aan side-panel"
```

---

### Task 8: Volledige test suite draaien

**Step 1: Run alle backend tests**

Run: `cd app && mvn test -q`
Expected: ALL PASS

**Step 2: Build frontend**

Run: `cd webapp && npm run build`
Expected: No errors

**Step 3: Checkstyle**

Run: `cd app && mvn checkstyle:check -q`
Expected: No violations

**Step 4: Fix eventuele issues**

Als er tests of checkstyle-fouten zijn, fix ze en commit per fix.

**Step 5: Final commit (indien nodig)**

Alle fixes committen.
