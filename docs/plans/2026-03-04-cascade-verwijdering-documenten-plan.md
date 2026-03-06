# Cascade Verwijdering bij Document- en Projectdeletie — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Bij verwijdering van een document worden verweesd kernbezwaar-referenties opgeruimd, en lege kernbezwaren/thema's verwijderd. Bij projectverwijdering wordt alle gerelateerde data opgeruimd.

**Architecture:** Directe service-aanroep — `ProjectService` roept `KernbezwaarService` aan voor cascade cleanup. Alles in 1 transactie. Drie nieuwe JPQL bulk-delete queries op de repositories voor de stapsgewijze opruiming.

**Tech Stack:** Java 21, Spring Data JPA, JPQL bulk deletes, Mockito unit tests, Testcontainers integratietest

---

### Task 1: Repository queries toevoegen

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarReferentieRepository.java:8-21`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarRepository.java:6-13`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ThemaRepository.java:7-16`

**Step 1: Voeg delete-query toe aan KernbezwaarReferentieRepository**

Voeg toe na regel 20:

```java
@Modifying
@Query("DELETE FROM KernbezwaarReferentieEntiteit r "
    + "WHERE r.bestandsnaam = :bestandsnaam "
    + "AND r.kernbezwaarId IN ("
    + "  SELECT k.id FROM KernbezwaarEntiteit k "
    + "  WHERE k.themaId IN ("
    + "    SELECT t.id FROM ThemaEntiteit t "
    + "    WHERE t.projectNaam = :projectNaam))")
void deleteByBestandsnaamAndProjectNaam(
    @Param("bestandsnaam") String bestandsnaam,
    @Param("projectNaam") String projectNaam);
```

Import toevoegen: `org.springframework.data.jpa.repository.Modifying`

**Step 2: Voeg delete-query toe aan KernbezwaarRepository**

Voeg toe na regel 12:

```java
@Modifying
@Query("DELETE FROM KernbezwaarEntiteit k "
    + "WHERE k.themaId IN ("
    + "  SELECT t.id FROM ThemaEntiteit t WHERE t.projectNaam = :projectNaam) "
    + "AND k.id NOT IN ("
    + "  SELECT DISTINCT r.kernbezwaarId FROM KernbezwaarReferentieEntiteit r)")
void deleteZonderReferenties(@Param("projectNaam") String projectNaam);
```

Imports toevoegen: `org.springframework.data.jpa.repository.Modifying`, `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`

**Step 3: Voeg delete-query toe aan ThemaRepository**

Voeg toe na regel 15:

```java
@Modifying
@Query("DELETE FROM ThemaEntiteit t "
    + "WHERE t.projectNaam = :projectNaam "
    + "AND t.id NOT IN ("
    + "  SELECT DISTINCT k.themaId FROM KernbezwaarEntiteit k)")
void deleteZonderKernbezwaren(@Param("projectNaam") String projectNaam);
```

Imports toevoegen: `org.springframework.data.jpa.repository.Modifying`, `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`

**Step 4: Compileer**

Run: `mvn compile -pl app -DskipTests -q`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarReferentieRepository.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarRepository.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ThemaRepository.java
git commit -m "feat: repository queries voor cascade-verwijdering"
```

---

### Task 2: Unit tests voor KernbezwaarService cascade-cleanup

**Files:**
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java`

**Step 1: Schrijf unit tests voor `ruimOpNaDocumentVerwijdering`**

Voeg de volgende tests toe aan `KernbezwaarServiceTest.java`:

```java
@Test
void ruimOpNaDocumentVerwijdering_verwijdertReferentiesEnLegeKernbezwarenEnThemas() {
  service.ruimOpNaDocumentVerwijdering("windmolens", "bezwaar-001.txt");

  verify(referentieRepository).deleteByBestandsnaamAndProjectNaam("bezwaar-001.txt", "windmolens");
  verify(kernbezwaarRepository).deleteZonderReferenties("windmolens");
  verify(themaRepository).deleteZonderKernbezwaren("windmolens");
}

