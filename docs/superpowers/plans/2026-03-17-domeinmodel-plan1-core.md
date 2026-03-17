# Domeinmodel Cleanup Plan 1: Core — BezwaarDocument + IndividueelBezwaar

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** BezwaarDocument als aggregate root, taken worden efemeer, passages geabsorbeerd in bezwaar.

**Architecture:** Strangler fig — bouw het nieuwe model naast het oude, switch de services over, ruim het oude op. BezwaarDocument vervangt BezwaarBestandEntiteit + TekstExtractieTaak + ExtractieTaak. De twee status-enums op het document vervangen de god-enum. Workers queryen BezwaarDocument rechtstreeks. Na de switch worden oude tabellen verwijderd.

**Tech Stack:** Java 21, Spring Boot 3.x, JPA/Hibernate, Liquibase, Cypress

**Design spec:** `docs/superpowers/specs/2026-03-16-domeinmodel-cleanup-design.md`

---

## Bestandenoverzicht

| Actie | Bestand | Wat |
|-------|---------|-----|
| Create | `app/src/main/resources/config/liquibase/changelog/20260317-domeinmodel-cleanup.xml` | Liquibase migratie |
| Create | `app/src/main/java/.../project/BezwaarDocument.java` | Nieuwe aggregate root entiteit |
| Create | `app/src/main/java/.../project/BezwaarDocumentRepository.java` | Repository |
| Create | `app/src/main/java/.../project/TekstExtractieStatus.java` | Enum |
| Create | `app/src/main/java/.../project/BezwaarExtractieStatus.java` | Enum |
| Modify | `app/src/main/java/.../project/GeextraheerdBezwaarEntiteit.java` | +documentId, +passageTekst, -taakId, -projectNaam, -bestandsnaam, -passageNr |
| Modify | `app/src/main/java/.../project/GeextraheerdBezwaarRepository.java` | Queries via documentId |
| Modify | `app/src/main/java/.../project/ExtractieTaakService.java` | Herschreven rond BezwaarDocument |
| Modify | `app/src/main/java/.../tekstextractie/TekstExtractieService.java` | Herschreven rond BezwaarDocument |
| Modify | `app/src/main/java/.../project/ProjectService.java` | Query BezwaarDocument i.p.v. compositie |
| Modify | `app/src/main/java/.../project/ProjectController.java` | Nieuwe status-mapping |
| Modify | `app/src/main/java/.../project/ExtractieController.java` | Aangepaste DTOs |
| Modify | `app/src/main/java/.../project/ExtractieTaakDto.java` | Velden aanpassen |
| Modify | `app/src/main/java/.../project/BezwaarBestand.java` | Record aanpassen aan nieuw model |
| Modify | `app/src/main/java/.../project/ExtractieWorker.java` | Update BezwaarDocument |
| Modify | `app/src/main/java/.../tekstextractie/TekstExtractieWorker.java` | Update BezwaarDocument |
| Modify | `app/src/main/java/.../kernbezwaar/KernbezwaarService.java` | passageLookup elimineren |
| Delete | `app/src/main/java/.../project/BezwaarBestandEntiteit.java` | Opgegaan in BezwaarDocument |
| Delete | `app/src/main/java/.../project/BezwaarBestandStatus.java` | Vervangen door twee enums |
| Delete | `app/src/main/java/.../project/ExtractieTaak.java` | Efemeer — geen entiteit meer |
| Delete | `app/src/main/java/.../project/ExtractieTaakStatus.java` | Niet meer nodig |
| Delete | `app/src/main/java/.../project/ExtractieTaakRepository.java` | Niet meer nodig |
| Delete | `app/src/main/java/.../project/ExtractiePassageEntiteit.java` | Geabsorbeerd in bezwaar |
| Delete | `app/src/main/java/.../project/ExtractiePassageRepository.java` | Niet meer nodig |
| Delete | `app/src/main/java/.../tekstextractie/TekstExtractieTaak.java` | Efemeer — geen entiteit meer |
| Delete | `app/src/main/java/.../tekstextractie/TekstExtractieTaakStatus.java` | Niet meer nodig |
| Delete | `app/src/main/java/.../tekstextractie/TekstExtractieTaakRepository.java` | Niet meer nodig |
| Modify | `webapp/src/js/bezwaarschriften-bezwaren-tabel.js` | Nieuwe statuswaarden |
| Modify | `webapp/src/js/bezwaarschriften-project-selectie.js` | Aangepaste API calls |
| Modify | Tests | Alle geraakt door bovenstaande wijzigingen |

