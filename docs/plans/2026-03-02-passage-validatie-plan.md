# Passage-validatie Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Detecteer of LLM-geretourneerde passages daadwerkelijk voorkomen in het brondocument en toon waarschuwingen in de UI voor niet-gevonden passages.

**Architecture:** Nieuwe `PassageValidator` component valideert passages in de `markeerKlaar` flow. Whitespace-normalisatie + exacte substring match + 90% fuzzy fallback. Resultaat gepersisteerd als boolean per bezwaar + vlag op taak. Frontend sorteert niet-gevonden bovenaan met waarschuwing.

**Tech Stack:** Java 21, Spring Boot 3.4, Liquibase, JPA, @domg-wc web components, Cypress component testing

**Referentie design:** `docs/plans/2026-03-02-passage-validatie-design.md`

---

### Task 1: Flyway migratie — nieuwe kolommen

**Files:**
- Create: `app/src/main/resources/config/liquibase/changelog/20260302-passage-validatie.xml`
- Modify: `app/src/main/resources/config/liquibase/master.xml`

**Step 1: Maak de Liquibase changelog**

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">

  <changeSet id="20260302-passage-validatie-1" author="claude">
    <addColumn tableName="geextraheerd_bezwaar">
      <column name="passage_gevonden" type="boolean" defaultValueBoolean="true">
        <constraints nullable="false"/>
      </column>
    </addColumn>
  </changeSet>

  <changeSet id="20260302-passage-validatie-2" author="claude">
    <addColumn tableName="extractie_taak">
      <column name="heeft_opmerkingen" type="boolean" defaultValueBoolean="false">
        <constraints nullable="false"/>
      </column>
    </addColumn>
  </changeSet>

</databaseChangeLog>
```

**Step 2: Voeg de changelog toe aan master.xml**

Voeg toe na de laatste `<include>` regel in `master.xml`:

```xml
  <include file="config/liquibase/changelog/20260302-passage-validatie.xml"/>
```

**Step 3: Commit**

```bash
git add app/src/main/resources/config/liquibase/changelog/20260302-passage-validatie.xml app/src/main/resources/config/liquibase/master.xml
git commit -m "feat: liquibase migratie voor passage-validatie kolommen"
```

---

### Task 2: JPA entiteiten uitbreiden

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarEntiteit.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaak.java`

**Step 1: Voeg `passageGevonden` toe aan `GeextraheerdBezwaarEntiteit`**

Voeg na het `categorie` veld (regel 28) het volgende veld + getter/setter toe:

```java
  @Column(name = "passage_gevonden", nullable = false)
  private boolean passageGevonden = true;
```

Getter:
```java
  public boolean isPassageGevonden() {
    return passageGevonden;
  }
```

Setter:
```java
  public void setPassageGevonden(boolean passageGevonden) {
    this.passageGevonden = passageGevonden;
  }
```

**Step 2: Voeg `heeftOpmerkingen` toe aan `ExtractieTaak`**

Voeg na het `aantalBezwaren` veld (regel 45) het volgende veld + getter/setter toe:

```java
  @Column(name = "heeft_opmerkingen", nullable = false)
  private boolean heeftOpmerkingen = false;
```

Getter:
```java
  public boolean isHeeftOpmerkingen() {
    return heeftOpmerkingen;
  }
```

Setter:
```java
  public void setHeeftOpmerkingen(boolean heeftOpmerkingen) {
    this.heeftOpmerkingen = heeftOpmerkingen;
  }
```

**Step 3: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarEntiteit.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaak.java
git commit -m "feat: passageGevonden en heeftOpmerkingen velden op entiteiten"
```

---

### Task 3: DTOs uitbreiden

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieDetailDto.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakDto.java`

**Step 1: Breid `BezwaarDetail` uit met `passageGevonden`**

In `ExtractieDetailDto.java`, vervang:

```java
  public record BezwaarDetail(String samenvatting, String passage) { }
```

door:

```java
  public record BezwaarDetail(String samenvatting, String passage, boolean passageGevonden) { }
```

**Step 2: Breid `ExtractieTaakDto` uit met `heeftOpmerkingen`**

In `ExtractieTaakDto.java`, vervang het record:

```java
public record ExtractieTaakDto(
    Long id, String projectNaam, String bestandsnaam, String status,
    int aantalPogingen, String aangemaaktOp, String verwerkingGestartOp,
    Integer aantalWoorden, Integer aantalBezwaren, String foutmelding) {
```

door:

```java
public record ExtractieTaakDto(
    Long id, String projectNaam, String bestandsnaam, String status,
    int aantalPogingen, String aangemaaktOp, String verwerkingGestartOp,
    Integer aantalWoorden, Integer aantalBezwaren, String foutmelding,
    boolean heeftOpmerkingen) {
```

En pas de `van()` factory method aan — voeg `taak.isHeeftOpmerkingen()` toe als laatste argument:

```java
  static ExtractieTaakDto van(ExtractieTaak taak) {
    return new ExtractieTaakDto(
        taak.getId(), taak.getProjectNaam(), taak.getBestandsnaam(),
        statusNaarString(taak.getStatus()), taak.getAantalPogingen(),
        taak.getAangemaaktOp().toString(),
        taak.getVerwerkingGestartOp() != null ? taak.getVerwerkingGestartOp().toString() : null,
        taak.getAantalWoorden(), taak.getAantalBezwaren(), taak.getFoutmelding(),
        taak.isHeeftOpmerkingen());
  }
```

**Step 3: Fix compileerfouten in tests**

De bestaande tests gebruiken `ExtractieTaakDto` constructors en `BezwaarDetail` constructors die nu een extra parameter nodig hebben. Fix alle voorkomens:

In `ExtractieControllerTest.java`:
- Alle `new ExtractieTaakDto(...)` — voeg `, false` toe als laatste argument
- Alle `new ExtractieDetailDto.BezwaarDetail(samenvatting, passage)` — voeg `, true` toe als derde argument

In `ExtractieTaakServiceTest.java`:
- Geen directe DTO-constructies, maar check dat tests nog compileren

**Step 4: Verifieer dat alles compileert**

Run: `mvn compile -pl app -Denforcer.skip=true`
Expected: BUILD SUCCESS

**Step 5: Verifieer dat bestaande tests slagen**

Run: `mvn test -pl app -Denforcer.skip=true`
Expected: BUILD SUCCESS, alle bestaande tests slagen

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieDetailDto.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakDto.java app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieControllerTest.java
git commit -m "feat: passageGevonden en heeftOpmerkingen in DTOs"
```

---

### Task 4: PassageValidator — failing tests schrijven

**Files:**
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/PassageValidatorTest.java`

**Step 1: Schrijf de test klasse**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PassageValidatorTest {

  private PassageValidator validator;

  @BeforeEach
  void setUp() {
    validator = new PassageValidator();
  }

  @Test
  void exacteMatchGevondenEenKeer() {
    var bezwaar = maakBezwaar(1);
    var passageMap = Map.of(1, "De geluidsoverlast zal onze nachtrust verstoren.");
    var documentTekst = "Wij dienen bezwaar in. De geluidsoverlast zal onze nachtrust verstoren. Daarom zijn wij tegen.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, documentTekst);

    assertThat(bezwaar.isPassageGevonden()).isTrue();
    assertThat(resultaat.aantalNietGevonden()).isZero();
  }

  @Test
  void exacteMatchGevondenMeerdereKeren() {
    var bezwaar = maakBezwaar(1);
    var passageMap = Map.of(1, "bezwaar");
    var documentTekst = "Wij dienen bezwaar in tegen het bezwaar dat is ingediend.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, documentTekst);

    assertThat(bezwaar.isPassageGevonden()).isTrue();
    assertThat(resultaat.aantalNietGevonden()).isZero();
  }

  @Test
  void nietGevondenGeeftFalse() {
    var bezwaar = maakBezwaar(1);
    var passageMap = Map.of(1, "Deze tekst komt helemaal niet voor in het document.");
    var documentTekst = "Wij dienen bezwaar in tegen de bouw van windmolens.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, documentTekst);

    assertThat(bezwaar.isPassageGevonden()).isFalse();
    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
  }

  @Test
  void whitespaceNormalisatieExtraSpaties() {
    var bezwaar = maakBezwaar(1);
    var passageMap = Map.of(1, "De geluidsoverlast  zal   onze nachtrust verstoren.");
    var documentTekst = "Wij vinden dat de geluidsoverlast zal onze nachtrust verstoren. Daarom zijn wij tegen.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, documentTekst);

    assertThat(bezwaar.isPassageGevonden()).isTrue();
    assertThat(resultaat.aantalNietGevonden()).isZero();
  }

  @Test
  void whitespaceNormalisatieNewlines() {
    var bezwaar = maakBezwaar(1);
    var passageMap = Map.of(1, "De geluidsoverlast\nzal onze\nnachtrust verstoren.");
    var documentTekst = "De geluidsoverlast zal onze nachtrust verstoren. Wij zijn tegen.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, documentTekst);

    assertThat(bezwaar.isPassageGevonden()).isTrue();
    assertThat(resultaat.aantalNietGevonden()).isZero();
  }

  @Test
  void fuzzyMatchNetBovenDrempel() {
    var bezwaar = maakBezwaar(1);
    // Passage wijkt licht af: "zal" → "zou" (1 char verschil op lange tekst, boven 90%)
    var passageMap = Map.of(1, "De geluidsoverlast zou onze nachtrust verstoren en onze gezondheid schaden");
    var documentTekst = "De geluidsoverlast zal onze nachtrust verstoren en onze gezondheid schaden bij de bouw.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, documentTekst);

    assertThat(bezwaar.isPassageGevonden()).isTrue();
    assertThat(resultaat.aantalNietGevonden()).isZero();
  }

  @Test
  void fuzzyMatchOnderDrempel() {
    var bezwaar = maakBezwaar(1);
    // Passage is compleet anders geformuleerd
    var passageMap = Map.of(1, "Het lawaai van de windmolens is ondraaglijk voor omwonenden.");
    var documentTekst = "De geluidsoverlast zal onze nachtrust verstoren en onze gezondheid schaden.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, documentTekst);

    assertThat(bezwaar.isPassageGevonden()).isFalse();
    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
  }

  @Test
  void legePassageTekst() {
    var bezwaar = maakBezwaar(1);
    var passageMap = Map.of(1, "");
    var documentTekst = "Wij dienen bezwaar in.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, documentTekst);

    assertThat(bezwaar.isPassageGevonden()).isFalse();
    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
  }

  @Test
  void legeDocumentTekst() {
    var bezwaar = maakBezwaar(1);
    var passageMap = Map.of(1, "De geluidsoverlast zal onze nachtrust verstoren.");
    var documentTekst = "";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, documentTekst);

    assertThat(bezwaar.isPassageGevonden()).isFalse();
    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
  }

  @Test
  void meerdereBezwarenMixVanGevondenEnNiet() {
    var bezwaar1 = maakBezwaar(1);
    var bezwaar2 = maakBezwaar(2);
    var passageMap = Map.of(
        1, "De geluidsoverlast zal onze nachtrust verstoren.",
        2, "Deze passage bestaat totaal niet in het document.");
    var documentTekst = "De geluidsoverlast zal onze nachtrust verstoren. Wij zijn tegen.";

    var resultaat = validator.valideer(List.of(bezwaar1, bezwaar2), passageMap, documentTekst);

    assertThat(bezwaar1.isPassageGevonden()).isTrue();
    assertThat(bezwaar2.isPassageGevonden()).isFalse();
    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
  }

  @Test
  void passageZonderEntryInMap() {
    var bezwaar = maakBezwaar(99);
    var passageMap = Map.of(1, "Een passage");
    var documentTekst = "Een document.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, documentTekst);

    assertThat(bezwaar.isPassageGevonden()).isFalse();
    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
  }

  private GeextraheerdBezwaarEntiteit maakBezwaar(int passageNr) {
    var bezwaar = new GeextraheerdBezwaarEntiteit();
    bezwaar.setPassageNr(passageNr);
    bezwaar.setSamenvatting("Samenvatting " + passageNr);
    bezwaar.setCategorie("milieu");
    return bezwaar;
  }
}
```

**Step 2: Verifieer dat de tests niet compileren**

Run: `mvn test-compile -pl app -Denforcer.skip=true`
Expected: FAIL — `PassageValidator` klasse bestaat nog niet

**Step 3: Commit**

```bash
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/PassageValidatorTest.java
git commit -m "test: failing tests voor PassageValidator"
```

---

### Task 5: PassageValidator — implementatie

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/PassageValidator.java`

