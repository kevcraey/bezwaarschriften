# Manueel Bezwaar Toevoegen — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Gebruikers kunnen in het side-panel manueel bezwaren toevoegen (samenvatting + passage) met server-side passage-validatie, en manuele bezwaren verwijderen.

**Architecture:** Uitbreiding van de bestaande `geextraheerd_bezwaar` entiteit met een `manueel` boolean. Twee nieuwe REST endpoints (POST/DELETE). Frontend krijgt inline formulier in het side-panel en verwijder-knoppen bij manuele bezwaren.

**Tech Stack:** Java 21, Spring Boot 3.4, JPA/Hibernate, Liquibase, Lit Web Components (@domg-wc), Cypress

**Design doc:** `docs/plans/2026-03-02-manueel-bezwaar-toevoegen-design.md`

---

### Task 1: Liquibase migratie

**Files:**
- Create: `app/src/main/resources/config/liquibase/changelog/20260302-manueel-bezwaar.xml`
- Modify: `app/src/main/resources/config/liquibase/master.xml:12`

**Step 1: Maak migratie-bestand aan**

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">

  <changeSet id="20260302-manueel-bezwaar-1" author="claude">
    <addColumn tableName="geextraheerd_bezwaar">
      <column name="manueel" type="boolean" defaultValueBoolean="false">
        <constraints nullable="false"/>
      </column>
    </addColumn>
  </changeSet>

  <changeSet id="20260302-manueel-bezwaar-2" author="claude">
    <addColumn tableName="extractie_taak">
      <column name="heeft_manueel" type="boolean" defaultValueBoolean="false">
        <constraints nullable="false"/>
      </column>
    </addColumn>
  </changeSet>

</databaseChangeLog>
```

**Step 2: Voeg toe aan master.xml**

Na regel 12 (na de passage-validatie include):
```xml
  <include file="config/liquibase/changelog/20260302-manueel-bezwaar.xml"/>
```

**Step 3: Commit**

```bash
git add app/src/main/resources/config/liquibase/changelog/20260302-manueel-bezwaar.xml \
       app/src/main/resources/config/liquibase/master.xml
git commit -m "chore: liquibase migratie voor manueel bezwaar kolommen"
```

---

### Task 2: Entity-updates

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarEntiteit.java:30-31`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaak.java:47-48`

**Step 1: Voeg `manueel` veld toe aan GeextraheerdBezwaarEntiteit**

Na het `passageGevonden` veld (regel 31), voeg toe:

```java
  @Column(name = "manueel", nullable = false)
  private boolean manueel = false;
```

En de getter/setter (na de `passageGevonden` getter/setter, rond regel 79):

```java
  public boolean isManueel() {
    return manueel;
  }

  public void setManueel(boolean manueel) {
    this.manueel = manueel;
  }
```

**Step 2: Voeg `heeftManueel` veld toe aan ExtractieTaak**

Na het `heeftOpmerkingen` veld (regel 48), voeg toe:

```java
  @Column(name = "heeft_manueel", nullable = false)
  private boolean heeftManueel = false;
```

En de getter/setter (na de `heeftOpmerkingen` getter/setter, rond regel 136):

```java
  public boolean isHeeftManueel() {
    return heeftManueel;
  }

  public void setHeeftManueel(boolean heeftManueel) {
    this.heeftManueel = heeftManueel;
  }
```

**Step 3: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarEntiteit.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaak.java
git commit -m "feat: manueel en heeftManueel velden op entiteiten"
```

---

### Task 3: DTO- en Repository-updates

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieDetailDto.java:10`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestand.java:12-17`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakDto.java:18-37`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java:360-365`

**Step 1: Breid ExtractieDetailDto.BezwaarDetail uit met `id` en `manueel`**

Vervang regel 10:
```java
  public record BezwaarDetail(String samenvatting, String passage, boolean passageGevonden) { }
```
door:
```java
  public record BezwaarDetail(Long id, String samenvatting, String passage,
      boolean passageGevonden, boolean manueel) { }
```

**Step 2: Update `geefExtractieDetails` in ExtractieTaakService**

Vervang de mapping in regels 360-364:
```java
    var details = bezwaren.stream()
        .map(b -> new ExtractieDetailDto.BezwaarDetail(
            b.getSamenvatting(),
            passageMap.getOrDefault(b.getPassageNr(), ""),
            b.isPassageGevonden()))
        .toList();
```
door:
```java
    var details = bezwaren.stream()
        .map(b -> new ExtractieDetailDto.BezwaarDetail(
            b.getId(),
            b.getSamenvatting(),
            passageMap.getOrDefault(b.getPassageNr(), ""),
            b.isPassageGevonden(),
            b.isManueel()))
        .toList();
```

**Step 3: Breid BezwaarBestand uit met `heeftManueel`**

Vervang het record (regels 12-17):
```java
public record BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status,
    Integer aantalWoorden, Integer aantalBezwaren, boolean heeftOpmerkingen) {

  public BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status) {
    this(bestandsnaam, status, null, null, false);
  }
}
```
door:
```java
public record BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status,
    Integer aantalWoorden, Integer aantalBezwaren, boolean heeftOpmerkingen,
    boolean heeftManueel) {

  public BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status) {
    this(bestandsnaam, status, null, null, false, false);
  }
}
```

**Step 4: Breid ExtractieTaakDto uit met `heeftManueel`**

Vervang het record (regels 18-22):
```java
public record ExtractieTaakDto(
    Long id, String projectNaam, String bestandsnaam, String status,
    int aantalPogingen, String aangemaaktOp, String verwerkingGestartOp,
    Integer aantalWoorden, Integer aantalBezwaren, String foutmelding,
    boolean heeftOpmerkingen) {
```
door:
```java
public record ExtractieTaakDto(
    Long id, String projectNaam, String bestandsnaam, String status,
    int aantalPogingen, String aangemaaktOp, String verwerkingGestartOp,
    Integer aantalWoorden, Integer aantalBezwaren, String foutmelding,
    boolean heeftOpmerkingen, boolean heeftManueel) {
```

