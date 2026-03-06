# Bulk bestandsverwijdering — fix race condition kernbezwaar cleanup

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix de race condition waarbij kernbezwaren achterblijven na het verwijderen van meerdere bestanden, door alle verwijderingen in één transactie af te handelen.

**Architecture:** Nieuw bulk DELETE endpoint op `ProjectController` dat een lijst bestandsnamen accepteert. `ProjectService.verwijderBezwaren` verwijdert alle extractie-taken per bestand, doet daarna één cleanup-pass via `KernbezwaarService`. Frontend switcht naar het bulk endpoint.

**Tech Stack:** Java 21, Spring Boot 3.x, JPA/JPQL, Lit web components

**Root cause:** Frontend stuurt parallelle DELETE-requests per bestand. Elke request draait in een eigen `@Transactional`. Door READ_COMMITTED isolation ziet elke transactie nog de referenties van de andere bestanden (nog niet gecommit), waardoor `deleteZonderReferenties` de gedeelde kernbezwaren niet opruimt.

---

### Task 1: Unit test voor `ProjectService.verwijderBezwaren`

**Files:**
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java`

**Step 1: Schrijf de falende test**

Voeg toe aan `ProjectServiceTest`:

```java
@Test
void verwijderBezwaren_verwijdertAlleBestandenEnRuimtKernbezwaarDataOp() {
  var bestandsnamen = List.of("bezwaar-001.txt", "bezwaar-002.txt", "bezwaar-003.txt");
  when(projectPoort.verwijderBestand(eq("windmolens"), anyString())).thenReturn(true);

  int aantalVerwijderd = service.verwijderBezwaren("windmolens", bestandsnamen);

  assertThat(aantalVerwijderd).isEqualTo(3);

  // Extractie-taken per bestand verwijderd
  for (String naam : bestandsnamen) {
    verify(extractieTaakRepository).deleteByProjectNaamAndBestandsnaam("windmolens", naam);
  }

  // Kernbezwaar cleanup exact 1x aangeroepen (na alle verwijderingen)
  verify(kernbezwaarService, times(1)).ruimOpNaBestandenVerwijdering("windmolens", bestandsnamen);

  // Bestanden verwijderd
  for (String naam : bestandsnamen) {
    verify(projectPoort).verwijderBestand("windmolens", naam);
  }
}

@Test
void verwijderBezwaren_teltEnkelSuccesvolVerwijderdeBestandenMee() {
  when(projectPoort.verwijderBestand("windmolens", "bestaat.txt")).thenReturn(true);
  when(projectPoort.verwijderBestand("windmolens", "bestaat-niet.txt")).thenReturn(false);

  int aantalVerwijderd = service.verwijderBezwaren("windmolens",
      List.of("bestaat.txt", "bestaat-niet.txt"));

  assertThat(aantalVerwijderd).isEqualTo(1);
}
```

**Step 2: Run test, verwacht FAIL**

Run: `mvn test -pl app -Dtest=ProjectServiceTest#verwijderBezwaren_verwijdertAlleBestandenEnRuimtKernbezwaarDataOp`
Expected: FAIL — methode `verwijderBezwaren` bestaat nog niet.

**Step 3: Implementeer `ProjectService.verwijderBezwaren`**

Voeg toe aan `ProjectService` (na de bestaande `verwijderBezwaar` methode):

```java
/**
 * Verwijdert meerdere bezwaarbestanden in één transactie en ruimt kernbezwaar-data op.
 *
 * @param projectNaam Naam van het project
 * @param bestandsnamen Lijst van te verwijderen bestandsnamen
 * @return aantal succesvol verwijderde bestanden
 */
@Transactional
public int verwijderBezwaren(String projectNaam, List<String> bestandsnamen) {
  int aantalVerwijderd = 0;
  for (String bestandsnaam : bestandsnamen) {
    extractieTaakRepository.deleteByProjectNaamAndBestandsnaam(projectNaam, bestandsnaam);
    if (projectPoort.verwijderBestand(projectNaam, bestandsnaam)) {
      aantalVerwijderd++;
    }
  }
  kernbezwaarService.ruimOpNaBestandenVerwijdering(projectNaam, bestandsnamen);
  return aantalVerwijderd;
}
```

**Step 4: Run test, verwacht FAIL** (want `ruimOpNaBestandenVerwijdering` bestaat nog niet)

**Step 5: Commit (als test al zou slagen na Task 2)**

---

### Task 2: `KernbezwaarService.ruimOpNaBestandenVerwijdering`

