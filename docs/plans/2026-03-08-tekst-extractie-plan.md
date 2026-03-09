# Tekst-extractie uit PDF en TXT — Implementatieplan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** PDF's en TXT-bestanden omzetten naar platte tekst met kwaliteitscontrole en OCR-fallback, als asynchrone stap vóór de AI-bezwaarextractie.

**Architecture:** Nieuw package `tekstextractie` met eigen taak-entiteit, worker en service. Bestanden worden opgeslagen in `bezwaren-orig/` (origineel) en `bezwaren-text/` (geëxtraheerde tekst). Gate-mechanisme voorkomt dat AI-extractie start zonder geslaagde tekst-extractie.

**Tech Stack:** PDFBox (digitale PDF-extractie), Tess4j (OCR-fallback), Liquibase (schema), Lit web components (@domg-wc), Cypress (frontend tests).

**Referentiedocumenten:**
- Design: `docs/plans/2026-03-08-tekst-extractie-design.md`
- Richtlijnen: `richtlijnen/` (lees bij elke taak de relevante richtlijn)
- Frontend richtlijnen: `richtlijnen/frontend.md`

---

## Task 1: Maven dependencies toevoegen

**Files:**
- Modify: `app/pom.xml`

**Step 1: Voeg PDFBox en Tess4j dependencies toe aan `app/pom.xml`**

Voeg toe in de `<dependencies>` sectie:

```xml
<dependency>
  <groupId>org.apache.pdfbox</groupId>
  <artifactId>pdfbox</artifactId>
  <version>3.0.4</version>
</dependency>
<dependency>
  <groupId>net.sourceforge.tess4j</groupId>
  <artifactId>tess4j</artifactId>
  <version>5.13.0</version>
</dependency>
```

**Step 2: Verifieer dat het compileert**

Run: `mvn compile -pl app -DskipTests`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add app/pom.xml
git commit -m "feat: voeg PDFBox en Tess4j dependencies toe voor tekst-extractie"
```

---

## Task 2: TekstKwaliteitsControle

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstKwaliteitsControle.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstKwaliteitsControleTest.java`

**Step 1: Schrijf de failing tests**

Test-cases:
1. Tekst met genoeg woorden, goede ratios → valide
2. Tekst met minder dan 100 woorden → ongeldig (reden: te weinig woorden)
3. Tekst met teveel niet-alfanumerieke karakters (<70%) → ongeldig
4. Tekst met klinker ratio onder 20% → ongeldig (gibberish)
5. Tekst met klinker ratio boven 60% → ongeldig
6. Lege tekst → ongeldig
7. Null tekst → ongeldig

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class TekstKwaliteitsControleTest {

  private final TekstKwaliteitsControle controle = new TekstKwaliteitsControle();

  @Test
  void valideNederlandseTekst() {
    var tekst = "Dit is een voorbeeld van een bezwaarschrift dat voldoende woorden bevat. "
        .repeat(20); // ~200 woorden
    var resultaat = controle.controleer(tekst);
    assertThat(resultaat.isValide()).isTrue();
  }

  @Test
  void teWeinigWoorden() {
    var tekst = "Dit is te kort.";
    var resultaat = controle.controleer(tekst);
    assertThat(resultaat.isValide()).isFalse();
    assertThat(resultaat.reden()).contains("woorden");
  }

  @Test
  void teVeelSpecialeTekens() {
    // >30% niet-alfanumeriek
    var tekst = ("@#$%^& " + "woord ").repeat(100);
    var resultaat = controle.controleer(tekst);
    assertThat(resultaat.isValide()).isFalse();
    assertThat(resultaat.reden()).contains("alfanumeriek");
  }

  @Test
  void klinkerRatioTeLaag() {
    // Consonanten-only gibberish
    var tekst = ("brdfg hklmn prstv wxzcb dflgh " ).repeat(20);
    var resultaat = controle.controleer(tekst);
    assertThat(resultaat.isValide()).isFalse();
    assertThat(resultaat.reden()).contains("klinker");
  }

  @Test
  void klinkerRatioTeHoog() {
    // Bijna alleen klinkers
    var tekst = ("aeiou aeiou aeiou aeiou oioea ").repeat(20);
    var resultaat = controle.controleer(tekst);
    assertThat(resultaat.isValide()).isFalse();
    assertThat(resultaat.reden()).contains("klinker");
  }

  @Test
  void legeTekst() {
    var resultaat = controle.controleer("");
    assertThat(resultaat.isValide()).isFalse();
  }

  @Test
  void nullTekst() {
    var resultaat = controle.controleer(null);
    assertThat(resultaat.isValide()).isFalse();
  }
}
```

**Step 2: Run tests om te bevestigen dat ze falen**

Run: `mvn test -pl app -Dtest=TekstKwaliteitsControleTest`
Expected: FAIL (klasse bestaat niet)

**Step 3: Implementeer TekstKwaliteitsControle**

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import org.springframework.stereotype.Component;

@Component
public class TekstKwaliteitsControle {

  private static final int MIN_WOORDEN = 100;
  private static final double MIN_ALFANUMERIEK_RATIO = 0.70;
  private static final double MIN_KLINKER_RATIO = 0.20;
  private static final double MAX_KLINKER_RATIO = 0.60;
  private static final String KLINKERS = "aeiouAEIOU";

  public record Resultaat(boolean isValide, String reden) {
    public static Resultaat valide() {
      return new Resultaat(true, null);
    }
    public static Resultaat ongeldig(String reden) {
      return new Resultaat(false, reden);
    }
  }

  public Resultaat controleer(String tekst) {
    if (tekst == null || tekst.isBlank()) {
      return Resultaat.ongeldig("Tekst is leeg");
    }

    // Woordtelling
    var woorden = tekst.trim().split("\\s+");
    if (woorden.length < MIN_WOORDEN) {
      return Resultaat.ongeldig(
          String.format("Te weinig woorden: %d (minimum %d)", woorden.length, MIN_WOORDEN));
    }

    // Alfanumerieke ratio (excl. spaties)
    var zonderSpaties = tekst.replaceAll("\\s", "");
    if (zonderSpaties.isEmpty()) {
      return Resultaat.ongeldig("Tekst bevat alleen spaties");
    }
    long alfanumeriek = zonderSpaties.chars()
        .filter(Character::isLetterOrDigit)
        .count();
    double alfaRatio = (double) alfanumeriek / zonderSpaties.length();
    if (alfaRatio < MIN_ALFANUMERIEK_RATIO) {
      return Resultaat.ongeldig(
          String.format("Alfanumeriek ratio te laag: %.0f%% (minimum %.0f%%)",
              alfaRatio * 100, MIN_ALFANUMERIEK_RATIO * 100));
    }

    // Klinker ratio (t.o.v. letters)
    long letters = zonderSpaties.chars()
        .filter(Character::isLetter)
        .count();
    if (letters > 0) {
      long klinkers = zonderSpaties.chars()
          .filter(c -> KLINKERS.indexOf(c) >= 0)
          .count();
      double klinkerRatio = (double) klinkers / letters;
      if (klinkerRatio < MIN_KLINKER_RATIO || klinkerRatio > MAX_KLINKER_RATIO) {
        return Resultaat.ongeldig(
            String.format("Klinker ratio buiten bereik: %.0f%% (verwacht %d%%-%d%%)",
                klinkerRatio * 100,
                (int) (MIN_KLINKER_RATIO * 100),
                (int) (MAX_KLINKER_RATIO * 100)));
      }
    }

    return Resultaat.valide();
  }
}
```