@Test
void ruimOpNaDocumentVerwijdering_roeptStappenInJuisteVolgordeAan() {
  var inOrder = inOrder(referentieRepository, kernbezwaarRepository, themaRepository);

  service.ruimOpNaDocumentVerwijdering("windmolens", "bezwaar-001.txt");

  inOrder.verify(referentieRepository).deleteByBestandsnaamAndProjectNaam("bezwaar-001.txt", "windmolens");
  inOrder.verify(kernbezwaarRepository).deleteZonderReferenties("windmolens");
  inOrder.verify(themaRepository).deleteZonderKernbezwaren("windmolens");
}
```

Import toevoegen: `static org.mockito.Mockito.inOrder`

**Step 2: Schrijf unit test voor `ruimAllesOpVoorProject`**

```java
@Test
void ruimAllesOpVoorProject_verwijdertAlleKernbezwaarEnClusteringData() {
  service.ruimAllesOpVoorProject("windmolens");

  verify(themaRepository).deleteByProjectNaam("windmolens");
  verify(clusteringTaakRepository).deleteByProjectNaam("windmolens");
}
```

Hiervoor moet `clusteringTaakRepository` als mock beschikbaar zijn. Check of deze al in het testbestand staat (dat is zo — `ClusteringTaakService` is een dep maar `ClusteringTaakRepository` niet direct). `KernbezwaarService` heeft `ClusteringTaakService` als dep, niet `ClusteringTaakRepository`. Maar voor `ruimAllesOpVoorProject` hebben we `ClusteringTaakRepository` nodig.

Er zijn twee opties:
- `KernbezwaarService` injecteert `ClusteringTaakRepository` (die staat al als field op regel 37 — nee, dat is `referentieRepository`; `ClusteringTaakService` staat op regel 38).

Kijk in de constructor: `ClusteringTaakService clusteringTaakService` is een dep op KernbezwaarService. Maar ClusteringTaakService heeft waarschijnlijk geen `deleteByProjectNaam` method. We moeten `ClusteringTaakRepository` toevoegen als extra dependency aan KernbezwaarService, of een methode toevoegen aan ClusteringTaakService.

Eenvoudigst: voeg `ClusteringTaakRepository` als extra constructor-parameter toe aan `KernbezwaarService`.

In de test: voeg `@Mock ClusteringTaakRepository clusteringTaakRepository;` toe en pas de constructor-aanroep aan.

**Step 3: Voer de tests uit en controleer dat ze falen**

Run: `mvn test -pl app -Dtest=KernbezwaarServiceTest#ruimOpNaDocumentVerwijdering_verwijdertReferentiesEnLegeKernbezwarenEnThemas+ruimOpNaDocumentVerwijdering_roeptStappenInJuisteVolgordeAan+ruimAllesOpVoorProject_verwijdertAlleKernbezwaarEnClusteringData -DfailIfNoTests=false`
Expected: COMPILATION ERROR (methoden bestaan nog niet)

**Step 4: Commit**

```bash
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java
git commit -m "test: failing tests voor KernbezwaarService cascade-cleanup"
```

---

### Task 3: Implementeer KernbezwaarService cleanup-methoden

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java`

**Step 1: Voeg `ClusteringTaakRepository` toe als dependency**

Voeg field toe (na regel 38):
```java
private final ClusteringTaakRepository clusteringTaakRepository;
```

Voeg constructor-parameter toe en assignment in constructor body.

**Step 2: Implementeer `ruimOpNaDocumentVerwijdering`**

Voeg toe na de `slaAntwoordOp` methode (na regel 187):

```java
/**
 * Ruimt kernbezwaar-data op na verwijdering van een document.
 * Verwijdert referenties voor het bestand, daarna lege kernbezwaren en lege thema's.
 *
 * @param projectNaam naam van het project
 * @param bestandsnaam naam van het verwijderde bestand
 */
public void ruimOpNaDocumentVerwijdering(String projectNaam, String bestandsnaam) {
  referentieRepository.deleteByBestandsnaamAndProjectNaam(bestandsnaam, projectNaam);
  kernbezwaarRepository.deleteZonderReferenties(projectNaam);
  themaRepository.deleteZonderKernbezwaren(projectNaam);
}
```

**Step 3: Implementeer `ruimAllesOpVoorProject`**

```java
/**
 * Ruimt alle kernbezwaar- en clusteringdata op voor een project.
 *
 * @param projectNaam naam van het project
 */