**Files:**
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java`

**Step 1: Schrijf de falende test**

Voeg toe aan `KernbezwaarServiceTest` (zoek bestaand testpatroon en volg dat):

```java
@Test
void ruimOpNaBestandenVerwijdering_verwijdertReferentiesVoorAlleBestandenDanOrphanedData() {
  var bestandsnamen = List.of("doc-a.txt", "doc-b.txt");

  service.ruimOpNaBestandenVerwijdering("testproject", bestandsnamen);

  // Referenties voor elk bestand verwijderd
  verify(referentieRepository).deleteByBestandsnaamAndProjectNaam("doc-a.txt", "testproject");
  verify(referentieRepository).deleteByBestandsnaamAndProjectNaam("doc-b.txt", "testproject");

  // Orphaned data exact 1x opgeruimd (na alle referentie-verwijderingen)
  verify(kernbezwaarRepository).deleteZonderReferenties("testproject");
  verify(themaRepository).deleteZonderKernbezwaren("testproject");
  verify(clusteringTaakRepository).deleteZonderThema("testproject");
}
```

**Step 2: Run test, verwacht FAIL**

Run: `mvn test -pl app -Dtest=KernbezwaarServiceTest#ruimOpNaBestandenVerwijdering_verwijdertReferentiesVoorAlleBestandenDanOrphanedData`

**Step 3: Implementeer `KernbezwaarService.ruimOpNaBestandenVerwijdering`**

Voeg toe aan `KernbezwaarService`:

```java
/**
 * Ruimt kernbezwaar-data op na verwijdering van meerdere documenten.
 * Verwijdert eerst alle referenties, daarna lege kernbezwaren, thema's en clustering-taken.
 *
 * @param projectNaam naam van het project
 * @param bestandsnamen lijst van verwijderde bestandsnamen
 */
public void ruimOpNaBestandenVerwijdering(String projectNaam, List<String> bestandsnamen) {
  for (String bestandsnaam : bestandsnamen) {
    referentieRepository.deleteByBestandsnaamAndProjectNaam(bestandsnaam, projectNaam);
  }
  kernbezwaarRepository.deleteZonderReferenties(projectNaam);
  themaRepository.deleteZonderKernbezwaren(projectNaam);
  clusteringTaakRepository.deleteZonderThema(projectNaam);
}
```

**Step 4: Run beide tests**

Run: `mvn test -pl app -Dtest="KernbezwaarServiceTest#ruimOpNaBestandenVerwijdering*,ProjectServiceTest#verwijderBezwaren*"`
Expected: PASS

**Step 5: Commit**

```bash
git commit -m "feat: ProjectService.verwijderBezwaren en KernbezwaarService.ruimOpNaBestandenVerwijdering"
```

---

### Task 3: Integratietest — bulk verwijdering in `CascadeVerwijderingIntegrationTest`

**Files:**
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/CascadeVerwijderingIntegrationTest.java`

**Step 1: Schrijf de falende integratietest**

Voeg een nieuw scenario toe (gebruik bestaande helper-methoden uit de test class):

```java
// --- Scenario: Alle bestanden tegelijk verwijderen ruimt alle kernbezwaren op ---

@Test
@DisplayName("Bulk verwijdering van alle bestanden ruimt alle kernbezwaren, thema's en antwoorden op")
void bulkVerwijderingRuimtAlleKernbezwarenOp() {
  // Arrange: 3 documenten met gedeelde en niet-gedeelde kernbezwaren
  var taakA = maakExtractieTaak("testproject", "doc-a.txt");
  var taakB = maakExtractieTaak("testproject", "doc-b.txt");
  var taakC = maakExtractieTaak("testproject", "doc-c.txt");

  var themaMilieu = maakThema("testproject", "milieu");

  // K1: gedeeld over alle documenten
  var k1 = maakKernbezwaar(themaMilieu.getId(), "Geluidshinder gedeeld");
  maakReferentie(k1.getId(), "doc-a.txt", "geluid A");
  maakReferentie(k1.getId(), "doc-b.txt", "geluid B");
  maakReferentie(k1.getId(), "doc-c.txt", "geluid C");
  maakAntwoord(k1.getId(), "<p>Antwoord geluid</p>");

  // K2: gedeeld over doc-a en doc-b
  var k2 = maakKernbezwaar(themaMilieu.getId(), "Fijnstof gedeeld A+B");
  maakReferentie(k2.getId(), "doc-a.txt", "fijnstof A");
  maakReferentie(k2.getId(), "doc-b.txt", "fijnstof B");

  var themaVerkeer = maakThema("testproject", "verkeer");

  // K3: enkel doc-c
  var k3 = maakKernbezwaar(themaVerkeer.getId(), "Verkeersoverlast enkel C");
  maakReferentie(k3.getId(), "doc-c.txt", "verkeer C");
  maakAntwoord(k3.getId(), "<p>Antwoord verkeer</p>");

  var clusteringMilieu = maakClusteringTaak("testproject", "milieu");
  var clusteringVerkeer = maakClusteringTaak("testproject", "verkeer");

  // Act: bulk verwijdering van alle bestanden in 1 transactie
  int verwijderd = projectService.verwijderBezwaren("testproject",
      List.of("doc-a.txt", "doc-b.txt", "doc-c.txt"));

  // Assert: alle bestanden verwijderd
  assertThat(verwijderd).isEqualTo(3);

  // Assert: alle kernbezwaren, referenties en antwoorden opgeruimd
  assertThat(kernbezwaarRepository.findById(k1.getId())).isEmpty();
  assertThat(kernbezwaarRepository.findById(k2.getId())).isEmpty();
  assertThat(kernbezwaarRepository.findById(k3.getId())).isEmpty();
  assertThat(antwoordRepository.findById(k1.getId())).isEmpty();
  assertThat(antwoordRepository.findById(k3.getId())).isEmpty();
  assertThat(referentieRepository.findByProjectNaam("testproject")).isEmpty();

  // Assert: alle thema's en clustering-taken opgeruimd
  assertThat(themaRepository.findByProjectNaam("testproject")).isEmpty();
  assertThat(clusteringTaakRepository.findByProjectNaam("testproject")).isEmpty();

  // Assert: extractie-taken ook opgeruimd
  assertThat(extractieTaakRepository.findByProjectNaam("testproject")).isEmpty();
}