**Step 4: Run tests**

Run: `mvn test -pl app -Dtest=TekstKwaliteitsControleTest`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstKwaliteitsControle.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstKwaliteitsControleTest.java
git commit -m "feat: deterministische kwaliteitscontrole voor geëxtraheerde tekst"
```

---

## Task 3: PdfTekstExtractor

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/ExtractieMethode.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieResultaat.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/PdfTekstExtractor.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/PdfTekstExtractorTest.java`
- Test resources: voeg een kleine test-PDF toe in `app/src/test/resources/testdata/`

**Step 1: Maak de enum en het resultaat-record**

```java
// ExtractieMethode.java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

public enum ExtractieMethode {
  DIGITAAL,
  OCR
}
```

```java
// TekstExtractieResultaat.java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

public record TekstExtractieResultaat(
    String tekst,
    ExtractieMethode methode,
    String details
) {}
```

**Step 2: Schrijf failing tests voor PdfTekstExtractor**

Test-cases:
1. Digitale PDF → tekst geëxtraheerd, methode DIGITAAL
2. Bestand dat niet bestaat → exception
3. Witruimte-normalisatie: dubbele spaties en lege regels worden opgeschoond

Maak een test-PDF aan via PDFBox in een `@BeforeAll`:

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfTekstExtractorTest {

  private final TekstKwaliteitsControle kwaliteitsControle = new TekstKwaliteitsControle();
  private final PdfTekstExtractor extractor = new PdfTekstExtractor(kwaliteitsControle);

  static Path testPdf;

  @TempDir
  static Path tempDir;

  @BeforeAll
  static void maakTestPdf() throws IOException {
    testPdf = tempDir.resolve("test.pdf");
    try (var doc = new PDDocument()) {
      var page = new PDPage();
      doc.addPage(page);
      try (var stream = new PDPageContentStream(doc, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(50, 700);
        // Genereer voldoende tekst voor kwaliteitscontrole
        var tekst = "Dit is een bezwaarschrift over het voorgenomen besluit van de overheid. ";
        for (int i = 0; i < 15; i++) {
          stream.showText(tekst);
          stream.newLineAtOffset(0, -15);
        }
        stream.endText();
      }
      doc.save(testPdf.toFile());
    }
  }

  @Test
  void extraheertTekstUitDigitalePdf() throws IOException {
    var resultaat = extractor.extraheer(testPdf);
    assertThat(resultaat.tekst()).contains("bezwaarschrift");
    assertThat(resultaat.methode()).isEqualTo(ExtractieMethode.DIGITAAL);
  }

  @Test
  void bestandNietGevonden() {
    var pad = Path.of("/niet/bestaand/bestand.pdf");
    assertThatThrownBy(() -> extractor.extraheer(pad))
        .isInstanceOf(IOException.class);
  }

  @Test
  void normaliseertWitruimte() throws IOException {
    var resultaat = extractor.extraheer(testPdf);
    // Geen dubbele spaties of lege regels
    assertThat(resultaat.tekst()).doesNotContain("  ");
    assertThat(resultaat.tekst()).doesNotContain("\n\n\n");
  }
}
```

**Step 3: Run tests om te bevestigen dat ze falen**

Run: `mvn test -pl app -Dtest=PdfTekstExtractorTest`
Expected: FAIL

**Step 4: Implementeer PdfTekstExtractor**

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PdfTekstExtractor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final int OCR_DPI = 300;

  private final TekstKwaliteitsControle kwaliteitsControle;

  public PdfTekstExtractor(TekstKwaliteitsControle kwaliteitsControle) {
    this.kwaliteitsControle = kwaliteitsControle;
  }

  public TekstExtractieResultaat extraheer(Path pdfPad) throws IOException {
    try (var document = Loader.loadPDF(pdfPad.toFile())) {
      // Stap 1: digitale extractie
      var stripper = new PDFTextStripper();
      var ruweTekst = stripper.getText(document);
      var tekst = normaliseerWitruimte(ruweTekst);

      var kwaliteit = kwaliteitsControle.controleer(tekst);
      if (kwaliteit.isValide()) {
        LOGGER.info("Digitale extractie geslaagd voor '{}'", pdfPad.getFileName());
        return new TekstExtractieResultaat(tekst, ExtractieMethode.DIGITAAL,
            "Digitale extractie geslaagd");
      }

      LOGGER.info("Digitale extractie gefaald voor '{}': {}. Fallback naar OCR.",
          pdfPad.getFileName(), kwaliteit.reden());

      // Stap 2: OCR-fallback
      return probeerOcr(document, pdfPad, kwaliteit.reden());
    }
  }

  private TekstExtractieResultaat probeerOcr(PDDocument document, Path pdfPad,
      String digitaleReden) throws IOException {
    if (!isTesseractBeschikbaar()) {
      LOGGER.warn("Tesseract niet beschikbaar voor OCR-fallback van '{}'",
          pdfPad.getFileName());
      throw new OcrNietBeschikbaarException(
          "Digitale extractie gefaald (" + digitaleReden
              + ") en Tesseract is niet beschikbaar voor OCR-fallback");
    }

    try {
      var ocrTekst = voerOcrUit(document);
      var tekst = normaliseerWitruimte(ocrTekst);

      var kwaliteit = kwaliteitsControle.controleer(tekst);
      if (kwaliteit.isValide()) {
        LOGGER.info("OCR-extractie geslaagd voor '{}'", pdfPad.getFileName());
        return new TekstExtractieResultaat(tekst, ExtractieMethode.OCR,
            "OCR-extractie geslaagd (digitaal gefaald: " + digitaleReden + ")");
      }

      throw new IOException(
          "Tekst-extractie mislukt. Digitaal: " + digitaleReden
              + ". OCR: " + kwaliteit.reden());
    } catch (TesseractException e) {
      throw new IOException("OCR-verwerking mislukt: " + e.getMessage(), e);
    }
  }

  private String voerOcrUit(PDDocument document) throws IOException, TesseractException {
    var tesseract = new Tesseract();
    tesseract.setLanguage("nld+eng");

    var renderer = new PDFRenderer(document);
    var sb = new StringBuilder();

    for (int pagina = 0; pagina < document.getNumberOfPages(); pagina++) {
      BufferedImage afbeelding = renderer.renderImageWithDPI(pagina, OCR_DPI);
      var paginaTekst = tesseract.doOCR(afbeelding);
      sb.append(paginaTekst);
      if (pagina < document.getNumberOfPages() - 1) {
        sb.append("\n");
      }
    }

    return sb.toString();
  }

  boolean isTesseractBeschikbaar() {
    try {
      var process = new ProcessBuilder("tesseract", "--version")
          .redirectErrorStream(true)
          .start();
      int exitCode = process.waitFor();
      return exitCode == 0;
    } catch (Exception e) {
      return false;
    }
  }

  static String normaliseerWitruimte(String tekst) {
    if (tekst == null) {
      return "";
    }
    return tekst
        .replaceAll("[\\t ]+", " ")           // meerdere spaties/tabs → één spatie
        .replaceAll(" ?\\n ?", "\n")           // spaties rond newlines
        .replaceAll("\\n{3,}", "\n\n")         // max 2 opeenvolgende newlines
        .strip();
  }
}
```

**Step 5: Maak OcrNietBeschikbaarException**

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

public class OcrNietBeschikbaarException extends RuntimeException {
  public OcrNietBeschikbaarException(String message) {
    super(message);
  }
}
```

**Step 6: Run tests**

Run: `mvn test -pl app -Dtest=PdfTekstExtractorTest`
Expected: ALL PASS

**Step 7: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/ExtractieMethode.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieResultaat.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/PdfTekstExtractor.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/OcrNietBeschikbaarException.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/PdfTekstExtractorTest.java
git commit -m "feat: PDF tekst-extractie met digitaal + OCR-fallback"
```

---

## Task 4: Folderstructuur aanpassen (ProjectPoort + adapter)

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectPoort.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapter.java`
- Modify: bestaande tests voor BestandssysteemProjectAdapter

De huidige code gebruikt `{project}/bezwaren/` als map. Dit moet worden gesplitst in:
- `{project}/bezwaren-orig/` — originele bestanden
- `{project}/bezwaren-text/` — geëxtraheerde tekst

**Step 1: Pas ProjectPoort aan — voeg methodes toe voor tekst-opslag**

Voeg aan `ProjectPoort` toe:

```java
/**
 * Slaat geëxtraheerde tekst op in de bezwaren-text-map van een project.
 */
void slaTekstOp(String projectNaam, String bestandsnaam, String tekst);

/**
 * Geeft het pad naar een tekst-bestand in de bezwaren-text-map.
 */
Path geefTekstBestandsPad(String projectNaam, String bestandsnaam);
```

**Step 2: Pas BestandssysteemProjectAdapter aan**

- Hernoem interne referenties van `bezwaren` → `bezwaren-orig`
- Implementeer `slaTekstOp()`: schrijft naar `{project}/bezwaren-text/{bestandsnaam}.txt`
- Implementeer `geefTekstBestandsPad()`
- `maakProjectAan()`: maakt zowel `bezwaren-orig` als `bezwaren-text` submappen aan
- `resolveEnValideerBezwarenPad()` → `resolveEnValideerOrigPad()`
- Voeg `resolveEnValideerTekstPad()` toe

**Step 3: Pas BestandssysteemIngestieAdapter aan**

De `leesBestand()` methode moet tekst lezen uit `bezwaren-text/` i.p.v. `bezwaren-orig/`. Dit wordt aangepast zodat het `.txt` extensie-check verwijderd wordt (want alle bestanden in `bezwaren-text/` zijn al tekst).

**Step 4: Pas tests aan en run**

Run: `mvn test -pl app`
Expected: ALL PASS (bestaande tests zullen falen op folder-namen, die moeten mee aangepast worden)

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectPoort.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BestandssysteemProjectAdapter.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/ingestie/BestandssysteemIngestieAdapter.java
git commit -m "refactor: splits bezwaren-map in bezwaren-orig en bezwaren-text"
```

---

## Task 5: TekstExtractieTaak entiteit + Liquibase migratie

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieTaakStatus.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieTaak.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieTaakRepository.java`
- Create: `app/src/main/resources/config/liquibase/changelog/20260308-tekst-extractie-taak.xml`
- Modify: `app/src/main/resources/config/liquibase/changelog/master.xml`

**Step 1: Maak de status enum**

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

public enum TekstExtractieTaakStatus {
  WACHTEND,
  BEZIG,
  KLAAR,
  MISLUKT,
  OCR_NIET_BESCHIKBAAR
}
```

**Step 2: Maak de JPA-entiteit**

Volg exact hetzelfde patroon als `ExtractieTaak`:

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tekst_extractie_taak")
public class TekstExtractieTaak {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "project_naam", nullable = false)
  private String projectNaam;

  @Column(name = "bestandsnaam", nullable = false)
  private String bestandsnaam;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private TekstExtractieTaakStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "extractie_methode")
  private ExtractieMethode extractieMethode;

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

  // Getters en setters (volg patroon van ExtractieTaak)
}
```

**Step 3: Maak de repository**

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TekstExtractieTaakRepository extends JpaRepository<TekstExtractieTaak, Long> {

  List<TekstExtractieTaak> findByStatusOrderByAangemaaktOpAsc(TekstExtractieTaakStatus status);

  long countByStatus(TekstExtractieTaakStatus status);

  Optional<TekstExtractieTaak> findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
      String projectNaam, String bestandsnaam);

  List<TekstExtractieTaak> findByProjectNaam(String projectNaam);

  List<TekstExtractieTaak> findByProjectNaamAndStatus(
      String projectNaam, TekstExtractieTaakStatus status);

  void deleteByProjectNaam(String projectNaam);

  void deleteByProjectNaamAndBestandsnaam(String projectNaam, String bestandsnaam);
}
```