Update de `van()` methode (regels 30-38) — voeg `taak.isHeeftManueel()` als laatste argument toe:
```java
  static ExtractieTaakDto van(ExtractieTaak taak) {
    return new ExtractieTaakDto(
        taak.getId(), taak.getProjectNaam(), taak.getBestandsnaam(),
        statusNaarString(taak.getStatus()), taak.getAantalPogingen(),
        taak.getAangemaaktOp().toString(),
        taak.getVerwerkingGestartOp() != null ? taak.getVerwerkingGestartOp().toString() : null,
        taak.getAantalWoorden(), taak.getAantalBezwaren(), taak.getFoutmelding(),
        taak.isHeeftOpmerkingen(), taak.isHeeftManueel());
  }
```

**Step 5: Fix alle compileerfouten door de DTO-wijzigingen**

Zoek alle plekken die `ExtractieTaakDto` en `BezwaarBestand` aanmaken en voeg het extra `heeftManueel` argument toe. Dit zijn met name de testbestanden:
- `ExtractieControllerTest.java` — elke `new ExtractieTaakDto(...)` krijgt `, false` als extra argument
- `ExtractieTaakServiceTest.java` — elke `new BezwaarBestand(...)` met 5 argumenten krijgt `, false`
- `ExtractieControllerTest.java` — elke `new ExtractieDetailDto.BezwaarDetail(...)` krijgt `id` en `manueel` extra

In `ExtractieControllerTest`:
- Regel 39-42: voeg `, false` toe aan eind van elke `ExtractieTaakDto`
- Regel 60-62: voeg `, false` toe
- Regel 95-99: update `BezwaarDetail` constructors: `new ExtractieDetailDto.BezwaarDetail(1L, "Geluidshinder...", "De geluids...", true, false)`
- Regel 134-138: idem

In `ExtractieTaakServiceTest`:
- Regel 208: `new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.FOUT)` — deze gebruikt de 2-arg constructor, die al gefixt is

Zoek ook in andere bestanden die deze records gebruiken (bv. `ProjectService`, `ExtractieWorkerTest`).

**Step 6: Bouw en run tests**

```bash
cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften
./mvnw compile -pl app -Denforcer.skip=true
./mvnw test -pl app -Denforcer.skip=true
```

Expected: compilatie slaagt, alle bestaande tests passen.

**Step 7: Commit**

```bash
git add -A
git commit -m "feat: DTO-uitbreidingen voor manueel bezwaar (id, manueel, heeftManueel)"
```

---

### Task 4: Service — voegManueelBezwaarToe (TDD)

**Files:**
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractiePassageRepository.java`

**Step 1: Voeg repository query toe**

In `ExtractiePassageRepository.java`, voeg toe:

```java
  Optional<ExtractiePassageEntiteit> findTopByTaakIdOrderByPassageNrDesc(Long taakId);
```

**Step 2: Schrijf de falende tests**

Voeg deze tests toe onderaan `ExtractieTaakServiceTest.java` (vóór de `maakTaak` helper):

```java
  @Test
  void voegManueelBezwaarToeMetGeldigePassage() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.KLAAR);
    when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
        "windmolens", "bezwaar-001.txt")).thenReturn(Optional.of(taak));

    var pad = Path.of("/tmp/windmolens/bezwaren/bezwaar-001.txt");
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar-001.txt")).thenReturn(pad);
    when(ingestiePoort.leesBestand(pad)).thenReturn(
        new Brondocument("Dit is de volledige documenttekst met relevante inhoud.",
            "bezwaar-001.txt", pad.toString(), Instant.now()));

    when(passageRepository.findTopByTaakIdOrderByPassageNrDesc(1L))
        .thenReturn(Optional.of(maakPassage(1L, 3)));
    when(passageRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(bezwaarRepository.save(any())).thenAnswer(i -> {
      var b = i.getArgument(0, GeextraheerdBezwaarEntiteit.class);
      b.setId(10L);
      return b;
    });
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    var detail = service.voegManueelBezwaarToe(
        "windmolens", "bezwaar-001.txt", "Samenvatting test", "volledige documenttekst");

    assertThat(detail).isNotNull();
    assertThat(detail.id()).isEqualTo(10L);
    assertThat(detail.samenvatting()).isEqualTo("Samenvatting test");
    assertThat(detail.manueel()).isTrue();
    assertThat(detail.passageGevonden()).isTrue();

    var bezwaarCaptor = ArgumentCaptor.forClass(GeextraheerdBezwaarEntiteit.class);
    verify(bezwaarRepository).save(bezwaarCaptor.capture());
    assertThat(bezwaarCaptor.getValue().isManueel()).isTrue();
    assertThat(bezwaarCaptor.getValue().getPassageNr()).isEqualTo(4);

    assertThat(taak.isHeeftManueel()).isTrue();
    assertThat(taak.getAantalBezwaren()).isEqualTo(1);
  }

  @Test
  void voegManueelBezwaarToeGooitExceptieBijOngeldigePassage() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.KLAAR);
    when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
        "windmolens", "bezwaar-001.txt")).thenReturn(Optional.of(taak));

    var pad = Path.of("/tmp/windmolens/bezwaren/bezwaar-001.txt");
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar-001.txt")).thenReturn(pad);
    when(ingestiePoort.leesBestand(pad)).thenReturn(
        new Brondocument("Dit is de volledige documenttekst.",
            "bezwaar-001.txt", pad.toString(), Instant.now()));

    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
        service.voegManueelBezwaarToe(
            "windmolens", "bezwaar-001.txt", "Samenvatting", "Deze tekst staat er niet in"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Passage komt niet voor");
  }

  @Test
  void voegManueelBezwaarToeGooitExceptieBijLegeSamenvatting() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
        service.voegManueelBezwaarToe("windmolens", "bezwaar-001.txt", "", "passage"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void voegManueelBezwaarToeGooitExceptieBijLegePassage() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
        service.voegManueelBezwaarToe("windmolens", "bezwaar-001.txt", "samenvatting", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void voegManueelBezwaarToeGooitExceptieAlsTaakNietKlaar() {
    var taak = maakTaak(1L, "windmolens", "bezig.txt", ExtractieTaakStatus.BEZIG);
    when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
        "windmolens", "bezig.txt")).thenReturn(Optional.of(taak));

    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
        service.voegManueelBezwaarToe("windmolens", "bezig.txt", "Samenvatting", "passage"))
        .isInstanceOf(IllegalArgumentException.class);
  }
```

Voeg de helper-methode toe (bij de `maakTaak` helper):
```java
  private ExtractiePassageEntiteit maakPassage(Long taakId, int passageNr) {
    var p = new ExtractiePassageEntiteit();
    p.setTaakId(taakId);
    p.setPassageNr(passageNr);
    p.setTekst("Passage tekst");
    return p;
  }
