# Bezwaar via document, niet via taak — Implementatieplan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Individuele bezwaren linken aan hun document (project+bestandsnaam) i.p.v. aan de extractietaak, en oude bezwaren opruimen bij her-extractie.

**Architecture:** Denormalisatie van `project_naam` en `bestandsnaam` op `geextraheerd_bezwaar`. Repository-queries gaan direct via die kolommen. `taak_id` blijft voor traceerbaarheid. `indienen()` ruimt oude taken+bezwaren op. Na cleanup bevat de database altijd precies één set bezwaren per document — de KLAAR-filter in queries is niet langer nodig.

**Tech Stack:** Java 21, Spring Boot 3.x, JPA/Hibernate, Liquibase, Cypress

**Design-keuze `passageLookup`:** De `bouwPassageLookup(taakIds)` in `KernbezwaarService` blijft taak-gebaseerd. Passages zijn een implementatiedetail van de extractie (ze horen bij de taak, niet bij het document). Alleen de `bestandsnaamLookup` wordt geëlimineerd.

**Design-keuze `geefExtractieDetails` en `verwijderBezwaar`:** Deze methoden in `ExtractieTaakService` blijven via `taakId` werken. Binnen de context van één taak is dat correct en er is altijd maar één actieve taak per bestand.

---

## Bestandenoverzicht

| Actie | Bestand | Wat |
|-------|---------|-----|
| Create | `app/src/main/resources/config/liquibase/changelog/20260316-bezwaar-document-kolommen.xml` | Liquibase migratie |
| Modify | `app/src/main/resources/config/liquibase/master.xml` | Include migratie |
| Modify | `app/src/main/java/.../project/GeextraheerdBezwaarEntiteit.java` | +projectNaam, +bestandsnaam |
| Modify | `app/src/main/java/.../project/GeextraheerdBezwaarRepository.java` | Queries via document |
| Modify | `app/src/main/java/.../project/ExtractiePassageRepository.java` | +deleteByTaakId |
| Modify | `app/src/main/java/.../project/ExtractieTaakService.java` | Opruimen + velden zetten |
| Modify | `app/src/main/java/.../kernbezwaar/KernbezwaarService.java` | bestandsnaamLookup elimineren |
| Modify | `app/src/main/java/.../kernbezwaar/PassageDeduplicatieService.java` | bestandsnaamLookup parameter verwijderen |
| Modify | Tests | Alle geraakt door bovenstaande wijzigingen |

---

## Task 1: Liquibase-migratie

**Files:**
- Create: `app/src/main/resources/config/liquibase/changelog/20260316-bezwaar-document-kolommen.xml`
- Modify: `app/src/main/resources/config/liquibase/master.xml`

- [ ] **Step 1: Maak migratiebestand aan**

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">

  <changeSet id="20260316-1" author="bezwaarschriften">
    <comment>Voeg project_naam en bestandsnaam toe aan geextraheerd_bezwaar (domeinmodel)</comment>
    <addColumn tableName="geextraheerd_bezwaar">
      <column name="project_naam" type="varchar(255)"/>
      <column name="bestandsnaam" type="varchar(255)"/>
    </addColumn>
  </changeSet>

  <changeSet id="20260316-2" author="bezwaarschriften">
    <comment>Backfill vanuit extractie_taak</comment>
    <sql>
      UPDATE geextraheerd_bezwaar b
      SET project_naam = t.project_naam,
          bestandsnaam = t.bestandsnaam
      FROM extractie_taak t
      WHERE b.taak_id = t.id
    </sql>
  </changeSet>

  <changeSet id="20260316-3" author="bezwaarschriften">
    <comment>NOT NULL constraint na backfill</comment>
    <addNotNullConstraint tableName="geextraheerd_bezwaar"
        columnName="project_naam" columnDataType="varchar(255)"/>
    <addNotNullConstraint tableName="geextraheerd_bezwaar"
        columnName="bestandsnaam" columnDataType="varchar(255)"/>
  </changeSet>

  <changeSet id="20260316-4" author="bezwaarschriften">
    <comment>Index voor domeinqueries</comment>
    <createIndex tableName="geextraheerd_bezwaar"
        indexName="idx_bezwaar_project_bestand">
      <column name="project_naam"/>
      <column name="bestandsnaam"/>
    </createIndex>
  </changeSet>

</databaseChangeLog>
```

- [ ] **Step 2: Voeg include toe aan master.xml**

Na de laatste `<include>` in `master.xml`:
```xml
  <include file="config/liquibase/changelog/20260316-bezwaar-document-kolommen.xml"/>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/resources/config/liquibase/changelog/20260316-bezwaar-document-kolommen.xml app/src/main/resources/config/liquibase/master.xml