---

## Task 1: Liquibase-migratie

**Files:**
- Create: `app/src/main/resources/config/liquibase/changelog/20260317-domeinmodel-cleanup.xml`
- Modify: `app/src/main/resources/config/liquibase/master.xml`

- [ ] **Step 1: Maak migratiebestand aan**

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">

  <!-- 1. Maak bezwaar_document tabel -->
  <changeSet id="20260317-01" author="bezwaarschriften">
    <comment>Maak BezwaarDocument tabel — de nieuwe aggregate root</comment>
    <createTable tableName="bezwaar_document">
      <column name="id" type="bigint" autoIncrement="true"><constraints primaryKey="true"/></column>
      <column name="project_naam" type="varchar(255)"><constraints nullable="false"/></column>
      <column name="bestandsnaam" type="varchar(255)"><constraints nullable="false"/></column>
      <column name="tekst_extractie_status" type="varchar(20)" defaultValue="GEEN"><constraints nullable="false"/></column>
      <column name="bezwaar_extractie_status" type="varchar(20)" defaultValue="GEEN"><constraints nullable="false"/></column>
      <column name="extractie_methode" type="varchar(20)"/>
      <column name="aantal_woorden" type="int"/>
      <column name="heeft_passages_die_niet_in_tekst_voorkomen" type="boolean" defaultValueBoolean="false"><constraints nullable="false"/></column>
      <column name="heeft_manueel" type="boolean" defaultValueBoolean="false"><constraints nullable="false"/></column>
      <column name="foutmelding" type="text"/>
      <column name="versie" type="int" defaultValueNumeric="0"><constraints nullable="false"/></column>
    </createTable>
    <createIndex tableName="bezwaar_document" indexName="idx_bezwaar_document_project">
      <column name="project_naam"/>
    </createIndex>
    <addUniqueConstraint tableName="bezwaar_document"
        columnNames="project_naam, bestandsnaam"
        constraintName="uk_bezwaar_document_project_bestand"/>
  </changeSet>

  <!-- 2. Migreer data vanuit bestaande tabellen (PostgreSQL only) -->
  <changeSet id="20260317-02" author="bezwaarschriften">
    <preConditions onFail="MARK_RAN"><dbms type="postgresql"/></preConditions>
    <comment>Migreer documenten vanuit bezwaar_bestand + taken</comment>
    <sql>
      INSERT INTO bezwaar_document (project_naam, bestandsnaam, tekst_extractie_status, bezwaar_extractie_status, extractie_methode, aantal_woorden, heeft_passages_die_niet_in_tekst_voorkomen, heeft_manueel, foutmelding, versie)
      SELECT DISTINCT
        bb.project_naam,
        bb.bestandsnaam,
        CASE
          WHEN te.status = 'KLAAR' THEN 'KLAAR'
          WHEN te.status = 'BEZIG' THEN 'BEZIG'
          WHEN te.status = 'WACHTEND' THEN 'BEZIG'
          WHEN te.status = 'MISLUKT' THEN 'FOUT'
          WHEN te.status = 'OCR_NIET_BESCHIKBAAR' THEN 'FOUT'
          WHEN te.status IS NULL THEN 'GEEN'
          ELSE 'GEEN'
        END,
        CASE
          WHEN et.status = 'KLAAR' THEN 'KLAAR'
          WHEN et.status = 'BEZIG' THEN 'BEZIG'
          WHEN et.status = 'WACHTEND' THEN 'BEZIG'
          WHEN et.status = 'FOUT' THEN 'FOUT'
          WHEN et.status IS NULL THEN 'GEEN'
          ELSE 'GEEN'
        END,
        CASE WHEN te.extractie_methode IS NOT NULL THEN te.extractie_methode::text ELSE NULL END,
        et.aantal_woorden,
        COALESCE(et.heeft_passages_die_niet_in_tekst_voorkomen, false),
        COALESCE(et.heeft_manueel, false),
        COALESCE(et.foutmelding, te.foutmelding),
        0
      FROM bezwaar_bestand bb
      LEFT JOIN LATERAL (
        SELECT * FROM tekst_extractie_taak t
        WHERE t.project_naam = bb.project_naam AND t.bestandsnaam = bb.bestandsnaam
        ORDER BY t.aangemaakt_op DESC LIMIT 1
      ) te ON true
      LEFT JOIN LATERAL (
        SELECT * FROM extractie_taak t
        WHERE t.project_naam = bb.project_naam AND t.bestandsnaam = bb.bestandsnaam
        ORDER BY t.aangemaakt_op DESC LIMIT 1
      ) et ON true
    </sql>
  </changeSet>

  <!-- 3. Voeg document_id en passage_tekst toe aan geextraheerd_bezwaar -->
  <changeSet id="20260317-03" author="bezwaarschriften">
    <comment>Voeg document_id en passage_tekst toe aan geextraheerd_bezwaar</comment>
    <addColumn tableName="geextraheerd_bezwaar">
      <column name="document_id" type="bigint"/>
      <column name="passage_tekst" type="text"/>
    </addColumn>
  </changeSet>

  <!-- 4. Backfill document_id en passage_tekst (PostgreSQL only) -->
  <changeSet id="20260317-04" author="bezwaarschriften">
    <preConditions onFail="MARK_RAN"><dbms type="postgresql"/></preConditions>
    <comment>Backfill document_id vanuit project_naam+bestandsnaam</comment>
    <sql>
      UPDATE geextraheerd_bezwaar gb
      SET document_id = bd.id
      FROM bezwaar_document bd
      WHERE gb.project_naam = bd.project_naam AND gb.bestandsnaam = bd.bestandsnaam
    </sql>
  </changeSet>

  <changeSet id="20260317-05" author="bezwaarschriften">
    <preConditions onFail="MARK_RAN"><dbms type="postgresql"/></preConditions>
    <comment>Backfill passage_tekst vanuit extractie_passage</comment>
    <sql>
      UPDATE geextraheerd_bezwaar gb
      SET passage_tekst = ep.tekst
      FROM extractie_passage ep
      WHERE ep.taak_id = gb.taak_id AND ep.passage_nr = gb.passage_nr
    </sql>
  </changeSet>

  <!-- 5. NOT NULL constraints na backfill -->
  <changeSet id="20260317-06" author="bezwaarschriften">
    <preConditions onFail="MARK_RAN"><dbms type="postgresql"/></preConditions>
    <comment>NOT NULL constraint op document_id</comment>
    <addNotNullConstraint tableName="geextraheerd_bezwaar"
        columnName="document_id" columnDataType="bigint"/>
    <addForeignKeyConstraint baseTableName="geextraheerd_bezwaar"
        baseColumnNames="document_id"
        referencedTableName="bezwaar_document"
        referencedColumnNames="id"
        constraintName="fk_bezwaar_document"/>
  </changeSet>

  <!-- 6. Verwijder oude kolommen van geextraheerd_bezwaar -->
  <changeSet id="20260317-07" author="bezwaarschriften">
    <comment>Verwijder oude kolommen: taak_id, passage_nr, project_naam, bestandsnaam</comment>
    <dropIndex tableName="geextraheerd_bezwaar" indexName="idx_bezwaar_project_bestand"/>
    <dropColumn tableName="geextraheerd_bezwaar" columnName="taak_id"/>
    <dropColumn tableName="geextraheerd_bezwaar" columnName="passage_nr"/>
    <dropColumn tableName="geextraheerd_bezwaar" columnName="project_naam"/>
    <dropColumn tableName="geextraheerd_bezwaar" columnName="bestandsnaam"/>
    <createIndex tableName="geextraheerd_bezwaar" indexName="idx_bezwaar_document">
      <column name="document_id"/>
    </createIndex>
  </changeSet>

  <!-- 7. Verwijder oude tabellen -->
  <changeSet id="20260317-08" author="bezwaarschriften">
    <comment>Verwijder extractie_passage tabel</comment>
    <dropTable tableName="extractie_passage"/>
  </changeSet>

  <changeSet id="20260317-09" author="bezwaarschriften">
    <comment>Verwijder extractie_taak tabel</comment>
    <dropTable tableName="extractie_taak"/>
  </changeSet>

  <changeSet id="20260317-10" author="bezwaarschriften">
    <comment>Verwijder tekst_extractie_taak tabel</comment>
    <dropTable tableName="tekst_extractie_taak"/>
  </changeSet>

  <changeSet id="20260317-11" author="bezwaarschriften">
    <comment>Verwijder bezwaar_bestand tabel</comment>
    <dropTable tableName="bezwaar_bestand"/>
  </changeSet>