```

**Step 3: Run tests om te verifiëren dat ze falen**

```bash
./mvnw test -pl app -Denforcer.skip=true -Dtest=ExtractieTaakServiceTest
```

Expected: FAIL — `voegManueelBezwaarToe` methode bestaat nog niet.

**Step 4: Implementeer voegManueelBezwaarToe**

Voeg deze methode toe aan `ExtractieTaakService.java` (na `geefExtractieDetails`, rond regel 368):

```java
  /**
   * Voegt een manueel bezwaar toe aan een afgeronde extractietaak.
   *
   * <p>Valideert dat de passage exact voorkomt in het originele document (na normalisatie).
   *
   * @param projectNaam naam van het project
   * @param bestandsnaam naam van het bestand
   * @param samenvatting samenvatting van het bezwaar
   * @param passageTekst exacte passage uit het originele document
   * @return het aangemaakte bezwaar als BezwaarDetail
   * @throws IllegalArgumentException bij ongeldige invoer of niet-gevonden passage
   */
  @Transactional
  public ExtractieDetailDto.BezwaarDetail voegManueelBezwaarToe(
      String projectNaam, String bestandsnaam, String samenvatting, String passageTekst) {

    if (samenvatting == null || samenvatting.isBlank()) {
      throw new IllegalArgumentException("Samenvatting mag niet leeg zijn");
    }
    if (passageTekst == null || passageTekst.isBlank()) {
      throw new IllegalArgumentException("Passage mag niet leeg zijn");
    }

    var taak = repository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(projectNaam, bestandsnaam)
        .orElseThrow(() -> new IllegalArgumentException("Geen taak gevonden voor: " + bestandsnaam));

    if (taak.getStatus() != ExtractieTaakStatus.KLAAR) {
      throw new IllegalArgumentException("Taak is niet afgerond: " + taak.getStatus());
    }

    // Passage-validatie: exacte match na normalisatie
    var pad = projectPoort.geefBestandsPad(projectNaam, bestandsnaam);
    var brondocument = ingestiePoort.leesBestand(pad);
    var genormaliseerdeDocument = passageValidator.normaliseer(brondocument.tekst());
    var genormaliseerdePassage = passageValidator.normaliseer(passageTekst);

    if (!genormaliseerdeDocument.contains(genormaliseerdePassage)) {
      throw new IllegalArgumentException("Passage komt niet voor in het originele document");
    }

    // Bepaal volgend passageNr
    int volgendPassageNr = passageRepository.findTopByTaakIdOrderByPassageNrDesc(taak.getId())
        .map(p -> p.getPassageNr() + 1)
        .orElse(1);

    // Sla passage op
    var passageEntiteit = new ExtractiePassageEntiteit();
    passageEntiteit.setTaakId(taak.getId());
    passageEntiteit.setPassageNr(volgendPassageNr);
    passageEntiteit.setTekst(passageTekst);
    passageRepository.save(passageEntiteit);

    // Sla bezwaar op
    var bezwaarEntiteit = new GeextraheerdBezwaarEntiteit();
    bezwaarEntiteit.setTaakId(taak.getId());
    bezwaarEntiteit.setPassageNr(volgendPassageNr);
    bezwaarEntiteit.setSamenvatting(samenvatting);
    bezwaarEntiteit.setCategorie("overig");
    bezwaarEntiteit.setPassageGevonden(true);
    bezwaarEntiteit.setManueel(true);
    var opgeslagen = bezwaarRepository.save(bezwaarEntiteit);

    // Werk taak bij
    taak.setHeeftManueel(true);
    int huidigAantal = taak.getAantalBezwaren() != null ? taak.getAantalBezwaren() : 0;
    taak.setAantalBezwaren(huidigAantal + 1);
    repository.save(taak);
    notificatie.taakGewijzigd(ExtractieTaakDto.van(taak));

    return new ExtractieDetailDto.BezwaarDetail(
        opgeslagen.getId(), samenvatting, passageTekst, true, true);
  }
```

**Step 5: Run tests**

```bash
./mvnw test -pl app -Denforcer.skip=true -Dtest=ExtractieTaakServiceTest
```

Expected: alle tests PASS.

**Step 6: Commit**

```bash
git add -A
git commit -m "feat: voegManueelBezwaarToe service-methode met passage-validatie"
```

---

### Task 5: Service — verwijderManueelBezwaar (TDD)

**Files:**
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java`

**Step 1: Schrijf de falende tests**

```java
  @Test
  void verwijderManueelBezwaarSuccesvol() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.KLAAR);
    taak.setAantalBezwaren(3);
    taak.setHeeftManueel(true);

    var bezwaar = new GeextraheerdBezwaarEntiteit();
    bezwaar.setId(10L);
    bezwaar.setTaakId(1L);
    bezwaar.setManueel(true);
    when(bezwaarRepository.findById(10L)).thenReturn(Optional.of(bezwaar));
    when(repository.findById(1L)).thenReturn(Optional.of(taak));

    // Nog 1 ander manueel bezwaar over na verwijdering
    var anderManueel = new GeextraheerdBezwaarEntiteit();
    anderManueel.setManueel(true);
    when(bezwaarRepository.findByTaakId(1L)).thenReturn(List.of(anderManueel));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.verwijderManueelBezwaar("windmolens", "bezwaar-001.txt", 10L);

    verify(bezwaarRepository).delete(bezwaar);
    assertThat(taak.getAantalBezwaren()).isEqualTo(2);
    assertThat(taak.isHeeftManueel()).isTrue();
  }

  @Test
  void verwijderLaatstManueelBezwaarZetHeeftManueelUit() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.KLAAR);
    taak.setAantalBezwaren(2);
    taak.setHeeftManueel(true);

    var bezwaar = new GeextraheerdBezwaarEntiteit();
    bezwaar.setId(10L);
    bezwaar.setTaakId(1L);
    bezwaar.setManueel(true);
    when(bezwaarRepository.findById(10L)).thenReturn(Optional.of(bezwaar));
    when(repository.findById(1L)).thenReturn(Optional.of(taak));

    // Geen manuele bezwaren meer over
    when(bezwaarRepository.findByTaakId(1L)).thenReturn(List.of());
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.verwijderManueelBezwaar("windmolens", "bezwaar-001.txt", 10L);

    assertThat(taak.isHeeftManueel()).isFalse();
  }

  @Test
  void verwijderNietManueelBezwaarGooitForbidden() {
    var bezwaar = new GeextraheerdBezwaarEntiteit();
    bezwaar.setId(10L);
    bezwaar.setTaakId(1L);
    bezwaar.setManueel(false);
    when(bezwaarRepository.findById(10L)).thenReturn(Optional.of(bezwaar));

    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
        service.verwijderManueelBezwaar("windmolens", "bezwaar-001.txt", 10L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("niet manueel");
  }
```