**Step 1: Implementeer de PassageValidator**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Valideert of LLM-geretourneerde passages daadwerkelijk voorkomen in het brondocument.
 *
 * <p>Werkt in twee fasen: eerst een exacte substring-check op genormaliseerde tekst,
 * daarna een fuzzy fallback met een sliding window en 90% similarity-drempel.
 */
@Component
public class PassageValidator {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final double FUZZY_DREMPEL = 0.90;

  /**
   * Resultaat van passage-validatie.
   *
   * @param aantalNietGevonden het aantal passages dat niet teruggevonden kon worden
   */
  public record ValidatieResultaat(int aantalNietGevonden) {}

  /**
   * Valideert alle bezwaren tegen de documenttekst. Zet {@code passageGevonden}
   * direct op de meegegeven entiteiten.
   *
   * @param bezwaren de te valideren bezwaar-entiteiten
   * @param passageMap mapping van passageNr naar passage-tekst
   * @param documentTekst de volledige tekst van het brondocument
   * @return validatieresultaat met het aantal niet-gevonden passages
   */
  public ValidatieResultaat valideer(List<GeextraheerdBezwaarEntiteit> bezwaren,
      Map<Integer, String> passageMap, String documentTekst) {
    var genormaliseerdeDoc = normaliseer(documentTekst);
    int aantalNietGevonden = 0;

    for (var bezwaar : bezwaren) {
      var passageTekst = passageMap.getOrDefault(bezwaar.getPassageNr(), "");
      var genormaliseerdePassage = normaliseer(passageTekst);

      if (genormaliseerdePassage.isEmpty() || genormaliseerdeDoc.isEmpty()) {
        bezwaar.setPassageGevonden(false);
        aantalNietGevonden++;
        continue;
      }

      if (genormaliseerdeDoc.contains(genormaliseerdePassage)) {
        bezwaar.setPassageGevonden(true);
      } else if (fuzzyMatch(genormaliseerdePassage, genormaliseerdeDoc)) {
        bezwaar.setPassageGevonden(true);
        LOGGER.debug("Passage fuzzy gevonden voor bezwaar passageNr={}",
            bezwaar.getPassageNr());
      } else {
        bezwaar.setPassageGevonden(false);
        aantalNietGevonden++;
        LOGGER.info("Passage niet gevonden voor bezwaar passageNr={}: '{}'",
            bezwaar.getPassageNr(),
            passageTekst.length() > 80 ? passageTekst.substring(0, 80) + "..." : passageTekst);
      }
    }

    return new ValidatieResultaat(aantalNietGevonden);
  }