@Test
@DisplayName("Bulk verwijdering van subset bestanden behoudt kernbezwaren met overblijvende referenties")
void bulkVerwijderingBehoudtKernbezwarenMetOverblijvendeReferenties() {
  // Arrange: 3 documenten, verwijder er 2
  var taakA = maakExtractieTaak("testproject", "doc-a.txt");
  var taakB = maakExtractieTaak("testproject", "doc-b.txt");
  var taakC = maakExtractieTaak("testproject", "doc-c.txt");

  var thema = maakThema("testproject", "milieu");

  // K1: gedeeld over alle 3 — moet blijven met enkel doc-c referentie
  var k1 = maakKernbezwaar(thema.getId(), "Geluid gedeeld");
  maakReferentie(k1.getId(), "doc-a.txt", "geluid A");
  maakReferentie(k1.getId(), "doc-b.txt", "geluid B");
  maakReferentie(k1.getId(), "doc-c.txt", "geluid C");

  // K2: enkel doc-a en doc-b — moet verdwijnen
  var k2 = maakKernbezwaar(thema.getId(), "Fijnstof A+B");
  maakReferentie(k2.getId(), "doc-a.txt", "fijnstof A");
  maakReferentie(k2.getId(), "doc-b.txt", "fijnstof B");
  maakAntwoord(k2.getId(), "<p>Fijnstof antwoord</p>");

  // Act: verwijder doc-a en doc-b (doc-c blijft)
  int verwijderd = projectService.verwijderBezwaren("testproject",
      List.of("doc-a.txt", "doc-b.txt"));

  // Assert
  assertThat(verwijderd).isEqualTo(2);

  // K1 blijft met enkel doc-c referentie
  assertThat(kernbezwaarRepository.findById(k1.getId())).isPresent();
  var k1Refs = referentieRepository.findByKernbezwaarIdIn(List.of(k1.getId()));
  assertThat(k1Refs).hasSize(1);
  assertThat(k1Refs.get(0).getBestandsnaam()).isEqualTo("doc-c.txt");

  // K2 is verwijderd (geen referenties meer)
  assertThat(kernbezwaarRepository.findById(k2.getId())).isEmpty();
  assertThat(antwoordRepository.findById(k2.getId())).isEmpty();

  // Thema blijft (K1 zit er nog in)
  assertThat(themaRepository.findById(thema.getId())).isPresent();
}
```

**Step 2: Run integratietests**

Run: `mvn verify -pl app -Dtest=CascadeVerwijderingIntegrationTest`
Expected: PASS (Docker moet draaien voor Testcontainers)

**Step 3: Commit**

```bash
git commit -m "test: integratietests voor bulk bestandsverwijdering"
```

---

### Task 4: REST endpoint `DELETE /api/v1/projects/{naam}/bezwaren`

**Files:**
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectControllerTest.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java`

**Step 1: Schrijf de falende controller test**

Voeg toe aan `ProjectControllerTest`:

```java
@Test
void verwijdertMeerdereBezwaren() throws Exception {
  when(projectService.verwijderBezwaren("windmolens", List.of("doc-a.txt", "doc-b.txt")))
      .thenReturn(2);

  mockMvc.perform(delete("/api/v1/projects/windmolens/bezwaren")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"bestandsnamen\":[\"doc-a.txt\",\"doc-b.txt\"]}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.aantalVerwijderd").value(2));
}

@Test
void verwijderBezwaren_geeft400BijLegeOfOntbrekendeLijst() throws Exception {
  mockMvc.perform(delete("/api/v1/projects/windmolens/bezwaren")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"bestandsnamen\":[]}"))
      .andExpect(status().isBadRequest());
}
```

