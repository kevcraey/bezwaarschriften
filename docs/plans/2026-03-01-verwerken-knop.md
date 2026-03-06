# Verwerken-knop voor onafgeronde extracties — Implementatieplan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** De "Opnieuw proberen"-knop vervangen door een "Verwerken (N)"-knop die zowel TODO- als FOUT-documenten (her)start.

**Architecture:** De backend-service `ExtractieTaakService` krijgt een nieuwe methode `verwerkOnafgeronde()` die gefaalde taken reset en voor TODO-documenten nieuwe taken aanmaakt. De controller krijgt een nieuw endpoint `/extracties/verwerken`. De frontend past visibility-logica en API-call aan.

**Tech Stack:** Java 21, Spring Boot, JPA, vanilla JS web components

---

### Task 1: Backend — nieuwe service-methode `verwerkOnafgeronde`

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java`

**Step 1: Schrijf de falende test — gefaalde taken worden herstart**

In `ExtractieTaakServiceTest.java`, vervang de test `herplanGefaaldeTakenZetTerugNaarWachtend` en `herplanGefaaldeTakenGeeftNulAlsGeenFouten` door nieuwe tests. De service heeft nu een extra dependency op `ProjectService` nodig.

Voeg veld toe bovenaan de testklasse:

```java
@Mock
private ProjectService projectService;
```

Pas `setUp` aan:

```java
@BeforeEach
void setUp() {
  service = new ExtractieTaakService(repository, notificatie, projectService, 3, 3);
}
```

Schrijf de eerste test:

```java
@Test
void verwerkOnafgerondeHerstartGefaaldeTaken() {
  var taak1 = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.FOUT);
  taak1.setAantalPogingen(3);
  taak1.setMaxPogingen(3);
  taak1.setFoutmelding("Timeout");
  taak1.setAfgerondOp(Instant.now());

  when(repository.findByProjectNaamAndStatus("windmolens", ExtractieTaakStatus.FOUT))
      .thenReturn(List.of(taak1));
  when(projectService.geefBezwaren("windmolens"))
      .thenReturn(List.of(new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.FOUT)));

  int aantal = service.verwerkOnafgeronde("windmolens");

  assertThat(aantal).isEqualTo(1);
  assertThat(taak1.getStatus()).isEqualTo(ExtractieTaakStatus.WACHTEND);
  assertThat(taak1.getMaxPogingen()).isEqualTo(4);
  assertThat(taak1.getFoutmelding()).isNull();
  assertThat(taak1.getAfgerondOp()).isNull();
  assertThat(taak1.getVerwerkingGestartOp()).isNull();
  verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
}
```

**Step 2: Run test om te verifiëren dat hij faalt**

Run: `cd app && mvn test -pl . -Dtest=ExtractieTaakServiceTest#verwerkOnafgerondeHerstartGefaaldeTaken -Dspring.profiles.active=test`
Expected: FAIL — methode `verwerkOnafgeronde` bestaat niet, constructor mismatch

**Step 3: Schrijf de falende test — TODO-documenten worden ingepland**

```java
@Test
void verwerkOnafgerondeCreëertTakenVoorTodoDocumenten() {
  when(repository.findByProjectNaamAndStatus("windmolens", ExtractieTaakStatus.FOUT))
      .thenReturn(List.of());
  when(projectService.geefBezwaren("windmolens"))
      .thenReturn(List.of(
          new BezwaarBestand("nieuw-001.txt", BezwaarBestandStatus.TODO),
          new BezwaarBestand("klaar-001.txt", BezwaarBestandStatus.EXTRACTIE_KLAAR),
          new BezwaarBestand("foto.jpg", BezwaarBestandStatus.NIET_ONDERSTEUND)
      ));
  when(repository.save(any())).thenAnswer(i -> {
    var t = i.getArgument(0, ExtractieTaak.class);
    t.setId(10L);
    return t;
  });

  int aantal = service.verwerkOnafgeronde("windmolens");

  assertThat(aantal).isEqualTo(1);
  var captor = ArgumentCaptor.forClass(ExtractieTaak.class);
  verify(repository).save(captor.capture());
  var nieuweTaak = captor.getValue();
  assertThat(nieuweTaak.getProjectNaam()).isEqualTo("windmolens");
  assertThat(nieuweTaak.getBestandsnaam()).isEqualTo("nieuw-001.txt");
  assertThat(nieuweTaak.getStatus()).isEqualTo(ExtractieTaakStatus.WACHTEND);
  assertThat(nieuweTaak.getAantalPogingen()).isZero();
}
```

