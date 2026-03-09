package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
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

  @TempDir
  static Path tempDir;
  static Path testPdf;

  private final TekstKwaliteitsControle kwaliteitsControle =
      new TekstKwaliteitsControle();
  private final PdfTekstExtractor extractor =
      new PdfTekstExtractor(kwaliteitsControle, "/opt/homebrew/share/tessdata");

  @BeforeAll
  static void maakTestPdf() throws IOException {
    testPdf = tempDir.resolve("test.pdf");
    try (var doc = new PDDocument()) {
      var page = new PDPage();
      doc.addPage(page);
      try (var stream = new PDPageContentStream(doc, page)) {
        stream.beginText();
        stream.setFont(
            new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(50, 700);
        var tekst = "Dit is een bezwaarschrift over het voorgenomen "
            + "besluit van de overheid. ";
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
    TekstExtractieResultaat resultaat = extractor.extraheer(testPdf);

    assertThat(resultaat.methode()).isEqualTo(ExtractieMethode.DIGITAAL);
    assertThat(resultaat.tekst()).contains("bezwaarschrift");
    assertThat(resultaat.tekst()).isNotBlank();
  }

  @Test
  void gooidIoExceptionVoorOnbestaandBestand() {
    Path onbestaand = tempDir.resolve("bestaat-niet.pdf");

    assertThatThrownBy(() -> extractor.extraheer(onbestaand))
        .isInstanceOf(IOException.class);
  }

  @Test
  void normaliseertWitruimteCorrect() {
    String input = "woord1  woord2\t\twoord3   woord4";
    String resultaat = PdfTekstExtractor.normaliseerWitruimte(input);
    assertThat(resultaat).doesNotContain("  ");
    assertThat(resultaat).doesNotContain("\t");

    String metDrieNewlines = "alinea1\n\n\nalinea2\n\n\n\nalinea3";
    String genormaliseerd =
        PdfTekstExtractor.normaliseerWitruimte(metDrieNewlines);
    assertThat(genormaliseerd).doesNotContain("\n\n\n");
    assertThat(genormaliseerd).contains("\n\n");
  }

  @Test
  void normaliseertSpatiesRondNewlines() {
    String input = "regel1  \n  regel2";
    String resultaat = PdfTekstExtractor.normaliseerWitruimte(input);
    assertThat(resultaat).isEqualTo("regel1\nregel2");
  }

  @Test
  void normaliseertLeadingEnTrailingWitruimte() {
    String input = "  tekst met spaties  ";
    String resultaat = PdfTekstExtractor.normaliseerWitruimte(input);
    assertThat(resultaat).isEqualTo("tekst met spaties");
  }
}