**Step 4: Maak Liquibase changeset**

Bestand: `app/src/main/resources/config/liquibase/changelog/20260308-tekst-extractie-taak.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

  <changeSet id="20260308-1" author="bezwaarschriften">
    <createTable tableName="tekst_extractie_taak">
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
      <column name="extractie_methode" type="varchar(50)"/>
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

**Step 5: Voeg changeset toe aan master.xml**

Voeg onderaan toe:
```xml
<include file="config/liquibase/changelog/20260308-tekst-extractie-taak.xml"/>
```

**Step 6: Verifieer compilatie**

Run: `mvn compile -pl app -DskipTests`
Expected: BUILD SUCCESS

**Step 7: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieTaakStatus.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieTaak.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieTaakRepository.java \
       app/src/main/resources/config/liquibase/changelog/20260308-tekst-extractie-taak.xml \
       app/src/main/resources/config/liquibase/changelog/master.xml
git commit -m "feat: TekstExtractieTaak entiteit en Liquibase migratie"
```

---

## Task 6: TekstExtractieService + TekstExtractieWorker

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieService.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieWorker.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieServiceTest.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/ExtractieConfig.java` (nieuwe executor bean)

**Step 1: Schrijf failing tests voor TekstExtractieService**

Test-cases:
1. `indienen()` maakt een taak aan met status WACHTEND
2. `pakOpVoorVerwerking()` zet status naar BEZIG
3. `markeerKlaar()` zet status naar KLAAR met methode
4. `markeerMislukt()` zet status naar MISLUKT met foutmelding
5. `markeerOcrNietBeschikbaar()` zet status naar OCR_NIET_BESCHIKBAAR

**Step 2: Implementeer TekstExtractieService**

Volg het patroon van `ExtractieTaakService`, maar eenvoudiger:

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TekstExtractieService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final TekstExtractieTaakRepository repository;
  private final PdfTekstExtractor pdfExtractor;
  private final TekstKwaliteitsControle kwaliteitsControle;
  private final be.vlaanderen.omgeving.bezwaarschriften.project.ProjectPoort projectPoort;
  private final int maxConcurrent;

  public TekstExtractieService(
      TekstExtractieTaakRepository repository,
      PdfTekstExtractor pdfExtractor,
      TekstKwaliteitsControle kwaliteitsControle,
      be.vlaanderen.omgeving.bezwaarschriften.project.ProjectPoort projectPoort,
      @Value("${bezwaarschriften.tekst-extractie.max-concurrent:2}") int maxConcurrent) {
    this.repository = repository;
    this.pdfExtractor = pdfExtractor;
    this.kwaliteitsControle = kwaliteitsControle;
    this.projectPoort = projectPoort;
    this.maxConcurrent = maxConcurrent;
  }

  @Transactional
  public TekstExtractieTaak indienen(String projectNaam, String bestandsnaam) {
    var taak = new TekstExtractieTaak();
    taak.setProjectNaam(projectNaam);
    taak.setBestandsnaam(bestandsnaam);
    taak.setStatus(TekstExtractieTaakStatus.WACHTEND);
    taak.setAangemaaktOp(Instant.now());
    var opgeslagen = repository.save(taak);
    LOGGER.info("Tekst-extractie taak ingediend: project='{}', bestand='{}'",
        projectNaam, bestandsnaam);
    return opgeslagen;
  }

  @Transactional
  public List<TekstExtractieTaak> pakOpVoorVerwerking() {
    long aantalBezig = repository.countByStatus(TekstExtractieTaakStatus.BEZIG);
    int beschikbareSlots = maxConcurrent - (int) aantalBezig;
    if (beschikbareSlots <= 0) {
      return List.of();
    }

    var wachtend = repository.findByStatusOrderByAangemaaktOpAsc(
        TekstExtractieTaakStatus.WACHTEND);
    var opTePakken = wachtend.stream().limit(beschikbareSlots).toList();

    for (var taak : opTePakken) {
      taak.setStatus(TekstExtractieTaakStatus.BEZIG);
      taak.setVerwerkingGestartOp(Instant.now());
      repository.save(taak);
    }

    return opTePakken;
  }

  public void verwerkTaak(TekstExtractieTaak taak) {
    var bestandsnaam = taak.getBestandsnaam();
    var projectNaam = taak.getProjectNaam();

    try {
      var origPad = projectPoort.geefBestandsPad(projectNaam, bestandsnaam);

      String tekst;
      ExtractieMethode methode;

      if (bestandsnaam.toLowerCase().endsWith(".pdf")) {
        var resultaat = pdfExtractor.extraheer(origPad);
        tekst = resultaat.tekst();
        methode = resultaat.methode();
        LOGGER.info("PDF '{}' verwerkt via {}: {}", bestandsnaam, methode, resultaat.details());
      } else {
        // Tekst-bestand: lees en controleer kwaliteit
        tekst = Files.readString(origPad, StandardCharsets.UTF_8);
        var kwaliteit = kwaliteitsControle.controleer(tekst);
        if (!kwaliteit.isValide()) {
          markeerMislukt(taak.getId(),
              "Tekst-kwaliteitscontrole gefaald: " + kwaliteit.reden());
          return;
        }
        methode = ExtractieMethode.DIGITAAL;
        LOGGER.info("TXT '{}' verwerkt via digitale extractie", bestandsnaam);
      }

      // Sla tekst op
      var tekstBestandsnaam = bestandsnaam.replaceAll("\\.[^.]+$", "") + ".txt";
      projectPoort.slaTekstOp(projectNaam, tekstBestandsnaam, tekst);

      markeerKlaar(taak.getId(), methode);

    } catch (OcrNietBeschikbaarException e) {
      markeerOcrNietBeschikbaar(taak.getId(), e.getMessage());
    } catch (Exception e) {
      LOGGER.error("Tekst-extractie mislukt voor '{}': {}", bestandsnaam, e.getMessage(), e);
      markeerMislukt(taak.getId(), e.getMessage());
    }
  }

  @Transactional
  public void markeerKlaar(Long taakId, ExtractieMethode methode) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    taak.setStatus(TekstExtractieTaakStatus.KLAAR);
    taak.setExtractieMethode(methode);
    taak.setAfgerondOp(Instant.now());
    repository.save(taak);
    LOGGER.info("Tekst-extractie taak {} afgerond via {}", taakId, methode);
  }

  @Transactional
  public void markeerMislukt(Long taakId, String foutmelding) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    taak.setStatus(TekstExtractieTaakStatus.MISLUKT);
    taak.setFoutmelding(foutmelding);
    taak.setAfgerondOp(Instant.now());
    repository.save(taak);
    LOGGER.error("Tekst-extractie taak {} mislukt: {}", taakId, foutmelding);
  }

  @Transactional
  public void markeerOcrNietBeschikbaar(Long taakId, String foutmelding) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    taak.setStatus(TekstExtractieTaakStatus.OCR_NIET_BESCHIKBAAR);
    taak.setFoutmelding(foutmelding);
    taak.setAfgerondOp(Instant.now());
    repository.save(taak);
    LOGGER.warn("Tekst-extractie taak {} - OCR niet beschikbaar: {}", taakId, foutmelding);
  }

  public boolean isTekstExtractieKlaar(String projectNaam, String bestandsnaam) {
    return repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
            projectNaam, bestandsnaam)
        .map(t -> t.getStatus() == TekstExtractieTaakStatus.KLAAR)
        .orElse(false);
  }
}
```