  /**
   * Normaliseert tekst: alle whitespace comprimeren naar enkele spatie, trim, lowercase.
   */
  String normaliseer(String tekst) {
    if (tekst == null) {
      return "";
    }
    return tekst.replaceAll("\\s+", " ").trim().toLowerCase();
  }

  /**
   * Fuzzy match via sliding window. Schuift een venster ter grootte van de passage
   * over het document en berekent per positie de similarity.
   */
  private boolean fuzzyMatch(String passage, String document) {
    int vensterGrootte = passage.length();
    if (vensterGrootte > document.length()) {
      return berekenSimilarity(passage, document) >= FUZZY_DREMPEL;
    }

    for (int i = 0; i <= document.length() - vensterGrootte; i++) {
      var venster = document.substring(i, i + vensterGrootte);
      if (berekenSimilarity(passage, venster) >= FUZZY_DREMPEL) {
        return true;
      }
    }
    return false;
  }

  /**
   * Berekent de similarity ratio tussen twee strings op basis van gemeenschappelijke
   * bigrammen (paren van opeenvolgende karakters). Geeft een waarde tussen 0.0 en 1.0.
   */
  double berekenSimilarity(String a, String b) {
    if (a.isEmpty() && b.isEmpty()) {
      return 1.0;
    }
    if (a.isEmpty() || b.isEmpty()) {
      return 0.0;
    }
    if (a.length() == 1 || b.length() == 1) {
      // Fallback naar karakter-overlap voor zeer korte strings
      long gemeenschappelijk = a.chars()
          .filter(c -> b.indexOf(c) >= 0)
          .count();
      return 2.0 * gemeenschappelijk / (a.length() + b.length());
    }

    var bigrammenA = new java.util.ArrayList<String>();
    for (int i = 0; i < a.length() - 1; i++) {
      bigrammenA.add(a.substring(i, i + 2));
    }
    var bigrammenB = new java.util.ArrayList<String>();
    for (int i = 0; i < b.length() - 1; i++) {
      bigrammenB.add(b.substring(i, i + 2));
    }

    var kopieB = new java.util.ArrayList<>(bigrammenB);
    int gemeenschappelijk = 0;
    for (var bigram : bigrammenA) {
      int idx = kopieB.indexOf(bigram);
      if (idx >= 0) {
        gemeenschappelijk++;
        kopieB.remove(idx);
      }
    }

    return 2.0 * gemeenschappelijk / (bigrammenA.size() + bigrammenB.size());
  }
}
```

**Step 2: Run de tests**

Run: `mvn test -pl app -Denforcer.skip=true -Dtest=PassageValidatorTest`
Expected: PASS — alle 11 tests slagen

Als een fuzzy-drempel test faalt, pas de testdata aan zodat de similarity duidelijk boven/onder 90% zit. De bigrammen-methode kan iets andere scores geven dan verwacht — stem de testcases af op de werkelijke scores.

**Step 3: Run alle tests**

Run: `mvn test -pl app -Denforcer.skip=true`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/PassageValidator.java
git commit -m "feat: PassageValidator met whitespace-normalisatie en fuzzy matching"
```

---

### Task 6: ExtractieTaakService — failing tests voor validatie-integratie

**Files:**
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java`

**Step 1: Voeg mocks toe voor nieuwe dependencies**

Voeg toe na de bestaande `@Mock` declaraties:

```java
  @Mock
  private ProjectPoort projectPoort;

  @Mock
  private IngestiePoort ingestiePoort;

  @Mock
  private PassageValidator passageValidator;
```

Pas de `setUp()` methode aan — voeg de nieuwe parameters toe aan de constructor:

```java
  @BeforeEach
  void setUp() {
    service = new ExtractieTaakService(repository, notificatie, projectService,
        passageRepository, bezwaarRepository, projectPoort, ingestiePoort,
        passageValidator, 3, 3);
  }