**Step 2: Run tests om te verifiëren dat ze falen**

```bash
./mvnw test -pl app -Denforcer.skip=true -Dtest=ExtractieTaakServiceTest
```

Expected: FAIL — methode bestaat nog niet.

**Step 3: Implementeer verwijderManueelBezwaar**

Voeg toe aan `ExtractieTaakService.java` (na `voegManueelBezwaarToe`):

```java
  /**
   * Verwijdert een manueel toegevoegd bezwaar.
   *
   * @param projectNaam naam van het project
   * @param bestandsnaam naam van het bestand
   * @param bezwaarId id van het te verwijderen bezwaar
   * @throws IllegalArgumentException als het bezwaar niet gevonden wordt
   * @throws IllegalStateException als het bezwaar niet manueel is
   */
  @Transactional
  public void verwijderManueelBezwaar(String projectNaam, String bestandsnaam, Long bezwaarId) {
    var bezwaar = bezwaarRepository.findById(bezwaarId)
        .orElseThrow(() -> new IllegalArgumentException("Bezwaar niet gevonden: " + bezwaarId));

    if (!bezwaar.isManueel()) {
      throw new IllegalStateException("Bezwaar " + bezwaarId + " is niet manueel toegevoegd");
    }

    var taak = repository.findById(bezwaar.getTaakId())
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden"));

    bezwaarRepository.delete(bezwaar);

    // Werk aantalBezwaren bij
    int huidigAantal = taak.getAantalBezwaren() != null ? taak.getAantalBezwaren() : 0;
    taak.setAantalBezwaren(Math.max(0, huidigAantal - 1));

    // Check of er nog manuele bezwaren over zijn
    var overigeBezwaren = bezwaarRepository.findByTaakId(taak.getId());
    boolean nogManueel = overigeBezwaren.stream().anyMatch(GeextraheerdBezwaarEntiteit::isManueel);
    taak.setHeeftManueel(nogManueel);

    repository.save(taak);
    notificatie.taakGewijzigd(ExtractieTaakDto.van(taak));
  }
```

**Step 4: Run tests**

```bash
./mvnw test -pl app -Denforcer.skip=true -Dtest=ExtractieTaakServiceTest
```

Expected: alle tests PASS.

**Step 5: Commit**

```bash
git add -A
git commit -m "feat: verwijderManueelBezwaar service-methode"
```

---

### Task 6: Controller — POST/DELETE endpoints (TDD)

**Files:**
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieControllerTest.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieController.java`

**Step 1: Schrijf de falende tests**

Voeg toe aan `ExtractieControllerTest.java`:

```java
  @Test
  void voegManueelBezwaarToe() throws Exception {
    var detail = new ExtractieDetailDto.BezwaarDetail(
        10L, "Geluidshinder", "De geluidsoverlast zal...", true, true);
    when(extractieTaakService.voegManueelBezwaarToe(
        "windmolens", "bezwaar-001.txt", "Geluidshinder", "De geluidsoverlast zal..."))
        .thenReturn(detail);

    mockMvc.perform(post("/api/v1/projects/windmolens/extracties/bezwaar-001.txt/bezwaren")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"samenvatting\":\"Geluidshinder\",\"passage\":\"De geluidsoverlast zal...\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.samenvatting").value("Geluidshinder"))
        .andExpect(jsonPath("$.manueel").value(true));
  }

  @Test
  void voegManueelBezwaarToeGeeft400BijOngeldigePassage() throws Exception {
    when(extractieTaakService.voegManueelBezwaarToe(
        "windmolens", "bezwaar-001.txt", "Samenvatting", "Onbekende passage"))
        .thenThrow(new IllegalArgumentException("Passage komt niet voor in het originele document"));

    mockMvc.perform(post("/api/v1/projects/windmolens/extracties/bezwaar-001.txt/bezwaren")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"samenvatting\":\"Samenvatting\",\"passage\":\"Onbekende passage\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fout").value("Passage komt niet voor in het originele document"));
  }

  @Test
  void verwijderManueelBezwaar() throws Exception {
    mockMvc.perform(delete("/api/v1/projects/windmolens/extracties/bezwaar-001.txt/bezwaren/10")
            .with(csrf()))
        .andExpect(status().isNoContent());

    verify(extractieTaakService).verwijderManueelBezwaar("windmolens", "bezwaar-001.txt", 10L);
  }

  @Test
  void verwijderNietManueelBezwaarGeeft403() throws Exception {
    doThrow(new IllegalStateException("Bezwaar is niet manueel toegevoegd"))
        .when(extractieTaakService).verwijderManueelBezwaar("windmolens", "bezwaar-001.txt", 10L);

    mockMvc.perform(delete("/api/v1/projects/windmolens/extracties/bezwaar-001.txt/bezwaren/10")
            .with(csrf()))
        .andExpect(status().isForbidden());
  }
