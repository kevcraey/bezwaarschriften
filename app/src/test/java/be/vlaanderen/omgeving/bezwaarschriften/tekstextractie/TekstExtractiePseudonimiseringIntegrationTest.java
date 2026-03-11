package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import static org.assertj.core.api.Assertions.assertThat;

import be.vlaanderen.omgeving.bezwaarschriften.BaseBezwaarschriftenIntegrationTest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * E2E integratietest: upload PDF → tekst-extractie → pseudonimisering → verificatie.
 *
 * <p>Gebruikt een echte Obscuro container via Testcontainers.
 */
@Testcontainers
class TekstExtractiePseudonimiseringIntegrationTest
    extends BaseBezwaarschriftenIntegrationTest {

  private static final Path testInputDir;

  static {
    try {
      testInputDir = Files.createTempDirectory("e2e-pseudonimisering");
      testInputDir.toFile().deleteOnExit();
    } catch (IOException e) {
      throw new RuntimeException("Kan test input directory niet aanmaken", e);
    }
  }

  @Container
  static final GenericContainer<?> obscuro =
      new GenericContainer<>("pseudonimiseren:test")
          .withExposedPorts(8000)
          .waitingFor(Wait.forHttp("/health").forStatusCode(200))
          .withStartupTimeout(Duration.ofMinutes(3));

  @DynamicPropertySource
  static void pseudonimiseringProperties(DynamicPropertyRegistry registry) {
    registry.add("bezwaarschriften.pseudonimisering.url",
        () -> "http://" + obscuro.getHost() + ":" + obscuro.getMappedPort(8000));
    registry.add("bezwaarschriften.input.folder", () -> testInputDir.toString());
  }

  @Autowired
  private TekstExtractieService tekstExtractieService;

  @Autowired
  private TekstExtractieTaakRepository repository;

  @Test
  void volledigeHappyPath_pdfMetPiiWordtGepseudonimiseerd() throws Exception {
    // 1. Maak test-PDF aan met bekende PII
    var pdfBytes = maakTestPdfMetPii(
        "Geachte heer Thomas De Smedt, "
        + "wonende te Kerkstraat 12 in 9000 Gent. "
        + "Uw rekeningnummer BE68 5390 0754 7034 is genoteerd. "
        + "Hierbij dien ik bezwaar in tegen de omgevingsvergunning "
        + "voor het bouwen van een meergezinswoning op het perceel "
        + "gelegen aan de Kerkstraat 14 te 9000 Gent. "
        + "De aanvraag is ingediend door Jan Peeters en werd "
        + "gepubliceerd in het Belgisch Staatsblad op 15 januari 2026. "
        + "Ik ben van mening dat het bouwproject een negatieve impact "
        + "zal hebben op de leefbaarheid van de buurt en de privacy "
        + "van de omwonenden. Het gebouw zal het zonlicht blokkeren "
        + "en de verkeersdruk in de straat aanzienlijk verhogen.");
    var bestandsnaam = "bezwaar-pii.pdf";
    var projectNaam = "e2e-test-" + System.currentTimeMillis();

    // 2. Maak project-directories aan en sla PDF op
    var origDir = testInputDir.resolve(projectNaam).resolve("bezwaren-orig");
    Files.createDirectories(origDir);
    var tekstDir = testInputDir.resolve(projectNaam).resolve("bezwaren-text");
    Files.createDirectories(tekstDir);
    Files.write(origDir.resolve(bestandsnaam), pdfBytes);

    // 3. Dien de extractie-taak in
    var taak = tekstExtractieService.indienen(projectNaam, bestandsnaam);
    assertThat(taak.getStatus()).isEqualTo(TekstExtractieTaakStatus.WACHTEND);

    // 4. Verwerk de taak (synchroon, via pakOpVoorVerwerking om versie-conflicten te vermijden)
    var opgepakteTaken = tekstExtractieService.pakOpVoorVerwerking();
    assertThat(opgepakteTaken).hasSize(1);
    tekstExtractieService.verwerkTaak(opgepakteTaken.get(0));

    // 5. Verifieer resultaat
    var bijgewerkt = repository.findById(taak.getId()).orElseThrow();

    // Status is KLAAR
    assertThat(bijgewerkt.getStatus()).isEqualTo(TekstExtractieTaakStatus.KLAAR);

    // Mapping-ID is opgeslagen (niet null, niet leeg)
    assertThat(bijgewerkt.getPseudonimiseringMappingId()).isNotNull();
    assertThat(bijgewerkt.getPseudonimiseringMappingId()).isNotEmpty();

    // Extractiemethode is ingevuld
    assertThat(bijgewerkt.getExtractieMethode()).isEqualTo(ExtractieMethode.DIGITAAL);

    // De opgeslagen tekst bevat GEEN originele PII meer
    // slaTekstOp replaces .pdf extension with .txt
    var tekstPad = tekstDir.resolve("bezwaar-pii.txt");
    assertThat(Files.exists(tekstPad))
        .as("Gepseudonimiseerde tekst moet opgeslagen zijn")
        .isTrue();
    var opgeslagenTekst = Files.readString(tekstPad);
    assertThat(opgeslagenTekst)
        .as("IBAN mag niet meer in de tekst staan")
        .doesNotContain("BE68 5390 0754 7034")
        .doesNotContain("BE68539007547034");
  }

  private byte[] maakTestPdfMetPii(String tekst) throws IOException {
    try (var document = new PDDocument()) {
      var page = new PDPage();
      document.addPage(page);
      try (var contentStream = new PDPageContentStream(document, page)) {
        contentStream.beginText();
        contentStream.setFont(
            new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        contentStream.newLineAtOffset(50, 700);
        var woorden = tekst.split(" ");
        var regel = new StringBuilder();
        for (var woord : woorden) {
          if (regel.length() + woord.length() > 70) {
            contentStream.showText(regel.toString());
            contentStream.newLineAtOffset(0, -15);
            regel = new StringBuilder();
          }
          if (regel.length() > 0) regel.append(" ");
          regel.append(woord);
        }
        if (regel.length() > 0) {
          contentStream.showText(regel.toString());
        }
        contentStream.endText();
      }
      var baos = new ByteArrayOutputStream();
      document.save(baos);
      return baos.toByteArray();
    }
  }
}