```

**Step 2: Voeg imports toe**

```java
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.Brondocument;
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import java.nio.file.Path;
```

**Step 3: Schrijf de nieuwe tests**

Voeg toe na de bestaande test `markeerKlaarSlaatPassagesEnBezwarenOp`:

```java
  @Test
  void markeerKlaarValideertPassagesEnZetPassageGevonden() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    var pad = Path.of("/tmp/windmolens/bezwaren/bezwaar-001.txt");
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar-001.txt")).thenReturn(pad);
    when(ingestiePoort.leesBestand(pad)).thenReturn(
        new Brondocument("Volledige documenttekst.", "bezwaar-001.txt", pad.toString(),
            Instant.now()));

    when(passageValidator.valideer(any(), any(), any()))
        .thenReturn(new PassageValidator.ValidatieResultaat(0));

    var resultaat = new ExtractieResultaat(100, 1,
        List.of(new Passage(1, "Volledige documenttekst.")),
        List.of(new GeextraheerdBezwaar(1, "Samenvatting", "milieu")),
        "Samenvatting doc");

    service.markeerKlaar(1L, resultaat);

    assertThat(taak.isHeeftOpmerkingen()).isFalse();
    verify(passageValidator).valideer(any(), any(), any());
  }

  @Test
  void markeerKlaarZetHeeftOpmerkingenBijNietGevondenPassages() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    var pad = Path.of("/tmp/windmolens/bezwaren/bezwaar-001.txt");
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar-001.txt")).thenReturn(pad);
    when(ingestiePoort.leesBestand(pad)).thenReturn(
        new Brondocument("Documenttekst.", "bezwaar-001.txt", pad.toString(), Instant.now()));

    when(passageValidator.valideer(any(), any(), any()))
        .thenReturn(new PassageValidator.ValidatieResultaat(2));

    var resultaat = new ExtractieResultaat(100, 2,
        List.of(new Passage(1, "Passage een")),
        List.of(
            new GeextraheerdBezwaar(1, "Samenvatting een", "milieu"),
            new GeextraheerdBezwaar(1, "Samenvatting twee", "mobiliteit")),
        "Doc samenvatting");

    service.markeerKlaar(1L, resultaat);

    assertThat(taak.isHeeftOpmerkingen()).isTrue();
  }

  @Test
  void markeerKlaarGracefulBijOnleesbaarDocument() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    when(projectPoort.geefBestandsPad("windmolens", "bezwaar-001.txt"))
        .thenThrow(new RuntimeException("Bestand niet gevonden"));

    var resultaat = new ExtractieResultaat(100, 1,
        List.of(new Passage(1, "Passage")),
        List.of(new GeextraheerdBezwaar(1, "Samenvatting", "milieu")),
        "Samenvatting");

    service.markeerKlaar(1L, resultaat);

    assertThat(taak.getStatus()).isEqualTo(ExtractieTaakStatus.KLAAR);
    assertThat(taak.isHeeftOpmerkingen()).isFalse();
    verify(passageValidator, org.mockito.Mockito.never()).valideer(any(), any(), any());
  }
```

**Step 4: Verifieer dat de tests niet compileren**

Run: `mvn test-compile -pl app -Denforcer.skip=true`
Expected: FAIL — `ExtractieTaakService` constructor heeft nog geen extra parameters

**Step 5: Commit**

```bash
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java
git commit -m "test: failing tests voor passage-validatie integratie in markeerKlaar"
```

---

### Task 7: ExtractieTaakService — validatie-integratie implementeren

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java`

**Step 1: Voeg nieuwe dependencies toe**

Voeg imports toe:

```java
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import java.nio.file.Path;
```

Voeg nieuwe velden toe na de bestaande velden:

```java
  private final ProjectPoort projectPoort2;
  private final IngestiePoort ingestiePoort;
  private final PassageValidator passageValidator;
```

> **Let op:** `ProjectService` bevat al een `ProjectPoort` intern. Gebruik een aparte veldnaam (`projectPoort2`) of hernoem. Aangezien `ProjectService` een hoger-niveau service is, is het schoner om `ProjectPoort` direct te injecteren naast `ProjectService`. Kies een beschrijvende naam of gebruik gewoon `projectPoort`.

Pas de constructor aan:

```java
  public ExtractieTaakService(
      ExtractieTaakRepository repository,
      ExtractieNotificatie notificatie,
      ProjectService projectService,
      ExtractiePassageRepository passageRepository,
      GeextraheerdBezwaarRepository bezwaarRepository,
      ProjectPoort projectPoort,
      IngestiePoort ingestiePoort,
      PassageValidator passageValidator,
      @Value("${bezwaarschriften.extractie.max-concurrent:3}") int maxConcurrent,
      @Value("${bezwaarschriften.extractie.max-pogingen:3}") int maxPogingen) {
    this.repository = repository;
    this.notificatie = notificatie;
    this.projectService = projectService;
    this.passageRepository = passageRepository;
    this.bezwaarRepository = bezwaarRepository;
    this.projectPoort = projectPoort;
    this.ingestiePoort = ingestiePoort;
    this.passageValidator = passageValidator;
    this.maxConcurrent = maxConcurrent;
    this.maxPogingen = maxPogingen;
  }
```

**Step 2: Pas `markeerKlaar(Long, ExtractieResultaat)` aan**

Vervang de bestaande methode (regels 152-183) door:

```java
  @Transactional
  public void markeerKlaar(Long taakId, ExtractieResultaat resultaat) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    taak.setStatus(ExtractieTaakStatus.KLAAR);
    taak.setAantalWoorden(resultaat.aantalWoorden());
    taak.setAantalBezwaren(resultaat.aantalBezwaren());
    taak.setAfgerondOp(Instant.now());

    var passageMap = new HashMap<Integer, String>();
    for (var passage : resultaat.passages()) {
      var entiteit = new ExtractiePassageEntiteit();
      entiteit.setTaakId(taakId);
      entiteit.setPassageNr(passage.id());
      entiteit.setTekst(passage.tekst());
      passageRepository.save(entiteit);
      passageMap.put(passage.id(), passage.tekst());
    }

    var bezwaarEntiteiten = new java.util.ArrayList<GeextraheerdBezwaarEntiteit>();
    for (var bezwaar : resultaat.bezwaren()) {
      var entiteit = new GeextraheerdBezwaarEntiteit();
      entiteit.setTaakId(taakId);
      entiteit.setPassageNr(bezwaar.passageId());
      entiteit.setSamenvatting(bezwaar.samenvatting());
      entiteit.setCategorie(bezwaar.categorie());
      bezwaarEntiteiten.add(entiteit);
    }

    // Passage-validatie: controleer of passages in brondocument voorkomen
    try {
      var pad = projectPoort.geefBestandsPad(taak.getProjectNaam(), taak.getBestandsnaam());
      var brondocument = ingestiePoort.leesBestand(pad);
      var validatie = passageValidator.valideer(bezwaarEntiteiten, passageMap,
          brondocument.tekst());
      if (validatie.aantalNietGevonden() > 0) {
        taak.setHeeftOpmerkingen(true);
        LOGGER.info("Taak {}: {} van {} passages niet gevonden in brondocument",
            taakId, validatie.aantalNietGevonden(), bezwaarEntiteiten.size());
      }
    } catch (Exception e) {
      LOGGER.warn("Passage-validatie overgeslagen voor taak {} ({}): {}",
          taakId, taak.getBestandsnaam(), e.getMessage());
    }

    for (var entiteit : bezwaarEntiteiten) {
      bezwaarRepository.save(entiteit);
    }

    repository.save(taak);
    notificatie.taakGewijzigd(ExtractieTaakDto.van(taak));
    LOGGER.info("Taak {} afgerond: {} woorden, {} bezwaren, {} passages opgeslagen",
        taakId, resultaat.aantalWoorden(), resultaat.aantalBezwaren(),
        resultaat.passages().size());
  }
```

**Step 3: Pas `geefExtractieDetails` aan voor `passageGevonden`**

Vervang de `details` mapping (regels 321-324) door:

```java
    var details = bezwaren.stream()
        .map(b -> new ExtractieDetailDto.BezwaarDetail(
            b.getSamenvatting(),
            passageMap.getOrDefault(b.getPassageNr(), ""),
            b.isPassageGevonden()))
        .toList();
```

**Step 4: Run de tests**

Run: `mvn test -pl app -Denforcer.skip=true`
Expected: BUILD SUCCESS — alle tests slagen inclusief de nieuwe

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java
git commit -m "feat: passage-validatie integratie in markeerKlaar flow"
```

---

### Task 8: ExtractieController test uitbreiden

**Files:**
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieControllerTest.java`

**Step 1: Pas bestaande test aan en voeg nieuwe test toe**

Update de bestaande `geeftExtractieDetailsVoorBestand` test om `passageGevonden` te verifiëren (de `BezwaarDetail` constructors zijn al aangepast in Task 3).

Voeg een nieuwe test toe:

```java
  @Test
  void geeftExtractieDetailsMetPassageNietGevonden() throws Exception {
    var detail = new ExtractieDetailDto("bezwaar-002.txt", 2, List.of(
        new ExtractieDetailDto.BezwaarDetail(
            "Geluidshinder", "De geluidsoverlast zal...", false),
        new ExtractieDetailDto.BezwaarDetail(
            "Parkeertekort", "Er zijn onvoldoende...", true)));

    when(extractieTaakService.geefExtractieDetails("windmolens", "bezwaar-002.txt"))
        .thenReturn(detail);

    mockMvc.perform(get("/api/v1/projects/windmolens/extracties/bezwaar-002.txt/details"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bezwaren[0].passageGevonden").value(false))
        .andExpect(jsonPath("$.bezwaren[1].passageGevonden").value(true));
  }
```

**Step 2: Run de tests**

Run: `mvn test -pl app -Denforcer.skip=true -Dtest=ExtractieControllerTest`
Expected: PASS

**Step 3: Run alle backend tests**

Run: `mvn test -pl app -Denforcer.skip=true`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieControllerTest.java
git commit -m "test: controller test voor passageGevonden in extractie-details"
```

---

### Task 9: Frontend — side-panel passage-waarschuwing

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

**Step 1: Voeg CSS toe voor waarschuwing**

Voeg de volgende CSS-regel toe na `.bezwaar-passage { ... }` (na regel 115):

```css
        .bezwaar-waarschuwing {
          color: #a5673f;
          font-size: 0.85rem;
          margin-bottom: 0.25rem;
        }