git commit -m "chore: liquibase migratie voor project_naam en bestandsnaam op geextraheerd_bezwaar"
```

---

## Task 2: Entiteit + Repository + PassageRepository

**Files:**
- Modify: `app/src/main/java/.../project/GeextraheerdBezwaarEntiteit.java`
- Modify: `app/src/main/java/.../project/GeextraheerdBezwaarRepository.java`
- Modify: `app/src/main/java/.../project/ExtractiePassageRepository.java`
- Test: `app/src/test/java/.../project/ExtractieTaakServiceTest.java`

- [ ] **Step 1: Schrijf test dat bezwaar projectNaam en bestandsnaam heeft**

In de bestaande testklasse, voeg een test toe die verifieert dat een bezwaar aangemaakt met `markeerKlaar` de juiste `projectNaam` en `bestandsnaam` heeft.

- [ ] **Step 2: Run test, verwacht FAIL**

Run: `mvn test -pl app -Dtest=ExtractieTaakServiceTest -DfailIfNoTests=false`

- [ ] **Step 3: Voeg velden toe aan GeextraheerdBezwaarEntiteit**

Voeg toe na het `taakId` veld:
```java
@Column(name = "project_naam", nullable = false)
private String projectNaam;

@Column(name = "bestandsnaam", nullable = false)
private String bestandsnaam;
```
Plus getters en setters.

- [ ] **Step 4: Herschrijf repository-queries**

`GeextraheerdBezwaarRepository.java` — vervang de `@Query`-gebaseerde methoden door derived queries:

```java
List<GeextraheerdBezwaarEntiteit> findByProjectNaam(String projectNaam);

int countByProjectNaam(String projectNaam);

List<GeextraheerdBezwaarEntiteit> findByProjectNaamAndBestandsnaam(
    String projectNaam, String bestandsnaam);

void deleteByProjectNaamAndBestandsnaam(
    String projectNaam, String bestandsnaam);
```

Geen `@Query` annotaties meer nodig — Spring Data genereert queries uit de methodenaam. De KLAAR-filter is niet langer nodig: na cleanup bevat de database altijd precies één set bezwaren per document.

> **Afhankelijkheid:** `deleteByProjectNaamAndBestandsnaam` wordt in Task 3 gebruikt door `indienen()`.

- [ ] **Step 5: Voeg `deleteByTaakId` toe aan ExtractiePassageRepository**

```java
void deleteByTaakId(Long taakId);
```

Dit is nodig voor het opruimen van passages bij her-extractie (Task 3).

- [ ] **Step 6: Run test, verwacht PASS**

Run: `mvn test -pl app -Dtest=ExtractieTaakServiceTest -DfailIfNoTests=false`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarEntiteit.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarRepository.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractiePassageRepository.java
git commit -m "feat: bezwaar kent project en document rechtstreeks"
```

---

## Task 3: Bezwaren opruimen bij her-extractie + velden zetten

**Files:**
- Modify: `app/src/main/java/.../project/ExtractieTaakService.java`
- Test: `app/src/test/java/.../project/ExtractieTaakServiceTest.java`

- [ ] **Step 1: Schrijf test: indienen voor bestaand bestand ruimt ALLE oude bezwaren op**

Test dat als er meerdere oude taken+bezwaren bestaan voor een bestandsnaam (bv. een KLAAR + een FOUT taak), `indienen()` alles verwijdert voordat de nieuwe taak wordt aangemaakt. Verifieer dat na `indienen()` er precies één WACHTEND-taak bestaat en geen bezwaren uit eerdere runs.

- [ ] **Step 2: Run test, verwacht FAIL**

- [ ] **Step 3: Pas indienen() aan — ruim ALLE oude taken op**

In `indienen()`, vóór het aanmaken van de nieuwe taak:
```java
// Ruim ALLE bestaande taken + bezwaren + passages op voor dit bestand
var oudeTaken = repository.findByProjectNaam(projectNaam).stream()
    .filter(t -> t.getBestandsnaam().equals(bestandsnaam))
    .toList();
if (!oudeTaken.isEmpty()) {
  bezwaarRepository.deleteByProjectNaamAndBestandsnaam(projectNaam, bestandsnaam);
  for (var oudeTaak : oudeTaken) {
    passageRepository.deleteByTaakId(oudeTaak.getId());
  }
  repository.deleteAll(oudeTaken);
}
```

> Cleanup en aanmaak van de nieuwe taak vallen binnen dezelfde `@Transactional` — als cleanup faalt, wordt de hele operatie teruggedraaid.

- [ ] **Step 4: Run test, verwacht PASS**

- [ ] **Step 5: Schrijf test: markeerKlaar zet projectNaam en bestandsnaam op bezwaren**

- [ ] **Step 6: Run test, verwacht FAIL**

- [ ] **Step 7: Pas markeerKlaar() aan — zet velden**