```

**Step 2: Run tests om te verifiëren dat ze falen**

```bash
./mvnw test -pl app -Denforcer.skip=true -Dtest=ExtractieControllerTest
```

Expected: FAIL — endpoints bestaan nog niet.

**Step 3: Implementeer de endpoints**

Voeg toe aan `ExtractieController.java` (na het `geefDetails` endpoint, rond regel 108):

```java
  /**
   * Voegt een manueel bezwaar toe aan een afgeronde extractie.
   *
   * @param naam projectnaam
   * @param bestandsnaam naam van het bestand
   * @param request verzoek met samenvatting en passage
   * @return het aangemaakte bezwaar
   */
  @PostMapping("/{naam}/extracties/{bestandsnaam}/bezwaren")
  public ResponseEntity<?> voegBezwaarToe(
      @PathVariable String naam, @PathVariable String bestandsnaam,
      @RequestBody ManueelBezwaarRequest request) {
    try {
      var detail = extractieTaakService.voegManueelBezwaarToe(
          naam, bestandsnaam, request.samenvatting(), request.passage());
      return ResponseEntity.status(201).body(detail);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new FoutResponse(e.getMessage()));
    }
  }

  /**
   * Verwijdert een manueel bezwaar.
   *
   * @param naam projectnaam
   * @param bestandsnaam naam van het bestand
   * @param bezwaarId id van het te verwijderen bezwaar
   * @return 204 bij succes, 403 als het bezwaar niet manueel is
   */
  @DeleteMapping("/{naam}/extracties/{bestandsnaam}/bezwaren/{bezwaarId}")
  public ResponseEntity<Void> verwijderBezwaar(
      @PathVariable String naam, @PathVariable String bestandsnaam,
      @PathVariable Long bezwaarId) {
    try {
      extractieTaakService.verwijderManueelBezwaar(naam, bestandsnaam, bezwaarId);
      return ResponseEntity.noContent().build();
    } catch (IllegalStateException e) {
      return ResponseEntity.status(403).build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    }
  }

  /** Request DTO voor manueel bezwaar. */
  record ManueelBezwaarRequest(String samenvatting, String passage) {}

  /** Response DTO voor foutmeldingen. */
  record FoutResponse(String fout) {}
```

**Step 4: Run tests**

```bash
./mvnw test -pl app -Denforcer.skip=true -Dtest=ExtractieControllerTest
```

Expected: alle tests PASS.

**Step 5: Run alle backend tests**

```bash
./mvnw test -pl app -Denforcer.skip=true
```

Expected: alle tests PASS.

**Step 6: Commit**

```bash
git add -A
git commit -m "feat: POST/DELETE endpoints voor manueel bezwaar"
```

---

### Task 7: Frontend — ✍️ emoji in tabel + heeftManueel

**Files:**
- Test: `webapp/test/bezwaarschriften-bezwaren-tabel-extractie-details.cy.js`
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js:280-286,226-233`

**Step 1: Schrijf de falende Cypress tests**

Voeg toe aan `bezwaarschriften-bezwaren-tabel-extractie-details.cy.js`:

```javascript
  it('toont ✍️ emoji bij bestandsnaam als heeftManueel true is', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-001.txt', status: 'extractie-klaar', aantalBezwaren: 3,
              heeftOpmerkingen: false, heeftManueel: true},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Bevat manueel toegevoegde bezwaren"]')
        .should('have.length', 1)
        .and('contain.text', '\u270D\uFE0F');
  });

  it('toont geen ✍️ emoji als heeftManueel false is', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-001.txt', status: 'extractie-klaar', aantalBezwaren: 3,
              heeftOpmerkingen: false, heeftManueel: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Bevat manueel toegevoegde bezwaren"]')
        .should('not.exist');
  });

  it('toont beide iconen als heeftOpmerkingen en heeftManueel true zijn', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-001.txt', status: 'extractie-klaar', aantalBezwaren: 3,
              heeftOpmerkingen: true, heeftManueel: true},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Niet alle passages konden gevonden worden"]')
        .should('have.length', 1);
    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Bevat manueel toegevoegde bezwaren"]')
        .should('have.length', 1);
  });
```

**Step 2: Run tests om te verifiëren dat ze falen**

```bash
cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften/webapp
npm test
```

Expected: FAIL — ✍️ emoji wordt nog niet gerenderd.

**Step 3: Implementeer ✍️ emoji in bestandsnaam renderer**

In `bezwaarschriften-bezwaren-tabel.js`, in de `case 'bestandsnaam':` renderer (na regel 286, na de `heeftOpmerkingen` blok):

```javascript
            if (rij.heeftManueel) {
              const manueelIcon = document.createElement('span');
              manueelIcon.textContent = '\u270D\uFE0F';
              manueelIcon.title = 'Bevat manueel toegevoegde bezwaren';
              manueelIcon.style.marginRight = '0.4rem';
              td.appendChild(manueelIcon);
            }
```

**Step 4: Voeg heeftManueel toe aan werkBijMetTaakUpdate**

In de `werkBijMetTaakUpdate` methode (regel 226-233), voeg `heeftManueel` toe aan het mapped object:

```javascript
      b.bestandsnaam === taak.bestandsnaam ? {
        bestandsnaam: taak.bestandsnaam,
        status: taak.status,
        aantalWoorden: taak.aantalWoorden,
        aantalBezwaren: taak.aantalBezwaren,
        heeftOpmerkingen: taak.heeftOpmerkingen,
        heeftManueel: taak.heeftManueel,
      } : b,
```

**Step 5: Run tests**

```bash
cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften/webapp
npm test
```

Expected: alle tests PASS.

**Step 6: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js \
       webapp/test/bezwaarschriften-bezwaren-tabel-extractie-details.cy.js
git commit -m "feat: ✍️ emoji in tabel bij manueel toegevoegde bezwaren"
```

---

### Task 8: Frontend — side-panel bezwaar rendering met manueel label en verwijder-knop

**Files:**
- Test: `webapp/test/bezwaarschriften-bezwaren-tabel-extractie-details.cy.js`
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js:424-445`

**Step 1: Schrijf de falende Cypress tests**

Voeg toe aan het testbestand:

```javascript
  it('toont "Manueel" label bij manueel bezwaar', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: {
        bestandsnaam: 'bezwaar-001.txt',
        aantalBezwaren: 2,
        bezwaren: [
          {id: 1, samenvatting: 'AI bezwaar', passage: 'Passage 1', passageGevonden: true, manueel: false},
          {id: 2, samenvatting: 'Manueel bezwaar', passage: 'Passage 2', passageGevonden: true, manueel: true},
        ],
      },
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-001.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('.bezwaar-manueel-label')
        .should('have.length', 1)
        .and('have.text', 'Manueel');
  });

  it('toont verwijder-knop alleen bij manueel bezwaar', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: {
        bestandsnaam: 'bezwaar-001.txt',
        aantalBezwaren: 2,
        bezwaren: [
          {id: 1, samenvatting: 'AI bezwaar', passage: 'Passage 1', passageGevonden: true, manueel: false},
          {id: 2, samenvatting: 'Manueel bezwaar', passage: 'Passage 2', passageGevonden: true, manueel: true},
        ],
      },
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-001.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('.bezwaar-verwijder-knop')
        .should('have.length', 1);
  });

  it('verwijdert manueel bezwaar na klik op verwijder-knop', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: {
        bestandsnaam: 'bezwaar-001.txt',
        aantalBezwaren: 1,
        bezwaren: [
          {id: 10, samenvatting: 'Manueel bezwaar', passage: 'Passage.', passageGevonden: true, manueel: true},
        ],
      },
    }).as('details');

    cy.intercept('DELETE', '/api/v1/projects/windmolens/extracties/bezwaar-001.txt/bezwaren/10', {
      statusCode: 204,
    }).as('verwijder');

    // Na verwijdering, herlaad met leeg resultaat
    cy.intercept('GET', '/api/v1/projects/windmolens/extracties/bezwaar-001.txt/details', {
      statusCode: 200,
      body: {bestandsnaam: 'bezwaar-001.txt', aantalBezwaren: 0, bezwaren: []},
    }).as('herlaad');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-001.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('.bezwaar-verwijder-knop')
        .click();

    cy.wait('@verwijder');
  });
```

**Step 2: Run tests om te verifiëren dat ze falen**

```bash
cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften/webapp
npm test
```

**Step 3: Voeg CSS toe voor de manueel-label**

In het `<style>` blok (rond regel 124, na `.bezwaar-waarschuwing`):

```css
        .bezwaar-manueel-label {
          display: inline-block;
          font-size: 0.75rem;
          color: #687483;
          background: #f3f5f6;
          padding: 0.1rem 0.4rem;
          border-radius: 2px;
          margin-bottom: 0.25rem;
        }
        .bezwaar-header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
        }
        .bezwaar-verwijder-knop {
          background: none;
          border: none;
          cursor: pointer;
          color: #db3434;
          font-size: 1rem;
          padding: 0;
          line-height: 1;
          flex-shrink: 0;
          opacity: 0.6;
        }
        .bezwaar-verwijder-knop:hover { opacity: 1; }
```

**Step 4: Update de bezwaar rendering loop**

Vervang het rendering-blok in `toonExtractieDetails` (regels 424-445):

```javascript
          gesorteerd.forEach((bezwaar) => {
            const item = document.createElement('div');
            item.className = 'bezwaar-item';

            if (!bezwaar.passageGevonden) {
              const waarschuwing = document.createElement('div');
              waarschuwing.className = 'bezwaar-waarschuwing';
              waarschuwing.textContent = '\u26A0\uFE0F Passage kon niet gevonden worden';
              item.appendChild(waarschuwing);
            }

            if (bezwaar.manueel) {
              const label = document.createElement('div');
              label.className = 'bezwaar-manueel-label';
              label.textContent = 'Manueel';
              item.appendChild(label);
            }

            const header = document.createElement('div');
            header.className = 'bezwaar-header';

            const samenvatting = document.createElement('div');
            samenvatting.className = 'bezwaar-samenvatting';
            samenvatting.textContent = bezwaar.samenvatting;
            header.appendChild(samenvatting);

            if (bezwaar.manueel) {
              const verwijderKnop = document.createElement('button');
              verwijderKnop.className = 'bezwaar-verwijder-knop';
              verwijderKnop.innerHTML = '\uD83D\uDDD1\uFE0F';
              verwijderKnop.title = 'Manueel bezwaar verwijderen';
              verwijderKnop.addEventListener('click', () => {
                this._verwijderManueelBezwaar(projectNaam, bestandsnaam, bezwaar.id);
              });
              header.appendChild(verwijderKnop);
            }

            item.appendChild(header);

            const passage = document.createElement('div');
            passage.className = 'bezwaar-passage';
            passage.textContent = `\u201C${bezwaar.passage}\u201D`;

            item.appendChild(passage);
            inhoud.appendChild(item);
          });
```

**Step 5: Voeg de verwijder-methode toe**

Na de `toonExtractieDetails` methode (na regel 451):

```javascript
  _verwijderManueelBezwaar(projectNaam, bestandsnaam, bezwaarId) {
    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/extracties/${encodeURIComponent(bestandsnaam)}/bezwaren/${bezwaarId}`, {
      method: 'DELETE',
    }).then((response) => {
      if (!response.ok) throw new Error('Verwijderen mislukt');
      // Herlaad side-panel en tabeldata
      this.toonExtractieDetails(projectNaam, bestandsnaam);
      this.dispatchEvent(new CustomEvent('bezwaar-gewijzigd', {
        bubbles: true, composed: true,
      }));
    }).catch(() => {
      // Foutmelding in side-panel
      const inhoud = this.shadowRoot.querySelector('#extractie-side-sheet-inhoud');
      if (inhoud) {
        const fout = document.createElement('div');
        fout.className = 'bezwaar-waarschuwing';
        fout.textContent = 'Verwijderen mislukt, probeer opnieuw.';
        inhoud.prepend(fout);
      }
    });
  }
```

**Step 6: Run tests**

```bash
cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften/webapp
npm test
```

Expected: alle tests PASS.

**Step 7: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js \
       webapp/test/bezwaarschriften-bezwaren-tabel-extractie-details.cy.js
git commit -m "feat: manueel label en verwijder-knop in side-panel bezwaren"
```

---

### Task 9: Frontend — + knop en inline formulier in side-panel

**Files:**
- Test: `webapp/test/bezwaarschriften-bezwaren-tabel-extractie-details.cy.js`
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

**Step 1: Schrijf de falende Cypress tests**

Voeg toe aan het testbestand:

```javascript
  it('toont + knop in side-panel header bij extractie-klaar', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-002.txt', status: 'extractie-klaar', aantalBezwaren: 2, heeftOpmerkingen: false, heeftManueel: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#bezwaar-toevoegen-knop')
        .should('exist');
  });

  it('toont inline formulier na klik op + knop', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-002.txt', status: 'extractie-klaar', aantalBezwaren: 2, heeftOpmerkingen: false, heeftManueel: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#bezwaar-toevoegen-knop')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-bezwaar-formulier')
        .should('exist');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-samenvatting')
        .should('exist');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-passage')
        .should('exist');
  });

  it('opslaan-knop disabled bij lege velden', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-002.txt', status: 'extractie-klaar', aantalBezwaren: 2, heeftOpmerkingen: false, heeftManueel: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#bezwaar-toevoegen-knop')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-opslaan')
        .should('have.attr', 'disabled');
  });

  it('slaat manueel bezwaar op en herlaadt side-panel', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.intercept('POST', '/api/v1/projects/windmolens/extracties/bezwaar-002.txt/bezwaren', {
      statusCode: 201,
      body: {id: 10, samenvatting: 'Nieuw bezwaar', passage: 'Eerste passage.', passageGevonden: true, manueel: true},
    }).as('opslaan');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-002.txt', status: 'extractie-klaar', aantalBezwaren: 2, heeftOpmerkingen: false, heeftManueel: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#bezwaar-toevoegen-knop')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-samenvatting')
        .shadow()
        .find('textarea')
        .type('Nieuw bezwaar');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-passage')
        .shadow()
        .find('textarea')
        .type('Eerste passage.');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-opslaan')
        .click();

    cy.wait('@opslaan');
  });

  it('toont foutmelding bij ongeldige passage', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.intercept('POST', '/api/v1/projects/windmolens/extracties/bezwaar-002.txt/bezwaren', {
      statusCode: 400,
      body: {fout: 'Passage komt niet voor in het originele document'},
    }).as('opslaan');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-002.txt', status: 'extractie-klaar', aantalBezwaren: 2, heeftOpmerkingen: false, heeftManueel: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#bezwaar-toevoegen-knop')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-samenvatting')
        .shadow()
        .find('textarea')
        .type('Samenvatting');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-passage')
        .shadow()
        .find('textarea')
        .type('Onbekende passage');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-opslaan')
        .click();

    cy.wait('@opslaan');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-foutmelding')
        .should('contain.text', 'Passage komt niet voor in het originele document');
  });

  it('annuleert formulier bij klik op annuleer-knop', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-002.txt', status: 'extractie-klaar', aantalBezwaren: 2, heeftOpmerkingen: false, heeftManueel: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#bezwaar-toevoegen-knop')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-bezwaar-formulier')
        .should('exist');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-annuleer')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-bezwaar-formulier')
        .should('not.exist');
  });
```

**Step 2: Run tests om te verifiëren dat ze falen**

```bash
cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften/webapp
npm test
```

**Step 3: Voeg VlTextareaComponent import toe**

Bovenaan `bezwaarschriften-bezwaren-tabel.js`, voeg de VlTextarea import toe (na VlSelectComponent import):

```javascript
import {VlTextareaComponent} from '@domg-wc/components/form/textarea/vl-textarea.component.js';
import {VlButtonComponent} from '@domg-wc/components/action/button/vl-button.component.js';
```

En registreer ze (in de `registerWebComponents` array):

```javascript
registerWebComponents([
  VlRichDataTable, VlRichDataField, VlPillComponent,
  VlSearchFilterComponent, VlPagerComponent,
  VlInputFieldComponent, VlFormLabelComponent, VlSelectComponent,
  VlSideSheet, VlTextareaComponent, VlButtonComponent,
]);
```

**Step 4: Voeg CSS toe voor het formulier**

In het `<style>` blok (na de `.bezwaar-verwijder-knop:hover` regel):

```css
        #manueel-bezwaar-formulier {
          background: #f3f5f6;
          padding: 1rem;
          margin-bottom: 1.5rem;
          border-radius: 4px;
          border: 1px solid #e8ebee;
        }
        #manueel-bezwaar-formulier label {
          display: block;
          font-weight: bold;
          margin-bottom: 0.25rem;
          font-size: 0.875rem;
        }
        #manueel-bezwaar-formulier .formulier-veld {
          margin-bottom: 0.75rem;
        }
        .formulier-acties {
          display: flex;
          gap: 0.5rem;
          align-items: center;
        }
        #manueel-foutmelding {
          color: #db3434;
          font-size: 0.875rem;
          margin-top: 0.5rem;
        }
```

**Step 5: Voeg + knop toe in side-panel header**

In de HTML template, in de `.side-sheet-header` div (regel 150-154), voeg de + knop toe vóór de sluitknop:

```html
          <div class="side-sheet-header">
            <div id="extractie-side-sheet-titel" class="side-sheet-titel"></div>
            <button id="bezwaar-toevoegen-knop" class="side-sheet-sluit-knop"
                aria-label="Bezwaar toevoegen" style="display:none;" title="Bezwaar toevoegen">+</button>
            <button id="extractie-side-sheet-sluit" class="side-sheet-sluit-knop"
                aria-label="Sluiten">&times;</button>
          </div>
```

**Step 6: Voeg event listener toe voor + knop**

In `connectedCallback`, na de sluitknop listener (rond regel 203):

```javascript
    const toevoegKnop = this.shadowRoot.querySelector('#bezwaar-toevoegen-knop');
    if (toevoegKnop) {
      toevoegKnop.addEventListener('click', () => {
        this._toonManueelBezwaarFormulier();
      });
    }
```

**Step 7: Toon/verberg + knop in toonExtractieDetails**

In de `toonExtractieDetails` methode, na het zetten van de titel (na regel 422), voeg toe:

```javascript
          // Toon + knop als status extractie-klaar is
          const toevoegKnop = this.shadowRoot.querySelector('#bezwaar-toevoegen-knop');
          if (toevoegKnop) {
            toevoegKnop.style.display = 'inline-block';
          }
```

En aan het begin van de methode (na regel 401), verberg de knop initieel:

```javascript
    const toevoegKnop = this.shadowRoot.querySelector('#bezwaar-toevoegen-knop');
    if (toevoegKnop) toevoegKnop.style.display = 'none';
```

**Step 8: Implementeer het inline formulier**

Voeg de volgende methoden toe (na `_verwijderManueelBezwaar`):