public void ruimAllesOpVoorProject(String projectNaam) {
  themaRepository.deleteByProjectNaam(projectNaam);
  clusteringTaakRepository.deleteByProjectNaam(projectNaam);
}
```

**Step 4: Voer de tests uit**

Run: `mvn test -pl app -Dtest=KernbezwaarServiceTest -DfailIfNoTests=false`
Expected: ALL TESTS PASS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java
git commit -m "feat: KernbezwaarService cleanup-methoden voor cascade-verwijdering"
```

---

### Task 4: Unit tests voor ProjectService wijzigingen

**Files:**
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java`

De `ProjectService` constructor verandert: `KernbezwaarService` en `ConsolidatieTaakRepository` worden toegevoegd.

**Step 1: Voeg mocks toe en pas setUp aan**

Voeg toe als fields:
```java
@Mock
private KernbezwaarService kernbezwaarService;

@Mock
private ConsolidatieTaakRepository consolidatieTaakRepository;
```

Pas `setUp()` aan:
```java
@BeforeEach
void setUp() {
  service = new ProjectService(projectPoort, extractieTaakRepository,
      kernbezwaarService, consolidatieTaakRepository);
}
```

Imports toevoegen:
```java
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarService;
import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakRepository;
```

**Step 2: Voeg test toe voor verwijderBezwaar cascade**

```java
@Test
void verwijderBezwaar_ruimtKernbezwaarDataOpVoorVerwijdering() {
  when(projectPoort.verwijderBestand("windmolens", "bezwaar-001.txt")).thenReturn(true);

  service.verwijderBezwaar("windmolens", "bezwaar-001.txt");

  var inOrder = inOrder(kernbezwaarService, extractieTaakRepository, projectPoort);
  inOrder.verify(kernbezwaarService).ruimOpNaDocumentVerwijdering("windmolens", "bezwaar-001.txt");
  inOrder.verify(extractieTaakRepository).deleteByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt");
  inOrder.verify(projectPoort).verwijderBestand("windmolens", "bezwaar-001.txt");
}
```

**Step 3: Pas bestaande verwijderProject tests aan en voeg test toe voor volledige cleanup**

Update test `verwijderProject_verwijdertExtractieTakenEnDelegeerNaarPoort`:
```java
@Test
void verwijderProject_verwijdertAlleDataEnDelegeerNaarPoort() {
  when(projectPoort.verwijderProject("oud-project")).thenReturn(true);

  boolean result = service.verwijderProject("oud-project");

  assertThat(result).isTrue();
  var inOrder = inOrder(kernbezwaarService, consolidatieTaakRepository,
      extractieTaakRepository, projectPoort);
  inOrder.verify(kernbezwaarService).ruimAllesOpVoorProject("oud-project");
  inOrder.verify(consolidatieTaakRepository).deleteByProjectNaam("oud-project");
  inOrder.verify(extractieTaakRepository).deleteByProjectNaam("oud-project");
  inOrder.verify(projectPoort).verwijderProject("oud-project");
}
```

Update ook test `verwijderProject_geeftFalseAlsProjectNietBestaat` met de extra verify's.

**Step 4: Voer tests uit en controleer dat ze falen**

Run: `mvn test -pl app -Dtest=ProjectServiceTest -DfailIfNoTests=false`
Expected: COMPILATION ERROR (ProjectService constructor heeft nog geen nieuwe parameters)

**Step 5: Commit**

```bash
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java
git commit -m "test: failing tests voor ProjectService cascade-verwijdering"
```

---

### Task 5: Implementeer ProjectService wijzigingen

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java`

**Step 1: Voeg dependencies toe**

Voeg fields toe (na regel 24):
```java
private final KernbezwaarService kernbezwaarService;
private final ConsolidatieTaakRepository consolidatieTaakRepository;
```

Pas constructor aan met extra parameters en assignments.

Imports toevoegen:
```java
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarService;
import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakRepository;
```

**Step 2: Wijzig `verwijderBezwaar`**