**Step 4: Schrijf de falende test — combinatie FOUT + TODO**

```java
@Test
void verwerkOnafgerondeCombinatieVanFoutEnTodo() {
  var foutTaak = maakTaak(1L, "windmolens", "fout-001.txt", ExtractieTaakStatus.FOUT);
  foutTaak.setAantalPogingen(3);
  foutTaak.setMaxPogingen(3);
  foutTaak.setFoutmelding("Error");
  foutTaak.setAfgerondOp(Instant.now());

  when(repository.findByProjectNaamAndStatus("windmolens", ExtractieTaakStatus.FOUT))
      .thenReturn(List.of(foutTaak));
  when(projectService.geefBezwaren("windmolens"))
      .thenReturn(List.of(
          new BezwaarBestand("fout-001.txt", BezwaarBestandStatus.FOUT),
          new BezwaarBestand("nieuw-001.txt", BezwaarBestandStatus.TODO)
      ));
  when(repository.save(any())).thenAnswer(i -> {
    var t = i.getArgument(0, ExtractieTaak.class);
    if (t.getId() == null) t.setId(10L);
    return t;
  });

  int aantal = service.verwerkOnafgeronde("windmolens");

  assertThat(aantal).isEqualTo(2);
}
```

**Step 5: Schrijf de falende test — nul als alles klaar is**

```java
@Test
void verwerkOnafgerondeGeeftNulAlsAllesKlaar() {
  when(repository.findByProjectNaamAndStatus("windmolens", ExtractieTaakStatus.FOUT))
      .thenReturn(List.of());
  when(projectService.geefBezwaren("windmolens"))
      .thenReturn(List.of(
          new BezwaarBestand("klaar-001.txt", BezwaarBestandStatus.EXTRACTIE_KLAAR)
      ));

  int aantal = service.verwerkOnafgeronde("windmolens");

  assertThat(aantal).isZero();
  verify(notificatie, org.mockito.Mockito.never()).taakGewijzigd(any());
}
```

**Step 6: Implementeer `verwerkOnafgeronde` in `ExtractieTaakService`**

Voeg `ProjectService` toe als constructor-parameter (na `notificatie`, voor `maxConcurrent`):

```java
private final ProjectService projectService;

public ExtractieTaakService(
    ExtractieTaakRepository repository,
    ExtractieNotificatie notificatie,
    ProjectService projectService,
    @Value("${bezwaarschriften.extractie.max-concurrent:3}") int maxConcurrent,
    @Value("${bezwaarschriften.extractie.max-pogingen:3}") int maxPogingen) {
  this.repository = repository;
  this.notificatie = notificatie;
  this.projectService = projectService;
  this.maxConcurrent = maxConcurrent;
  this.maxPogingen = maxPogingen;
}
```

Vervang `herplanGefaaldeTaken` door:

```java
@Transactional
public int verwerkOnafgeronde(String projectNaam) {
  // 1. Reset gefaalde taken
  var gefaaldeTaken = repository.findByProjectNaamAndStatus(projectNaam,
      ExtractieTaakStatus.FOUT);
  for (var taak : gefaaldeTaken) {
    taak.setMaxPogingen(taak.getMaxPogingen() + 1);
    taak.setStatus(ExtractieTaakStatus.WACHTEND);
    taak.setFoutmelding(null);
    taak.setVerwerkingGestartOp(null);
    taak.setAfgerondOp(null);
    repository.save(taak);
    notificatie.taakGewijzigd(ExtractieTaakDto.van(taak));
  }

  // 2. Maak taken aan voor TODO-documenten
  var todoBestanden = projectService.geefBezwaren(projectNaam).stream()
      .filter(b -> b.status() == BezwaarBestandStatus.TODO)
      .toList();
  for (var bestand : todoBestanden) {
    var taak = new ExtractieTaak();
    taak.setProjectNaam(projectNaam);
    taak.setBestandsnaam(bestand.bestandsnaam());
    taak.setStatus(ExtractieTaakStatus.WACHTEND);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(maxPogingen);
    taak.setAangemaaktOp(Instant.now());
    repository.save(taak);
    notificatie.taakGewijzigd(ExtractieTaakDto.van(taak));
  }

  int totaal = gefaaldeTaken.size() + todoBestanden.size();
  LOGGER.info("Verwerkt {} onafgeronde taken voor project '{}' ({} gefaald, {} todo)",
      totaal, projectNaam, gefaaldeTaken.size(), todoBestanden.size());
  return totaal;
}
```

**Step 7: Verwijder de oude `herplanGefaaldeTaken` methode**

Verwijder de methode `herplanGefaaldeTaken` en de bijhorende Javadoc (regels 183-205).

**Step 8: Verwijder oude tests**

Verwijder `herplanGefaaldeTakenZetTerugNaarWachtend` en `herplanGefaaldeTakenGeeftNulAlsGeenFouten` uit `ExtractieTaakServiceTest.java`.

**Step 9: Run alle tests**

Run: `cd app && mvn test -pl . -Dtest=ExtractieTaakServiceTest -Dspring.profiles.active=test`
Expected: PASS — alle tests groen

**Step 10: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java
git commit -m "feat: verwerkOnafgeronde vervangt herplanGefaaldeTaken — TODO + FOUT"
```

---

### Task 2: Backend — controller endpoint wijzigen

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieController.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieControllerTest.java`

**Step 1: Schrijf de falende test**

Vervang `retryHerplantGefaaldeTaken` in `ExtractieControllerTest.java`:

```java
@Test
void verwerkenPlantOnafgerondeTakenIn() throws Exception {
  when(extractieTaakService.verwerkOnafgeronde("windmolens")).thenReturn(5);

  mockMvc.perform(post("/api/v1/projects/windmolens/extracties/verwerken")
          .with(csrf()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.aantalIngepland").value(5));
}
```

**Step 2: Run test om te verifiëren dat hij faalt**

Run: `cd app && mvn test -pl . -Dtest=ExtractieControllerTest#verwerkenPlantOnafgerondeTakenIn -Dspring.profiles.active=test`
Expected: FAIL — endpoint bestaat niet

**Step 3: Implementeer het endpoint**

Vervang het retry-endpoint (regels 56-69) in `ExtractieController.java`:

```java
/**
 * Verwerkt alle onafgeronde extractie-taken voor het opgegeven project.
 * Herstart gefaalde taken en plant nieuwe taken in voor TODO-documenten.
 *
 * @param naam projectnaam
 * @return het aantal ingeplande taken
 */
@PostMapping("/{naam}/extracties/verwerken")
public ResponseEntity<VerwerkenResponse> verwerken(@PathVariable String naam) {
  int aantal = extractieTaakService.verwerkOnafgeronde(naam);
  return ResponseEntity.ok(new VerwerkenResponse(aantal));
}

/** Response DTO voor het verwerken-endpoint. */
record VerwerkenResponse(int aantalIngepland) {}
```

Verwijder `RetryResponse` record.

**Step 4: Verwijder oude test**

Verwijder `retryHerplantGefaaldeTaken` uit `ExtractieControllerTest.java`.

**Step 5: Run alle tests**

Run: `cd app && mvn test -pl . -Dtest=ExtractieControllerTest -Dspring.profiles.active=test`
Expected: PASS

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieController.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieControllerTest.java
git commit -m "feat: POST /extracties/verwerken vervangt /extracties/retry"
```

---

### Task 3: Frontend — knop aanpassen

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js`