**Step 2: Run test, verwacht FAIL**

Run: `mvn test -pl app -Dtest="ProjectControllerTest#verwijdertMeerdereBezwaren"`

**Step 3: Implementeer endpoint**

Voeg toe aan `ProjectController`:

Request/response records (inner classes of apart bestand, volg bestaand patroon):

```java
record VerwijderBezwarenRequest(List<String> bestandsnamen) {}
record VerwijderBezwarenResponse(int aantalVerwijderd) {}
```

Endpoint:

```java
@DeleteMapping("/{naam}/bezwaren")
public ResponseEntity<?> verwijderBezwaren(
    @PathVariable String naam,
    @RequestBody VerwijderBezwarenRequest request) {
  if (request.bestandsnamen() == null || request.bestandsnamen().isEmpty()) {
    return ResponseEntity.badRequest().build();
  }
  int aantalVerwijderd = projectService.verwijderBezwaren(naam, request.bestandsnamen());
  return ResponseEntity.ok(new VerwijderBezwarenResponse(aantalVerwijderd));
}
```

**Let op:** Het bestaande single-delete endpoint (`DELETE /{naam}/bezwaren/{bestandsnaam}`) blijft behouden voor backwards compatibility.

**Step 4: Run tests**

Run: `mvn test -pl app -Dtest="ProjectControllerTest"`
Expected: PASS

**Step 5: Commit**

```bash
git commit -m "feat: bulk DELETE endpoint voor bestandsverwijdering"
```

---

### Task 5: Frontend — gebruik bulk endpoint

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js`

**Step 1: Pas `_verwijderBestanden` aan**

Vervang de bestaande methode (regel ~611-633):

```javascript
_verwijderBestanden(bestandsnamen) {
  if (!bestandsnamen || bestandsnamen.length === 0 || !this.__geselecteerdProject) return;

  this._zetBezig(true);
  this._verbergFout();

  fetch(`/api/v1/projects/${encodeURIComponent(this.__geselecteerdProject)}/bezwaren`, {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ bestandsnamen }),
  })
    .then((response) => {
      if (!response.ok) throw new Error('Verwijdering mislukt');
      return response.json();
    })
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

**Step 2: Build en test**

Run: `cd webapp && npm run build`
Run: `mvn process-resources -pl webapp -Denforcer.skip=true`

**Step 3: Commit**

```bash
git commit -m "fix: frontend gebruikt bulk delete endpoint voor bestandsverwijdering"
```

---

### Task 6: Cypress test

**Files:**
- Zoek bestaande Cypress tests voor de project-selectie component en voeg een test toe.

**Step 1: Schrijf Cypress test**

Zoek het bestaande Cypress-testbestand voor `bezwaarschriften-project-selectie` (of het dichtst gerelateerde testbestand). Voeg een test toe die valideert:

- Bij verwijdering van meerdere bestanden wordt 1 bulk DELETE request verstuurd (niet meerdere individuele requests)
- De `Content-Type` is `application/json`
- De body bevat `{ bestandsnamen: [...] }`

**Step 2: Run Cypress test**

Run: `cd webapp && npm test`

**Step 3: Commit**

```bash
git commit -m "test: Cypress test voor bulk bestandsverwijdering"
```

---

### Task 7: Build en volledige testsuite

**Step 1: Backend build + tests**

Run: `mvn clean install -pl app`
Expected: BUILD SUCCESS, alle tests groen.

**Step 2: Frontend build + tests**

Run: `cd webapp && npm run build && npm test`
Expected: Alles groen.

**Step 3: Integratietests**

Run: `mvn verify -pl app`
Expected: Alle integratietests groen (Docker moet draaien).

---

## Test- en verificatieplan

| Scenario | Verwacht resultaat | Test |
|---|---|---|
| Verwijder 1 bestand via bulk endpoint | Kernbezwaren met enkel dat bestand verdwijnen, gedeelde blijven | Integratietest Task 3 |
| Verwijder alle bestanden via bulk endpoint | Alle kernbezwaren, thema's, antwoorden verdwijnen | Integratietest Task 3 |
| Verwijder subset bestanden | Gedeelde kernbezwaren met overblijvende referenties blijven | Integratietest Task 3 |
| Lege bestandsnamen-lijst | 400 Bad Request | Controller test Task 4 |
| Frontend stuurt 1 bulk request i.p.v. meerdere | Eén fetch-call naar bulk endpoint | Cypress test Task 6 |
| Bestaand single-delete endpoint blijft werken | 204 No Content | Bestaande tests ongewijzigd |