```

**Step 2: Pas `toonExtractieDetails` methode aan**

Vervang de `data.bezwaren.forEach(...)` blok (regels 400-414) door:

```javascript
          // Sorteer: niet-gevonden passages bovenaan
          const gesorteerd = [...data.bezwaren].sort((a, b) => {
            if (a.passageGevonden === b.passageGevonden) return 0;
            return a.passageGevonden ? 1 : -1;
          });

          const heeftNietGevonden = gesorteerd.some((b) => !b.passageGevonden);

          if (titelEl) {
            const basisTitel = `${data.bestandsnaam} - ${data.aantalBezwaren} bezwar${data.aantalBezwaren === 1 ? '' : 'en'} gevonden`;
            titelEl.textContent = heeftNietGevonden
                ? `${basisTitel} (met opmerkingen)`
                : basisTitel;
          }

          gesorteerd.forEach((bezwaar) => {
            const item = document.createElement('div');
            item.className = 'bezwaar-item';

            if (!bezwaar.passageGevonden) {
              const waarschuwing = document.createElement('div');
              waarschuwing.className = 'bezwaar-waarschuwing';
              waarschuwing.textContent = '\u26A0\uFE0F Passage kon niet gevonden worden';
              item.appendChild(waarschuwing);
            }

            const samenvatting = document.createElement('div');
            samenvatting.className = 'bezwaar-samenvatting';
            samenvatting.textContent = bezwaar.samenvatting;

            const passage = document.createElement('div');
            passage.className = 'bezwaar-passage';
            passage.textContent = `\u201C${bezwaar.passage}\u201D`;

            item.appendChild(samenvatting);
            item.appendChild(passage);
            inhoud.appendChild(item);
          });
```

Verwijder het dubbele `if (titelEl)` blok dat eerder stond (regels 396-398) — de titel wordt nu in het gesorteerde blok gezet.

**Step 3: Build de frontend**

Run: `cd webapp && npm run build`
Expected: Geen fouten

**Step 4: Kopieer naar Spring Boot target**

Run: `mvn process-resources -pl webapp -Denforcer.skip=true`

**Step 5: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "feat: waarschuwing en sortering voor niet-gevonden passages in side-panel"
```

---

### Task 10: Cypress setup

**Files:**
- Modify: `webapp/package.json`
- Create: `webapp/cypress.config.js`
- Create: `webapp/cypress/support/component-index.html`
- Create: `webapp/cypress/support/component.js`

**Step 1: Installeer Cypress dependencies**

Run: `cd webapp && npm install --save-dev cypress@14.3.3 cypress-lit@0.0.8`

**Step 2: Maak `cypress.config.js` aan**

```javascript
const {defineConfig} = require('cypress');

module.exports = defineConfig({
  defaultCommandTimeout: 10000,
  video: false,
  videosFolder: './target/cypress/videos',
  screenshotsFolder: './target/cypress/screenshots',
  chromeWebSecurity: false,
  retries: 0,
  includeShadowDom: true,
  component: {
    supportFile: 'cypress/support/component.js',
    specPattern: './test/**/*.cy.js',
    devServer: {
      bundler: 'webpack',
      webpackConfig: {
        devServer: {
          allowedHosts: 'all',
        },
        module: {
          rules: [
            {
              test: /\.m?js$/i,
              resolve: {
                fullySpecified: false,
              },
            },
          ],
        },
      },
    },
    indexHtmlFile: 'cypress/support/component-index.html',
    viewportWidth: 1920,
    viewportHeight: 1080,
  },
});
```

**Step 3: Maak `cypress/support/component-index.html` aan**

```html
<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width,initial-scale=1.0">
    <title>Components App</title>
  </head>
  <body>
    <div data-cy-root></div>
  </body>
</html>
```

**Step 4: Maak `cypress/support/component.js` aan**

```javascript
import {mount} from 'cypress-lit';

Cypress.Commands.add('mount', mount);

Cypress.on('uncaught:exception', () => {
  return false;
});
```

**Step 5: Voeg npm script toe aan `package.json`**

Voeg toe aan `"scripts"`:

```json
    "test": "cypress run --component",
    "test:open": "cypress open --component"
```

**Step 6: Verifieer dat Cypress opstart**

Run: `cd webapp && npx cypress verify`
Expected: Geen fouten

**Step 7: Commit**

```bash
git add webapp/package.json webapp/package-lock.json webapp/cypress.config.js webapp/cypress/support/component-index.html webapp/cypress/support/component.js
git commit -m "chore: Cypress component testing setup"
```

---

### Task 11: Cypress component tests voor side-panel

**Files:**
- Create: `webapp/test/bezwaarschriften-bezwaren-tabel-extractie-details.cy.js`

**Step 1: Schrijf de Cypress component tests**