```java
@Transactional
public boolean verwijderBezwaar(String projectNaam, String bestandsnaam) {
  kernbezwaarService.ruimOpNaDocumentVerwijdering(projectNaam, bestandsnaam);
  extractieTaakRepository.deleteByProjectNaamAndBestandsnaam(projectNaam, bestandsnaam);
  return projectPoort.verwijderBestand(projectNaam, bestandsnaam);
}
```

**Step 3: Wijzig `verwijderProject`**

```java
@Transactional
public boolean verwijderProject(String naam) {
  kernbezwaarService.ruimAllesOpVoorProject(naam);
  consolidatieTaakRepository.deleteByProjectNaam(naam);
  extractieTaakRepository.deleteByProjectNaam(naam);
  return projectPoort.verwijderProject(naam);
}
```

**Step 4: Voer alle tests uit**

Run: `mvn test -pl app`
Expected: ALL TESTS PASS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java
git commit -m "feat: ProjectService cascade-verwijdering bij document- en projectdeletie"
```

---

### Task 6: Integratietest met Testcontainers

**Files:**
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/CascadeVerwijderingIT.java`

Deze test valideert dat de JPQL queries en DB cascades correct samenwerken.

**Step 1: Schrijf integratietest**

De test erft van `BaseBezwaarschriftenIntegrationTest` en gebruikt `@SpringBootTest`.

Setup: twee documenten ("doc-a.txt", "doc-b.txt") in project "testproject", elk met een extractie_taak + geextraheerd_bezwaar. Een thema met twee kernbezwaren: K1 heeft referenties naar beide documenten (gedeeld), K2 heeft alleen referenties naar doc-a (niet-gedeeld).

```java
@SpringBootTest
@ActiveProfiles("test")
class CascadeVerwijderingIT extends BaseBezwaarschriftenIntegrationTest {

  @Autowired private ProjectService projectService;
  @Autowired private ExtractieTaakRepository extractieTaakRepository;
  @Autowired private ThemaRepository themaRepository;
  @Autowired private KernbezwaarRepository kernbezwaarRepository;
  @Autowired private KernbezwaarReferentieRepository referentieRepository;
  @Autowired private KernbezwaarAntwoordRepository antwoordRepository;

  // Test data setup in @BeforeEach

  @Test
  void verwijderBezwaar_gedeeldKernbezwaar_verwijdertAlleenReferentieVanDocument() {
    // Verwijder doc-a → K1 verliest 1 referentie maar behoudt die van doc-b
    // K2 verliest alle referenties → K2 wordt verwijderd
    // Thema blijft bestaan want K1 is er nog
  }

  @Test
  void verwijderBezwaar_nietGedeeldKernbezwaar_verwijdertKernbezwaarEnAntwoord() {
    // Verwijder doc-a → K2 (alleen referenties naar doc-a) wordt verwijderd
    // K2's antwoord wordt ook verwijderd (DB cascade)
  }

  @Test
  void verwijderBezwaar_leegThema_wordtVerwijderd() {
    // Setup: thema met 1 kernbezwaar dat alleen referenties naar doc-a heeft
    // Verwijder doc-a → kernbezwaar verwijderd → thema verwijderd
  }

  @Test
  void verwijderProject_verwijdertAlleData() {
    // Verwijder heel project → alle thema's, kernbezwaren, referenties,
    // antwoorden, clustering-taken, consolidatie-taken, extractie-taken weg
    // Ander project ongewijzigd
  }
}
```

**Step 2: Voer de integratietest uit**

Run: `mvn verify -pl app -Dtest=CascadeVerwijderingIT -DfailIfNoTests=false`
Expected: ALL TESTS PASS

**Step 3: Commit**

```bash
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/CascadeVerwijderingIT.java
git commit -m "test: integratietest cascade-verwijdering met Testcontainers"
```

---

### Task 7: Volledige build + verificatie

**Step 1: Draai alle unit tests**

Run: `mvn test -pl app`
Expected: ALL TESTS PASS

**Step 2: Draai alle integratietests (Docker vereist)**

Run: `mvn verify -pl app`
Expected: ALL TESTS PASS

**Step 3: Compileer frontend (regressiecheck)**

Run: `cd webapp && npm run build && cd ..`
Expected: BUILD SUCCESS (geen frontend wijzigingen, maar regressiecheck)

**Step 4: Commit eventuele fixes**

Als er fixes nodig waren, commit die hier.