**Step 3: Implementeer TekstExtractieWorker**

Volg exact het patroon van `ExtractieWorker`:

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class TekstExtractieWorker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final TekstExtractieService service;
  private final ThreadPoolTaskExecutor executor;

  public TekstExtractieWorker(TekstExtractieService service,
      @Qualifier("tekstExtractieExecutor") ThreadPoolTaskExecutor executor) {
    this.service = service;
    this.executor = executor;
  }

  @Scheduled(fixedDelay = 1000)
  public void verwerkTaken() {
    var taken = service.pakOpVoorVerwerking();
    for (var taak : taken) {
      executor.submit(() -> {
        try {
          service.verwerkTaak(taak);
        } catch (Exception e) {
          LOGGER.error("Onverwachte fout bij tekst-extractie taak {}: {}",
              taak.getId(), e.getMessage(), e);
          try {
            service.markeerMislukt(taak.getId(), e.getMessage());
          } catch (Exception ex) {
            LOGGER.error("Kon taak {} niet als mislukt markeren", taak.getId(), ex);
          }
        }
      });
    }
  }
}
```

**Step 4: Voeg thread pool executor toe aan ExtractieConfig**

Voeg aan `ExtractieConfig.java` toe:

```java
@Bean
public ThreadPoolTaskExecutor tekstExtractieExecutor(
    @Value("${bezwaarschriften.tekst-extractie.max-concurrent:2}") int maxConcurrent) {
  var executor = new ThreadPoolTaskExecutor();
  executor.setCorePoolSize(maxConcurrent);
  executor.setMaxPoolSize(maxConcurrent);
  executor.setThreadNamePrefix("tekst-extractie-");
  executor.initialize();
  return executor;
}
```

**Step 5: Run tests**

Run: `mvn test -pl app`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieService.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieWorker.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/ExtractieConfig.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieServiceTest.java
git commit -m "feat: TekstExtractieService en async worker voor tekst-extractie"
```