</databaseChangeLog>
```

- [ ] **Step 2: Voeg include toe aan master.xml**

Na de laatste `<include>`:
```xml
  <include file="config/liquibase/changelog/20260317-domeinmodel-cleanup.xml"/>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/resources/config/liquibase/
git commit -m "chore: liquibase migratie voor domeinmodel cleanup — BezwaarDocument als aggregate root"
```

---

## Task 2: BezwaarDocument entiteit + status-enums + repository

**Files:**
- Create: `app/src/main/java/.../project/TekstExtractieStatus.java`
- Create: `app/src/main/java/.../project/BezwaarExtractieStatus.java`
- Create: `app/src/main/java/.../project/BezwaarDocument.java`
- Create: `app/src/main/java/.../project/BezwaarDocumentRepository.java`

- [ ] **Step 1: Maak TekstExtractieStatus enum**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

public enum TekstExtractieStatus {
  GEEN, BEZIG, KLAAR, FOUT
}
```

- [ ] **Step 2: Maak BezwaarExtractieStatus enum**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

public enum BezwaarExtractieStatus {
  GEEN, BEZIG, KLAAR, FOUT
}
```

- [ ] **Step 3: Maak BezwaarDocument entiteit**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import javax.persistence.*;

@Entity
@Table(name = "bezwaar_document")
public class BezwaarDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "project_naam", nullable = false)
  private String projectNaam;

  @Column(name = "bestandsnaam", nullable = false)
  private String bestandsnaam;

  @Enumerated(EnumType.STRING)
  @Column(name = "tekst_extractie_status", nullable = false)
  private TekstExtractieStatus tekstExtractieStatus = TekstExtractieStatus.GEEN;

  @Enumerated(EnumType.STRING)
  @Column(name = "bezwaar_extractie_status", nullable = false)
  private BezwaarExtractieStatus bezwaarExtractieStatus = BezwaarExtractieStatus.GEEN;

  @Column(name = "extractie_methode")
  private String extractieMethode;

  @Column(name = "aantal_woorden")
  private Integer aantalWoorden;

  @Column(name = "heeft_passages_die_niet_in_tekst_voorkomen", nullable = false)
  private boolean heeftPassagesDieNietInTekstVoorkomen;

  @Column(name = "heeft_manueel", nullable = false)
  private boolean heeftManueel;

  @Column(name = "foutmelding", columnDefinition = "text")
  private String foutmelding;

  @Version
  @Column(name = "versie", nullable = false)
  private int versie;

  // Getters en setters voor alle velden
}
```