**Step 1: Pas de knop-ID en tekst aan in de HTML-template**

Wijzig regel 39:

```html
<vl-button id="verwerken-knop" hidden>Verwerken</vl-button>
```

**Step 2: Pas `_koppelEventListeners` aan**

Vervang alle verwijzingen naar `retry-knop` door `verwerken-knop` (regels 190, 222-224, 238-243):

```javascript
const verwerkenKnop = this.shadowRoot && this.shadowRoot.querySelector('#verwerken-knop');
```

In het `selectie-gewijzigd` event (regels 222-224):

```javascript
if (verwerkenKnop) {
  const aantalTeVerwerken = this.__bezwaren.filter(
      (b) => b.status === 'fout' || b.status === 'todo').length;
  verwerkenKnop.hidden = heeftSelectie || aantalTeVerwerken === 0;
}
```

In de click handler (regels 238-243):

```javascript
if (verwerkenKnop) {
  verwerkenKnop.addEventListener('vl-click', () => {
    if (this.__bezig || !this.__geselecteerdProject) return;
    this._verwerkOnafgeronde(this.__geselecteerdProject);
  });
}
```

**Step 3: Vervang `_werkRetryKnopBij` door `_werkVerwerkenKnopBij`**

Vervang de methode (regels 402-410):

```javascript
_werkVerwerkenKnopBij() {
  const verwerkenKnop = this.shadowRoot && this.shadowRoot.querySelector('#verwerken-knop');
  if (!verwerkenKnop) return;
  const aantalTeVerwerken = this.__bezwaren.filter(
      (b) => b.status === 'fout' || b.status === 'todo').length;
  verwerkenKnop.hidden = aantalTeVerwerken === 0;
  if (aantalTeVerwerken > 0) {
    verwerkenKnop.textContent = `Verwerken (${aantalTeVerwerken})`;
  }
}
```

**Step 4: Vervang `_retryGefaaldeExtracties` door `_verwerkOnafgeronde`**

Vervang de methode (regels 339-362):

```javascript
_verwerkOnafgeronde(projectNaam) {
  this._verbergFout();
  this._zetBezig(true);

  fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/extracties/verwerken`, {
    method: 'POST',
  })
      .then((response) => {
        if (!response.ok) throw new Error('Verwerken mislukt');
        return response.json();
      })
      .then((data) => {
        if (data.aantalIngepland > 0) {
          this._toonToast('success',
              `${data.aantalIngepland} extractie(s) ingepland.`);
        }
      })
      .catch(() => {
        this._toonFout('Verwerken mislukt.');
      })
      .finally(() => {
        this._zetBezig(false);
      });
}
```

**Step 5: Vervang alle `_werkRetryKnopBij()` aanroepen door `_werkVerwerkenKnopBij()`**

Dit betreft 4 plekken:
- `_verwerkTaakUpdate` (regel 141)
- `_syncExtracties` (regel 162)
- `_laadBezwaren` (regel 292)

**Step 6: Verifieer handmatig in de browser**

1. Start de applicatie
2. Selecteer een project met TODO- en/of FOUT-documenten
3. Controleer: knop toont "Verwerken (N)" met correct aantal
4. Klik de knop — extracties starten
5. Controleer: knop verdwijnt zodra alles klaar is

**Step 7: Commit**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "feat: verwerken-knop vervangt retry-knop — TODO + FOUT"
```

---

### Task 4: Opruimen en afronden

**Step 1: Run volledige backend test suite**

Run: `cd app && mvn test -Dspring.profiles.active=test`
Expected: PASS — alle tests groen

**Step 2: Controleer of er geen verwijzingen naar het oude endpoint resteren**

Zoek in de codebase naar `retry`, `herplanGefaaldeTaken`, `RetryResponse`, `_retryGefaaldeExtracties`, `retry-knop`.

**Step 3: Commit (indien nodig)**

Indien er nog achtergebleven verwijzingen zijn, fix en commit.