---

## Task 7: Upload-flow aanpassen + tekst-extractie automatisch starten

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java`
- Modify: tests voor ProjectService

**Step 1: Pas `isTxtBestand()` aan naar `isOndersteundFormaat()`**

Accepteer `.txt` en `.pdf`:

```java
private boolean isOndersteundFormaat(String bestandsnaam) {
  var lower = bestandsnaam.toLowerCase();
  return lower.endsWith(".txt") || lower.endsWith(".pdf");
}
```

**Step 2: Pas `uploadBezwaren()` aan**

- Vervang `isTxtBestand()` door `isOndersteundFormaat()`
- Sla bestanden op via `projectPoort.slaBestandOp()` (die nu naar `bezwaren-orig/` schrijft)
- Na succesvolle opslag: roep `tekstExtractieService.indienen()` aan voor elk bestand

Injecteer `TekstExtractieService` in `ProjectService` constructor.

**Step 3: Pas `geefBezwaren()` aan**

- Haal ook de tekst-extractie taak op naast de extractie-taak
- Als tekst-extractie niet KLAAR is, toon de tekst-extractie status
- Als tekst-extractie KLAAR is, toon de AI-extractie status
- Voeg `extractieMethode` toe aan het resultaat

**Step 4: Pas tests aan en run**

Run: `mvn test -pl app`
Expected: ALL PASS

**Step 5: Commit**

```bash
git commit -m "feat: upload accepteert PDF + start automatisch tekst-extractie"
```

---

## Task 8: Gate-mechanisme in ExtractieTaakService

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java`
- Create of modify: tests