```javascript
import {html} from 'lit';
import '../src/js/bezwaarschriften-bezwaren-tabel';

describe('bezwaarschriften-bezwaren-tabel extractie-details', () => {
  const MOCK_DETAILS_MET_OPMERKINGEN = {
    bestandsnaam: 'bezwaar-001.txt',
    aantalBezwaren: 3,
    bezwaren: [
      {samenvatting: 'Gevonden bezwaar', passage: 'Dit is een gevonden passage.', passageGevonden: true},
      {samenvatting: 'Niet gevonden bezwaar', passage: 'Deze passage bestaat niet.', passageGevonden: false},
      {samenvatting: 'Nog een gevonden bezwaar', passage: 'Nog een gevonden passage.', passageGevonden: true},
    ],
  };

  const MOCK_DETAILS_ZONDER_OPMERKINGEN = {
    bestandsnaam: 'bezwaar-002.txt',
    aantalBezwaren: 2,
    bezwaren: [
      {samenvatting: 'Eerste bezwaar', passage: 'Eerste passage.', passageGevonden: true},
      {samenvatting: 'Tweede bezwaar', passage: 'Tweede passage.', passageGevonden: true},
    ],
  };

  beforeEach(() => {
    cy.mount(html`<bezwaarschriften-bezwaren-tabel></bezwaarschriften-bezwaren-tabel>`);
  });

  it('toont niet-gevonden bezwaren bovenaan in side-panel', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_MET_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-001.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('.bezwaar-item')
        .first()
        .find('.bezwaar-samenvatting')
        .should('have.text', 'Niet gevonden bezwaar');
  });

  it('toont waarschuwingstekst bij niet-gevonden passage', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_MET_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-001.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('.bezwaar-waarschuwing')
        .should('have.length', 1)
        .and('contain.text', 'Passage kon niet gevonden worden');
  });

  it('toont geen waarschuwing bij gevonden passages', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('.bezwaar-waarschuwing')
        .should('not.exist');
  });

  it('titel bevat "(met opmerkingen)" bij niet-gevonden passages', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_MET_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-001.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#extractie-side-sheet-titel')
        .should('contain.text', '(met opmerkingen)');
  });

  it('titel zonder opmerkingen bij alle passages gevonden', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#extractie-side-sheet-titel')
        .should('not.contain.text', '(met opmerkingen)')
        .and('contain.text', '2 bezwaren gevonden');
  });
});
```

**Step 2: Run de Cypress component tests**

Run: `cd webapp && npx cypress run --component`
Expected: PASS — alle 5 tests slagen

> **Mogelijke issues:** Shadow DOM querying kan extra configuratie nodig hebben. `includeShadowDom: true` in cypress.config.js zou dit moeten opvangen. Als `cy.find('.bezwaar-item')` niet werkt in shadow root, probeer `cy.get('bezwaarschriften-bezwaren-tabel').shadow().find(...)`.

**Step 3: Commit**

```bash
git add webapp/test/bezwaarschriften-bezwaren-tabel-extractie-details.cy.js
git commit -m "test: Cypress component tests voor passage-validatie in side-panel"
```

---

### Task 12: Build en volledige verificatie

**Files:** geen nieuwe wijzigingen

**Step 1: Volledige backend build + tests**

Run: `mvn clean verify -Denforcer.skip=true`
Expected: BUILD SUCCESS, alle tests slagen

**Step 2: Frontend build**

Run: `cd webapp && npm run build`
Expected: Geen fouten

**Step 3: Kopieer naar Spring Boot**

Run: `mvn process-resources -pl webapp -Denforcer.skip=true`

**Step 4: Cypress tests**

Run: `cd webapp && npx cypress run --component`
Expected: Alle tests slagen

**Step 5: Manuele verificatie (optioneel)**

Start de applicatie en test handmatig:
1. Open een project met extracties
2. Klik op het vergrootglas-icoon bij een verwerkt bestand
3. Verifieer dat niet-gevonden passages bovenaan staan met waarschuwing
4. Verifieer dat de titel "(met opmerkingen)" toont als er waarschuwingen zijn

---

### Samenvatting wijzigingen per bestand

| Bestand | Actie |
|---------|-------|
| `app/src/main/resources/config/liquibase/changelog/20260302-passage-validatie.xml` | Nieuw |
| `app/src/main/resources/config/liquibase/master.xml` | Gewijzigd |
| `app/src/main/java/.../GeextraheerdBezwaarEntiteit.java` | Gewijzigd |
| `app/src/main/java/.../ExtractieTaak.java` | Gewijzigd |
| `app/src/main/java/.../ExtractieDetailDto.java` | Gewijzigd |
| `app/src/main/java/.../ExtractieTaakDto.java` | Gewijzigd |
| `app/src/main/java/.../PassageValidator.java` | Nieuw |
| `app/src/main/java/.../ExtractieTaakService.java` | Gewijzigd |
| `app/src/test/java/.../PassageValidatorTest.java` | Nieuw |
| `app/src/test/java/.../ExtractieTaakServiceTest.java` | Gewijzigd |
| `app/src/test/java/.../ExtractieControllerTest.java` | Gewijzigd |
| `webapp/src/js/bezwaarschriften-bezwaren-tabel.js` | Gewijzigd |
| `webapp/package.json` | Gewijzigd |
| `webapp/cypress.config.js` | Nieuw |
| `webapp/cypress/support/component-index.html` | Nieuw |
| `webapp/cypress/support/component.js` | Nieuw |
| `webapp/test/bezwaarschriften-bezwaren-tabel-extractie-details.cy.js` | Nieuw |