- [ ] **Step 4: Maak BezwaarDocumentRepository**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BezwaarDocumentRepository extends JpaRepository<BezwaarDocument, Long> {

  List<BezwaarDocument> findByProjectNaam(String projectNaam);

  Optional<BezwaarDocument> findByProjectNaamAndBestandsnaam(
      String projectNaam, String bestandsnaam);

  int countByProjectNaam(String projectNaam);

  List<BezwaarDocument> findByTekstExtractieStatus(TekstExtractieStatus status);

  List<BezwaarDocument> findByBezwaarExtractieStatus(BezwaarExtractieStatus status);

  int countByTekstExtractieStatus(TekstExtractieStatus status);

  int countByBezwaarExtractieStatus(BezwaarExtractieStatus status);

  void deleteByProjectNaam(String projectNaam);

  void deleteByProjectNaamAndBestandsnaam(String projectNaam, String bestandsnaam);

  void deleteByProjectNaamAndBestandsnaamIn(String projectNaam, List<String> bestandsnamen);
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/TekstExtractieStatus.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarExtractieStatus.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarDocument.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarDocumentRepository.java
git commit -m "feat: BezwaarDocument entiteit + twee status-enums + repository"
```

---

## Task 3: GeextraheerdBezwaarEntiteit herschrijven

**Files:**
- Modify: `app/src/main/java/.../project/GeextraheerdBezwaarEntiteit.java`
- Modify: `app/src/main/java/.../project/GeextraheerdBezwaarRepository.java`

- [ ] **Step 1: Herschrijf GeextraheerdBezwaarEntiteit**

Vervang alle velden. De entiteit krijgt:
- `documentId` (FK → BezwaarDocument) — vervangt `taakId`, `projectNaam`, `bestandsnaam`
- `passageTekst` — geabsorbeerd vanuit ExtractiePassageEntiteit
- Verwijder: `taakId`, `passageNr`, `projectNaam`, `bestandsnaam`
- Behoud: `id`, `samenvatting`, `passageGevonden`, `manueel`, `embeddingPassage`, `embeddingSamenvatting`

```java
@Entity
@Table(name = "geextraheerd_bezwaar")
public class GeextraheerdBezwaarEntiteit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "document_id", nullable = false)
  private Long documentId;

  @Column(name = "samenvatting", columnDefinition = "text")
  private String samenvatting;

  @Column(name = "passage_tekst", columnDefinition = "text")
  private String passageTekst;

  @Column(name = "passage_gevonden")
  private boolean passageGevonden;

  @Column(name = "manueel")
  private boolean manueel;

  @Column(name = "embedding_passage", columnDefinition = "vector")
  private float[] embeddingPassage;

  @Column(name = "embedding_samenvatting", columnDefinition = "vector")
  private float[] embeddingSamenvatting;

  // Getters en setters
}
```

- [ ] **Step 2: Herschrijf GeextraheerdBezwaarRepository**

```java
public interface GeextraheerdBezwaarRepository
    extends JpaRepository<GeextraheerdBezwaarEntiteit, Long> {

  List<GeextraheerdBezwaarEntiteit> findByDocumentId(Long documentId);

  int countByDocumentId(Long documentId);

  void deleteByDocumentId(Long documentId);

  @Query("SELECT b FROM GeextraheerdBezwaarEntiteit b " +
         "JOIN BezwaarDocument d ON b.documentId = d.id " +
         "WHERE d.projectNaam = :projectNaam")
  List<GeextraheerdBezwaarEntiteit> findByProjectNaam(@Param("projectNaam") String projectNaam);

  @Query("SELECT COUNT(b) FROM GeextraheerdBezwaarEntiteit b " +
         "JOIN BezwaarDocument d ON b.documentId = d.id " +
         "WHERE d.projectNaam = :projectNaam")
  int countByProjectNaam(@Param("projectNaam") String projectNaam);
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarEntiteit.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarRepository.java
git commit -m "refactor: GeextraheerdBezwaarEntiteit → documentId + passageTekst, weg met taakId"
```

---

## Task 4: ExtractieTaakService herschrijven

Dit is de grootste task. De service wordt herschreven rond BezwaarDocument. ExtractieTaak en ExtractiePassage bestaan niet meer als entiteiten.

**Files:**
- Modify: `app/src/main/java/.../project/ExtractieTaakService.java`
- Modify: `app/src/test/java/.../project/ExtractieTaakServiceTest.java`

- [ ] **Step 1: Herschrijf de service**

De constructor krijgt `BezwaarDocumentRepository` i.p.v. `ExtractieTaakRepository` en `ExtractiePassageRepository`. De belangrijkste methoden worden:

**`indienen(projectNaam, bestandsnamen)`:**
- Voor elk bestand: zoek of maak BezwaarDocument
- Business rule: `tekstExtractieStatus` moet `KLAAR` zijn
- Zet `bezwaarExtractieStatus = BEZIG`, `foutmelding = null`
- Wis oude bezwaren via `bezwaarRepository.deleteByDocumentId(doc.id)`
- Retourneer DTOs

**`markeerKlaar(documentId, resultaat)`:**
- Parameter verandert van `taakId` naar `documentId`
- Sla bezwaren op met `documentId` en `passageTekst` (direct op bezwaar, geen aparte passage-entiteit)
- Valideer passages tegen brondocument
- Genereer embeddings
- Update BezwaarDocument: `bezwaarExtractieStatus = KLAAR`, `aantalWoorden`, flags

**`markeerFout(documentId, foutmelding)`:**
- Update BezwaarDocument: `bezwaarExtractieStatus = FOUT`, `foutmelding`

**`pakOpVoorVerwerking()`:**
- Query `bezwaarDocumentRepository.findByBezwaarExtractieStatus(BEZIG)`
- Concurrency check via `countByBezwaarExtractieStatus(BEZIG)`

**`geefExtractieDetails(projectNaam, bestandsnaam)`:**
- Query BezwaarDocument + bezwaren via `documentId`
- Bouw ExtractieDetailDto zonder passage-lookup (passageTekst zit op bezwaar)

**`voegManueelBezwaarToe(projectNaam, bestandsnaam, samenvatting, passageTekst)`:**
- Zoek document, valideer dat `bezwaarExtractieStatus = KLAAR`
- Maak bezwaar met `documentId`, `passageTekst`, `manueel = true`
- Update `heeftManueel = true` op document

**`verwijderBezwaar(projectNaam, bestandsnaam, bezwaarId)`:**
- Verwijder bezwaar, update document count

**`verwerkOnafgeronde(projectNaam)`:**
- Zoek documenten met `bezwaarExtractieStatus = FOUT` → reset naar BEZIG
- Zoek documenten met `tekstExtractieStatus = KLAAR` en `bezwaarExtractieStatus = GEEN` → zet BEZIG

- [ ] **Step 2: Herschrijf alle tests**

De bestaande tests mocken `ExtractieTaakRepository` en `ExtractiePassageRepository` — die bestaan niet meer. Vervang door `BezwaarDocumentRepository` mocks. Test:
- indienen zet status BEZIG en wist oude bezwaren
- markeerKlaar slaat bezwaren op met documentId en passageTekst
- markeerFout zet status FOUT
- voegManueelBezwaarToe maakt bezwaar met manueel=true
- verwijderBezwaar verwijdert bezwaar
- verwerkOnafgeronde reset FOUT documenten

- [ ] **Step 3: Run tests**

Run: `mvn test -pl app -Denforcer.skip=true -Dcheckstyle.skip=true -Dtest=ExtractieTaakServiceTest`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java
git commit -m "refactor: ExtractieTaakService herschreven rond BezwaarDocument"
```

---

## Task 5: TekstExtractieService herschrijven

**Files:**
- Modify: `app/src/main/java/.../tekstextractie/TekstExtractieService.java`
- Modify: `app/src/test/java/.../tekstextractie/TekstExtractieServiceTest.java` (als die bestaat)

- [ ] **Step 1: Herschrijf de service**

De service werkt nu met BezwaarDocument i.p.v. TekstExtractieTaak:

**`indienen(projectNaam, bestandsnaam)`:**
- Zoek of maak BezwaarDocument
- Zet `tekstExtractieStatus = BEZIG`, `foutmelding = null`
- Business rule 2: als tekst-extractie opnieuw → `bezwaarExtractieStatus = GEEN` + bezwaren wissen

**`pakOpVoorVerwerking()`:**
- Query `bezwaarDocumentRepository.findByTekstExtractieStatus(BEZIG)`
- Concurrency via `countByTekstExtractieStatus(BEZIG)`

**`markeerKlaar(documentId, extractieMethode)`:**
- Update `tekstExtractieStatus = KLAAR`, `extractieMethode`

**`markeerFout(documentId, foutmelding)`:**
- Update `tekstExtractieStatus = FOUT`, `foutmelding`

**`isTekstExtractieKlaar(projectNaam, bestandsnaam)`:**
- Check `document.getTekstExtractieStatus() == KLAAR`

- [ ] **Step 2: Tests schrijven/herschrijven**

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/ \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/
git commit -m "refactor: TekstExtractieService herschreven rond BezwaarDocument"
```

---

## Task 6: Workers updaten

**Files:**
- Modify: `app/src/main/java/.../project/ExtractieWorker.java`
- Modify: `app/src/main/java/.../tekstextractie/TekstExtractieWorker.java`

- [ ] **Step 1: ExtractieWorker aanpassen**

De worker krijgt nu `BezwaarDocument` objecten i.p.v. `ExtractieTaak`. Verwerk-methode ontvangt documentId. Bij succes: `markeerKlaar(documentId, resultaat)`. Bij fout: `markeerFout(documentId, foutmelding)`.

- [ ] **Step 2: TekstExtractieWorker aanpassen**

Idem: ontvangt `BezwaarDocument`, meldt terug via `markeerKlaar(documentId, methode)` / `markeerFout(documentId, fout)`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieWorker.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieWorker.java
git commit -m "refactor: workers gebruiken BezwaarDocument i.p.v. taken"
```

---

## Task 7: ProjectService vereenvoudigen

**Files:**
- Modify: `app/src/main/java/.../project/ProjectService.java`
- Modify: `app/src/main/java/.../project/BezwaarBestand.java`
- Modify: `app/src/test/java/.../project/ProjectServiceTest.java`

- [ ] **Step 1: BezwaarBestand record aanpassen**

Het record wordt simpeler — de status komt direct uit BezwaarDocument:

```java
public record BezwaarBestand(
    String bestandsnaam,
    String tekstExtractieStatus,
    String bezwaarExtractieStatus,
    Integer aantalWoorden,
    Integer aantalBezwaren,
    boolean heeftPassagesDieNietInTekstVoorkomen,
    boolean heeftManueel,
    String extractieMethode,
    String foutmelding
) {}
```

- [ ] **Step 2: ProjectService.geefBezwaren() vereenvoudigen**

Was: 70 regels compositie-logica met 2 taak-repositories + status-mapping.
Wordt: query BezwaarDocument + tel bezwaren + combineer met bestandsnamen van filesystem.

```java
public List<BezwaarBestand> geefBezwaren(String projectNaam) {
  var bestandsnamen = projectPoort.geefBestandsnamen(projectNaam);
  var documenten = bezwaarDocumentRepository.findByProjectNaam(projectNaam)
      .stream().collect(Collectors.toMap(BezwaarDocument::getBestandsnaam, d -> d));

  return bestandsnamen.stream()
      .map(naam -> {
        if (!isOndersteundFormaat(naam)) {
          return new BezwaarBestand(naam, "NIET_ONDERSTEUND", "GEEN",
              null, null, false, false, null, null);
        }
        var doc = documenten.get(naam);
        if (doc == null) {
          return new BezwaarBestand(naam, "GEEN", "GEEN",
              null, null, false, false, null, null);
        }
        var aantalBezwaren = bezwaarRepository.countByDocumentId(doc.getId());
        return new BezwaarBestand(naam,
            doc.getTekstExtractieStatus().name(),
            doc.getBezwaarExtractieStatus().name(),
            doc.getAantalWoorden(),
            aantalBezwaren,
            doc.isHeeftPassagesDieNietInTekstVoorkomen(),
            doc.isHeeftManueel(),
            doc.getExtractieMethode(),
            doc.getFoutmelding());
      })
      .toList();
}
```

- [ ] **Step 3: Verwijder-methoden updaten**

`verwijderBezwaar()`, `verwijderBezwaren()`, `verwijderProject()` — werken nu met `bezwaarDocumentRepository`.

- [ ] **Step 4: Tests herschrijven**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestand.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java
git commit -m "refactor: ProjectService query BezwaarDocument i.p.v. taak-compositie"
```

---

## Task 8: Controllers + DTOs + WebSocket

**Files:**
- Modify: `app/src/main/java/.../project/ProjectController.java`
- Modify: `app/src/main/java/.../project/ExtractieController.java`
- Modify: `app/src/main/java/.../project/ExtractieTaakDto.java`
- Modify: `app/src/main/java/.../project/ExtractieNotificatie.java`
- Modify: `app/src/main/java/.../project/TaakWebSocketHandler.java`
- Modify: Tests

- [ ] **Step 1: ProjectController aanpassen**

BezwaarBestandDto volgt de nieuwe BezwaarBestand record-structuur met twee losse status-strings. De `statusNaarString()` vertaling verdwijnt — statussen zijn al strings.

- [ ] **Step 2: ExtractieController aanpassen**

De endpoints die `taakId` in het pad hebben worden vervangen door `bestandsnaam`. ExtractieTaakDto bevat nu document-velden i.p.v. taak-velden.

- [ ] **Step 3: WebSocket notificaties aanpassen**

`ExtractieNotificatie` stuurt document-updates i.p.v. taak-updates.

- [ ] **Step 4: Tests**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieController.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakDto.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieNotificatie.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/TaakWebSocketHandler.java
git commit -m "refactor: controllers en DTOs werken met BezwaarDocument"
```

---

## Task 9: KernbezwaarService — passageLookup elimineren

**Files:**
- Modify: `app/src/main/java/.../kernbezwaar/KernbezwaarService.java`
- Modify: `app/src/test/java/.../kernbezwaar/KernbezwaarServiceTest.java`

- [ ] **Step 1: Verwijder bouwPassageLookup()**

`passageTekst` zit nu direct op het bezwaar. Overal waar `geefPassageTekst(bezwaar, passageLookup)` staat → `bezwaar.getPassageTekst()`.

Verwijder:
- `bouwPassageLookup()` methode
- `passageLookup` parameter uit `clusterMetDeduplicatieVooraf()` en `clusterMetDeduplicatieAchteraf()`
- `geefPassageTekst()` helper

- [ ] **Step 2: Verwijder ExtractiePassageRepository en ExtractieTaakRepository injecties**

De constructor heeft deze niet meer nodig.

- [ ] **Step 3: Fix tests**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java
git commit -m "refactor: passageLookup geëlimineerd — passageTekst zit op het bezwaar"
```

---

## Task 10: Oude entiteiten en bestanden verwijderen

**Files:**
- Delete: `ExtractieTaak.java`, `ExtractieTaakStatus.java`, `ExtractieTaakRepository.java`
- Delete: `ExtractiePassageEntiteit.java`, `ExtractiePassageRepository.java`
- Delete: `TekstExtractieTaak.java`, `TekstExtractieTaakStatus.java`, `TekstExtractieTaakRepository.java`
- Delete: `BezwaarBestandEntiteit.java`, `BezwaarBestandStatus.java`
- Delete: Bijbehorende repository-tests

- [ ] **Step 1: Verwijder alle genoemde bestanden**

Controleer eerst dat geen enkele class nog gerefereerd wordt (compiler errors = nog een referentie).

- [ ] **Step 2: Fix compile errors**

Eventuele vergeten referenties fixen.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: verwijder oude entiteiten — ExtractieTaak, TekstExtractieTaak, ExtractiePassage, BezwaarBestand"
```

---

## Task 11: Frontend aanpassen

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js`
- Modify: Cypress tests

- [ ] **Step 1: Status-labels en pills aanpassen**

De API geeft nu twee losse statussen (`tekstExtractieStatus`, `bezwaarExtractieStatus`) i.p.v. één `status`. De frontend moet de gecombineerde weergave-logica updaten:

```javascript
_bepaalWeergaveStatus(bezwaar) {
  if (bezwaar.tekstExtractieStatus === 'GEEN') return { label: 'Te verwerken', type: '' };
  if (bezwaar.tekstExtractieStatus === 'BEZIG') return { label: 'Tekst extractie...', type: 'warning' };
  if (bezwaar.tekstExtractieStatus === 'FOUT') return { label: 'Tekst extractie mislukt', type: 'error' };
  // tekstExtractie = KLAAR
  if (bezwaar.bezwaarExtractieStatus === 'GEEN') return { label: 'Klaar voor extractie', type: '' };
  if (bezwaar.bezwaarExtractieStatus === 'BEZIG') return { label: 'Extractie...', type: 'warning' };
  if (bezwaar.bezwaarExtractieStatus === 'FOUT') return { label: 'Extractie mislukt', type: 'error' };
  return { label: 'Extractie klaar', type: 'success' };
}
```

- [ ] **Step 2: WebSocket handler aanpassen**

`werkBijMetTaakUpdate()` verwacht nu document-velden i.p.v. taak-velden.

- [ ] **Step 3: API calls aanpassen**

Endpoints die `taakId` gebruikten → `bestandsnaam`.

- [ ] **Step 4: Cypress tests updaten**

- [ ] **Step 5: Commit**

```bash
git add webapp/
git commit -m "refactor: frontend aangepast aan nieuwe status-model"
```

---

## Task 12: Volledige verificatie

- [ ] **Step 1: Run alle unit tests**

Run: `mvn test -pl app -Denforcer.skip=true -Dcheckstyle.skip=true`
Verwacht: PASS.

- [ ] **Step 2: Run integratietests**

Run: `mvn verify -pl app -Denforcer.skip=true -Dcheckstyle.skip=true`
Verwacht: PASS.

- [ ] **Step 3: Run frontend tests**

Run: `cd webapp && npm test`
Verwacht: PASS.

- [ ] **Step 4: Update domeinmodel documentatie**

Update `docs/architecture/domain-model.md` met het nieuwe ER-diagram uit de design spec.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "docs: domeinmodel documentatie bijgewerkt na cleanup"
```

---

## Verificatieplan

| Check | Wat | Hoe |
|-------|-----|-----|
| Unit tests | Alle passen | `mvn test -pl app -Denforcer.skip=true` |
| Integratietests | Migratie + queries werken | `mvn verify -pl app -Denforcer.skip=true` |
| Frontend tests | Geen regressie | `cd webapp && npm test` |
| Handmatig | Upload → tekst-extractie → bezwaar-extractie flow | Volledig doorlopen |
| Handmatig | Her-extractie wist oude bezwaren | Upload, extraheer, extraheer opnieuw |
| Handmatig | Status-weergave klopt | Alle statussen visueel controleren |
| Handmatig | Manueel bezwaar toevoegen/verwijderen | Side-sheet testen |
| Handmatig | Clustering na extractie | Bezwaren clusteren, statistieken controleren |