**Step 1: Schrijf failing test**

Test dat `indienen()` een exception gooit als tekst-extractie niet KLAAR is.

**Step 2: Voeg gate-check toe aan `ExtractieTaakService.indienen()`**

Injecteer `TekstExtractieService` en controleer voor elk bestand:

```java
if (!tekstExtractieService.isTekstExtractieKlaar(projectNaam, bestandsnaam)) {
  throw new IllegalStateException(
      "Tekst-extractie niet voltooid voor bestand: " + bestandsnaam);
}
```

**Step 3: Pas `verwerkOnafgeronde()` aan**

Filter TODO-documenten zodat alleen bestanden met KLAAR tekst-extractie worden meegenomen.

**Step 4: Pas `IngestiePoort` / adapter aan**

`leesBestand()` in `BestandssysteemIngestieAdapter` moet nu lezen uit `bezwaren-text/` via `projectPoort.geefTekstBestandsPad()`. Verwijder de `.txt`-extensie check.

**Step 5: Run tests**

Run: `mvn test -pl app`
Expected: ALL PASS

**Step 6: Commit**

```bash
git commit -m "feat: gate-mechanisme - AI-extractie vereist voltooide tekst-extractie"
```

---

## Task 9: API en DTO aanpassingen

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestand.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestandStatus.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java` (inner records)

**Step 1: Voeg `extractieMethode` toe aan `BezwaarBestand`**

```java
public record BezwaarBestand(
    String bestandsnaam,
    BezwaarBestandStatus status,
    Integer aantalWoorden,
    Integer aantalBezwaren,
    boolean heeftPassagesDieNietInTekstVoorkomen,
    boolean heeftManueel,
    String extractieMethode
) {
  public BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status) {
    this(bestandsnaam, status, null, null, false, false, null);
  }
}
```

**Step 2: Voeg statussen toe aan `BezwaarBestandStatus`**

```java
public enum BezwaarBestandStatus {
  TODO,
  TEKST_EXTRACTIE_WACHTEND,
  TEKST_EXTRACTIE_BEZIG,
  TEKST_EXTRACTIE_KLAAR,
  TEKST_EXTRACTIE_MISLUKT,
  TEKST_EXTRACTIE_OCR_NIET_BESCHIKBAAR,
  WACHTEND,
  BEZIG,
  EXTRACTIE_KLAAR,
  FOUT,
  NIET_ONDERSTEUND
}
```

**Step 3: Voeg `extractieMethode` toe aan `BezwaarBestandDto`**

```java
record BezwaarBestandDto(
    String bestandsnaam,
    String status,
    Integer aantalWoorden,
    Integer aantalBezwaren,
    boolean heeftPassagesDieNietInTekstVoorkomen,
    String extractieMethode
) {}
```

**Step 4: Pas `statusNaarString()` en `van()` aan**

Voeg vertalingen toe voor de nieuwe statussen en neem `extractieMethode` mee.

**Step 5: Run tests en build**

Run: `mvn test -pl app`
Expected: ALL PASS

**Step 6: Commit**

```bash
git commit -m "feat: API uitgebreid met extractieMethode en tekst-extractie statussen"
```

---

## Task 10: Frontend — kolom "Methode" in documententabel

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`
- Modify: eventuele CSS