In `markeerKlaar()`, bij het aanmaken van bezwaar-entiteiten:
```java
entiteit.setProjectNaam(taak.getProjectNaam());
entiteit.setBestandsnaam(taak.getBestandsnaam());
```

- [ ] **Step 8: Schrijf test: voegManueelBezwaarToe zet projectNaam en bestandsnaam**

- [ ] **Step 9: Pas voegManueelBezwaarToe() aan — zet velden**

Zelfde twee regels toevoegen bij het aanmaken van `bezwaarEntiteit`.

- [ ] **Step 10: Run alle ExtractieTaakService tests**

Run: `mvn test -pl app -Dtest=ExtractieTaakServiceTest`

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java
git commit -m "fix: ruim oude bezwaren op bij her-extractie, zet document-velden"
```

---

## Task 4: KernbezwaarService — elimineer bestandsnaamLookup

**Files:**
- Modify: `app/src/main/java/.../kernbezwaar/KernbezwaarService.java`
- Modify: `app/src/main/java/.../kernbezwaar/PassageDeduplicatieService.java`
- Test: `app/src/test/java/.../kernbezwaar/KernbezwaarServiceTest.java`
- Test: `app/src/test/java/.../kernbezwaar/PassageDeduplicatieServiceTest.java`

- [ ] **Step 1: Pas PassageDeduplicatieService.groepeer() aan**

Verwijder de `Map<Long, String> bestandsnaamLookup` parameter. Haal `bestandsnaam` direct uit de bezwaar-entiteit:

```java
public List<DeduplicatieGroep> groepeer(
    List<GeextraheerdBezwaarEntiteit> bezwaren,
    Map<Long, Map<Integer, String>> passageLookup) {
  // ...
  // Was: bestandsnaamLookup.getOrDefault(bezwaar.getTaakId(), "onbekend")
  // Wordt: bezwaar.getBestandsnaam()
```

- [ ] **Step 2: Pas KernbezwaarService.clusterProject() aan**

- Verwijder `bouwBestandsnaamLookup()` methode
- Verwijder de aanroep `final var bestandsnaamLookup = bouwBestandsnaamLookup(taakIds);`
- Pas alle aanroepen naar `deduplicatieService.groepeer()` aan (verwijder derde parameter)

- [ ] **Step 3: Run KernbezwaarService tests**

Run: `mvn test -pl app -Dtest=KernbezwaarServiceTest`
Fix failures door de `bestandsnaamLookup` setup te verwijderen uit testcode en bezwaar-entiteiten van `projectNaam` + `bestandsnaam` te voorzien.

- [ ] **Step 4: Run PassageDeduplicatieService tests**

Run: `mvn test -pl app -Dtest=PassageDeduplicatieServiceTest`
Fix de test-aanroepen van `groepeer()` (verwijder derde parameter, zet `bestandsnaam` op test-bezwaren).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/PassageDeduplicatieService.java app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/PassageDeduplicatieServiceTest.java
git commit -m "refactor: bezwaar.getBestandsnaam() vervangt bestandsnaamLookup via taak"
```

---

## Task 5: Volledige verificatie

**Files:** Geen nieuwe wijzigingen

- [ ] **Step 1: Run alle unit tests**

Run: `mvn test -pl app`
Verwacht: PASS. Fix eventuele overige failures door ontbrekende `projectNaam`/`bestandsnaam` in test-entiteiten.

- [ ] **Step 2: Run integratietests**

Run: `mvn verify -pl app`
Verwacht: PASS. De Liquibase-migratie wordt automatisch toegepast op de Testcontainers database.

- [ ] **Step 3: Run frontend tests**

Run: `cd webapp && npm test`
Verwacht: PASS. Frontend-code wijzigt niet — de API-response structuur blijft identiek.

- [ ] **Step 4: Commit eventuele testfixes**

---

## Task 6: Documentatie

- [ ] **Step 1: Update domeinmodel diagram**

In `docs/architecture/domain-model.md`: voeg `projectNaam` en `bestandsnaam` toe aan de bezwaar-entiteit.

- [ ] **Step 2: Commit**

```bash
git add docs/
git commit -m "docs: domeinmodel bijgewerkt — bezwaar kent document rechtstreeks"
```

---

## Verificatieplan

| Check | Wat | Hoe |
|-------|-----|-----|
| Unit tests | Alle passen | `mvn test -pl app` |
| Integratietests | Migratie + queries werken | `mvn verify -pl app` |
| Frontend tests | Geen regressie | `cd webapp && npm test` |
| Handmatig | Her-extractie produceert geen dubbele bezwaren | Upload bestand, extraheer, extraheer opnieuw, controleer telling |
| Handmatig | Clustering-statistieken kloppen | Na clustering: "X herleid" + "Y niet toegewezen" = totaal bezwaren |
| Handmatig | Meerdere her-extracties | Extraheer 3× hetzelfde bestand, verifieer dat er maar 1 set bezwaren bestaat |