```javascript
  _toonManueelBezwaarFormulier() {
    const inhoud = this.shadowRoot.querySelector('#extractie-side-sheet-inhoud');
    if (!inhoud || inhoud.querySelector('#manueel-bezwaar-formulier')) return;

    const formulier = document.createElement('div');
    formulier.id = 'manueel-bezwaar-formulier';

    formulier.innerHTML = `
      <div class="formulier-veld">
        <label for="manueel-samenvatting">Samenvatting</label>
        <vl-textarea id="manueel-samenvatting" rows="2" block></vl-textarea>
      </div>
      <div class="formulier-veld">
        <label for="manueel-passage">Passage</label>
        <vl-textarea id="manueel-passage" rows="4" block></vl-textarea>
      </div>
      <div class="formulier-acties">
        <vl-button id="manueel-opslaan" disabled>Opslaan</vl-button>
        <button id="manueel-annuleer" class="side-sheet-sluit-knop"
            aria-label="Annuleren" title="Annuleren">&times;</button>
      </div>
      <div id="manueel-foutmelding"></div>
    `;

    inhoud.prepend(formulier);

    // Enable/disable opslaan-knop op basis van input
    const samenvatting = formulier.querySelector('#manueel-samenvatting');
    const passage = formulier.querySelector('#manueel-passage');
    const opslaanKnop = formulier.querySelector('#manueel-opslaan');

    const updateOpslaanStatus = () => {
      const samenvattingWaarde = samenvatting.value || '';
      const passageWaarde = passage.value || '';
      if (samenvattingWaarde.trim() && passageWaarde.trim()) {
        opslaanKnop.removeAttribute('disabled');
      } else {
        opslaanKnop.setAttribute('disabled', '');
      }
    };

    samenvatting.addEventListener('input', updateOpslaanStatus);
    passage.addEventListener('input', updateOpslaanStatus);

    // Annuleer
    formulier.querySelector('#manueel-annuleer').addEventListener('click', () => {
      formulier.remove();
    });

    // Opslaan
    opslaanKnop.addEventListener('vl-click', () => {
      this._slaManueelBezwaarOp(samenvatting.value, passage.value);
    });
  }

  _slaManueelBezwaarOp(samenvatting, passage) {
    const projectNaam = this._projectNaam;
    const sideSheet = this.shadowRoot.querySelector('#extractie-side-sheet');
    const titelEl = this.shadowRoot.querySelector('#extractie-side-sheet-titel');
    const bestandsnaam = titelEl ? titelEl.textContent.split(' - ')[0] : '';

    const foutEl = this.shadowRoot.querySelector('#manueel-foutmelding');
    if (foutEl) foutEl.textContent = '';

    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/extracties/${encodeURIComponent(bestandsnaam)}/bezwaren`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({samenvatting, passage}),
    }).then((response) => {
      if (response.ok) {
        // Herlaad side-panel
        this.toonExtractieDetails(projectNaam, bestandsnaam);
        this.dispatchEvent(new CustomEvent('bezwaar-gewijzigd', {
          bubbles: true, composed: true,
        }));
      } else {
        return response.json().then((data) => {
          if (foutEl) {
            foutEl.textContent = data.fout || 'Opslaan mislukt, probeer opnieuw.';
          }
        });
      }
    }).catch(() => {
      if (foutEl) foutEl.textContent = 'Opslaan mislukt, probeer opnieuw.';
    });
  }
```

**Step 9: Run tests**

```bash
cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften/webapp
npm test
```

Expected: alle tests PASS.

**NB:** De Cypress tests interageren met `vl-textarea` via shadow DOM (`.shadow().find('textarea')`). Als de component anders werkt, pas de selectors aan. Check ook of `vl-textarea` een `value` property heeft of een `input` event dispatcht — raadpleeg `@domg-wc` documentatie indien nodig. Het kan zijn dat `vl-textarea` events anders dispatcht, in dat geval pas de `updateOpslaanStatus` listener aan.

**Step 10: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js \
       webapp/test/bezwaarschriften-bezwaren-tabel-extractie-details.cy.js
git commit -m "feat: + knop en inline formulier voor manueel bezwaar toevoegen"
```

---

### Task 10: Build, verify en frontend build

**Step 1: Run alle backend tests**

```bash
cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften
./mvnw test -pl app -Denforcer.skip=true
```

Expected: alle tests PASS.

**Step 2: Run alle frontend tests**

```bash
cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften/webapp
npm test
```

Expected: alle tests PASS.

**Step 3: Frontend build**

```bash
cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften/webapp
npm run build
```

**Step 4: Maven process-resources (zodat Spring Boot de nieuwe build serveert)**

```bash
cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften
mvn process-resources -pl webapp -Denforcer.skip=true
```

**Step 5: Volledige Maven build**

```bash
./mvnw package -Denforcer.skip=true -DskipTests
```

Expected: BUILD SUCCESS.

**Step 6: Commit build-artefacten**

```bash
git add webapp/build/
git commit -m "chore: frontend build met manueel bezwaar functionaliteit"
```

---

### Task 11: Documentatie — C4 update

**Files:**
- Modify: `docs/c4-c2-containers.md` (als relevant — het manueel bezwaar is een uitbreiding van het bestaande extractie-subsysteem, niet een nieuwe container)

Check of het C2-diagram updates nodig heeft voor de nieuwe endpoints. Waarschijnlijk niet — het is een uitbreiding binnen de bestaande Web Application container.

**Step 1: Lees en evalueer het huidige C2-diagram**

Controleer of er aanpassingen nodig zijn. De nieuwe endpoints zitten in de bestaande `ExtractieController` en `ExtractieTaakService`.

**Step 2: Commit indien nodig**

---

## Samenvatting van alle wijzigingen

| Bestand | Wijziging |
|---------|-----------|
| `20260302-manueel-bezwaar.xml` | Nieuw: Liquibase migratie |
| `master.xml` | +1 include |
| `GeextraheerdBezwaarEntiteit.java` | +`manueel` veld |
| `ExtractieTaak.java` | +`heeftManueel` veld |
| `ExtractieDetailDto.java` | BezwaarDetail +`id`, +`manueel` |
| `BezwaarBestand.java` | +`heeftManueel` |
| `ExtractieTaakDto.java` | +`heeftManueel` |
| `ExtractiePassageRepository.java` | +`findTopByTaakIdOrderByPassageNrDesc` |
| `ExtractieTaakService.java` | +`voegManueelBezwaarToe`, +`verwijderManueelBezwaar`, update `geefExtractieDetails` |
| `ExtractieController.java` | +POST bezwaren, +DELETE bezwaren, +DTOs |
| `ExtractieTaakServiceTest.java` | +7 tests |
| `ExtractieControllerTest.java` | +4 tests, fix bestaande constructors |
| `bezwaarschriften-bezwaren-tabel.js` | +imports, +CSS, +HTML, +formulier, +verwijder-logica, +✍️ emoji |
| `bezwaarschriften-bezwaren-tabel-extractie-details.cy.js` | +10 tests |