**Step 1: Voeg `vl-rich-data-field` toe voor methode**

Voeg na de `aantalBezwaren` kolom toe:

```html
<vl-rich-data-field name="extractieMethode" label="Methode" sortable></vl-rich-data-field>
```

**Step 2: Voeg renderer toe in `_configureerRenderers()`**

In de switch-case in `_configureerRenderers()`, voeg toe:

```javascript
case 'extractieMethode':
  field.renderer = (value) => {
    const wrapper = document.createElement('span');
    if (value === 'DIGITAAL') {
      wrapper.textContent = 'Digitaal';
    } else if (value === 'OCR') {
      wrapper.textContent = 'OCR';
    } else {
      wrapper.textContent = '-';
    }
    return wrapper;
  };
  break;
```

**Step 3: Pas status-renderer aan voor nieuwe statussen**

Voeg cases toe voor `tekst-extractie-wachtend`, `tekst-extractie-bezig`, `tekst-extractie-klaar`, `tekst-extractie-mislukt`, `tekst-extractie-ocr-niet-beschikbaar`.

**Step 4: Pas gate-logica aan in UI**

De "Start extractie" knop moet disabled zijn als de status geen `tekst-extractie-klaar` of latere status is. Pas `_isDisabled()` aan.

**Step 5: Build en test**

Run: `cd webapp && npm run build`
Run: `mvn process-resources -pl webapp -Denforcer.skip=true`
Expected: BUILD SUCCESS

**Step 6: Commit**

```bash
git commit -m "feat: kolom Methode in documententabel + tekst-extractie statussen"
```

---

## Task 11: Frontend tests (Cypress)

**Files:**
- Create of modify: Cypress component tests in `webapp/cypress/`

**Step 1: Schrijf Cypress tests**

Test-cases:
1. Documententabel toont "Methode" kolom
2. Methode toont "Digitaal" voor digitaal verwerkte documenten
3. Methode toont "OCR" voor OCR-verwerkte documenten
4. Methode toont "-" voor niet-verwerkte documenten
5. "Start extractie" knop is disabled bij mislukte tekst-extractie
6. Status-pill toont correcte tekst voor tekst-extractie statussen

**Step 2: Run Cypress tests**

Run: `cd webapp && npm test`
Expected: ALL PASS

**Step 3: Commit**

```bash
git commit -m "test: Cypress tests voor methode-kolom en tekst-extractie statussen"
```

---

## Task 12: Documentatie

**Files:**
- Create: `docs/text-extractie.md`
- Modify: `docs/c4-c2-containers.md` (C2 diagram bijwerken)

**Step 1: Schrijf `docs/text-extractie.md`**

Documenteer:
- Doel en werking van tekst-extractie
- Kwaliteitscriteria (3 checks met drempelwaarden)
- Verwerkingsflow (digitaal → kwaliteitscontrole → OCR-fallback)
- Foutstatussen en hun betekenis
- Folderstructuur (bezwaren-orig / bezwaren-text)
- Traceability (extractiemethode per document)
- Vereisten (Tesseract voor OCR)

**Step 2: Werk C2 diagram bij**

Voeg `tekstextractie` component toe aan het C2 container diagram in `docs/c4-c2-containers.md`.

**Step 3: Commit**

```bash
git commit -m "docs: text-extractie documentatie en C2 diagram update"
```

---

## Task 13: Integratietest en volledige build

**Step 1: Run volledige backend build**

Run: `mvn clean install` (vereist Docker voor Testcontainers)
Expected: BUILD SUCCESS

**Step 2: Run frontend build**

Run: `cd webapp && npm run build && npm test`
Expected: BUILD SUCCESS, ALL TESTS PASS

**Step 3: Handmatig testen (lokaal)**

1. Start applicatie: `mvn spring-boot:run -pl app -Pdev`
2. Upload een PDF via de UI
3. Verifieer: bestand verschijnt in documententabel met status "Tekst extractie bezig"
4. Wacht tot status "Tekst extractie klaar" + methode kolom toont "Digitaal" of "OCR"
5. Start AI-extractie → moet nu werken
6. Upload een corrupt/lege PDF → status moet "Mislukt" worden
7. Verifieer: "Start extractie" knop is disabled voor mislukte documenten

**Step 4: Final commit indien nodig**

---

## Test- en verificatieplan

| Scenario | Verwacht resultaat |
|---|---|
| Upload .txt met goede tekst | Tekst-extractie KLAAR, methode Digitaal |
| Upload .txt met gibberish | Tekst-extractie MISLUKT |
| Upload .pdf met digitale tekst | Tekst-extractie KLAAR, methode Digitaal |
| Upload .pdf met scan (zonder Tesseract) | OCR_NIET_BESCHIKBAAR |
| Upload .pdf met scan (met Tesseract) | Tekst-extractie KLAAR, methode OCR |
| Upload .docx | Upload geweigerd (niet-ondersteund formaat) |
| Start AI-extractie zonder tekst-extractie | Geweigerd (gate-check) |
| Start AI-extractie na geslaagde tekst-extractie | OK |
| Verwijder document | Zowel orig als text versie verwijderd |
| Methode-kolom in tabel | Toont "Digitaal", "OCR", of "-" |
| Status-pill tekst-extractie | Toont juiste kleur en tekst per status |
