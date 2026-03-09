package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.PostConstruct;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Extraheert tekst uit PDF-bestanden via digitale extractie met
 * OCR-fallback.
 *
 * <p>Probeert eerst de tekst digitaal te extraheren met PDFBox.
 * Als de kwaliteitscontrole faalt, wordt Tesseract OCR ingezet
 * als fallback.
 */
@Component
public class PdfTekstExtractor {

  private static final Logger LOG =
      LoggerFactory.getLogger(PdfTekstExtractor.class);
  private static final int OCR_DPI = 300;

  private final TekstKwaliteitsControle kwaliteitsControle;
  private final String tessdataPath;

  /**
   * Maakt een nieuwe PdfTekstExtractor aan.
   *
   * @param kwaliteitsControle de kwaliteitscontrole voor geextraheerde tekst
   * @param tessdataPath pad naar de Tesseract tessdata directory
   */
  public PdfTekstExtractor(
      TekstKwaliteitsControle kwaliteitsControle,
      @Value("${bezwaarschriften.ocr.tessdata-path}") String tessdataPath) {
    this.kwaliteitsControle = kwaliteitsControle;
    this.tessdataPath = tessdataPath;
  }

  @PostConstruct
  void configureerJnaBibliotheekPad() {
    // tessdata-path is bv. /opt/homebrew/share/tessdata
    // native libs staan in /opt/homebrew/lib (twee niveaus omhoog)
    Path tessdataDir = Paths.get(tessdataPath);
    Path prefix = tessdataDir.getParent() != null
        ? tessdataDir.getParent().getParent() : null;
    Path libDir = prefix != null ? prefix.resolve("lib") : null;
    if (libDir != null && libDir.toFile().isDirectory()) {
      String bestaandPad = System.getProperty("jna.library.path", "");
      String nieuwPad = bestaandPad.isEmpty()
          ? libDir.toString()
          : bestaandPad + ":" + libDir;
      System.setProperty("jna.library.path", nieuwPad);
      LOG.info("JNA library pad geconfigureerd: {}", nieuwPad);
    }
  }

  /**
   * Extraheert tekst uit een PDF-bestand.
   *
   * <p>Probeert eerst digitale extractie. Als de kwaliteit onvoldoende is,
   * wordt OCR als fallback gebruikt.
   *
   * @param pdfPad pad naar het PDF-bestand
   * @return het extractieresultaat met tekst, methode en details
   * @throws IOException als het bestand niet gelezen kan worden of
   *     beide extractiemethodes falen
   */
  public TekstExtractieResultaat extraheer(Path pdfPad) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfPad.toFile())) {
      String digitaleTekst = new PDFTextStripper().getText(document);
      String genormaliseerd = normaliseerWitruimte(digitaleTekst);

      TekstKwaliteitsControle.Resultaat digitaalResultaat =
          kwaliteitsControle.controleer(genormaliseerd);

      if (digitaalResultaat.isValide()) {
        LOG.info("PDF digitaal geextraheerd: {}", pdfPad.getFileName());
        return new TekstExtractieResultaat(
            genormaliseerd,
            ExtractieMethode.DIGITAAL,
            "Digitale extractie geslaagd");
      }

      LOG.info("Digitale extractie onvoldoende voor {}: {}. "
              + "OCR-fallback wordt geprobeerd.",
          pdfPad.getFileName(), digitaalResultaat.reden());

      return probeerOcr(document, pdfPad, digitaalResultaat.reden());
    }
  }

  private TekstExtractieResultaat probeerOcr(
      PDDocument document, Path pdfPad, String digitaleReden)
      throws IOException {

    if (!isTesseractBeschikbaar()) {
      throw new OcrNietBeschikbaarException(
          "Tesseract is niet geinstalleerd. Digitale extractie faalde: "
              + digitaleReden);
    }

    try {
      String ocrTekst = voerOcrUit(document);
      String genormaliseerd = normaliseerWitruimte(ocrTekst);

      TekstKwaliteitsControle.Resultaat ocrResultaat =
          kwaliteitsControle.controleer(genormaliseerd);

      if (ocrResultaat.isValide()) {
        LOG.info("PDF via OCR geextraheerd: {}", pdfPad.getFileName());
        return new TekstExtractieResultaat(
            genormaliseerd,
            ExtractieMethode.OCR,
            "OCR-extractie geslaagd (digitaal faalde: "
                + digitaleReden + ")");
      }

      throw new IOException(String.format(
          "Beide extractiemethodes faalden voor %s. "
              + "Digitaal: %s. OCR: %s.",
          pdfPad.getFileName(), digitaleReden, ocrResultaat.reden()));

    } catch (TesseractException e) {
      throw new IOException(
          "OCR-extractie mislukt voor " + pdfPad.getFileName(), e);
    }
  }

  private String voerOcrUit(PDDocument document)
      throws IOException, TesseractException {

    PDFRenderer renderer = new PDFRenderer(document);
    Tesseract tesseract = new Tesseract();
    tesseract.setDatapath(tessdataPath);
    tesseract.setLanguage("nld+eng");

    int totaalPaginas = document.getNumberOfPages();
    LOG.info("OCR gestart: {} pagina('s) te verwerken", totaalPaginas);

    StringBuilder resultaat = new StringBuilder();
    for (int pagina = 0; pagina < totaalPaginas; pagina++) {
      LOG.info("OCR pagina {}/{} - rendering...", pagina + 1, totaalPaginas);
      BufferedImage afbeelding = renderer.renderImageWithDPI(
          pagina, OCR_DPI);
      LOG.info("OCR pagina {}/{} - herkenning...", pagina + 1, totaalPaginas);
      String paginaTekst = tesseract.doOCR(afbeelding);
      LOG.info("OCR pagina {}/{} - klaar ({} tekens)",
          pagina + 1, totaalPaginas, paginaTekst.length());
      resultaat.append(paginaTekst);
      if (pagina < totaalPaginas - 1) {
        resultaat.append("\n");
      }
    }
    return resultaat.toString();
  }

  /**
   * Controleert of Tesseract OCR beschikbaar is op het systeem.
   *
   * @return true als Tesseract geinstalleerd en uitvoerbaar is
   */
  boolean isTesseractBeschikbaar() {
    try {
      Process process = new ProcessBuilder("tesseract", "--version")
          .redirectErrorStream(true)
          .start();
      int exitCode = process.waitFor();
      return exitCode == 0;
    } catch (IOException | InterruptedException e) {
      return false;
    }
  }

  /**
   * Normaliseert witruimte in de gegeven tekst.
   *
   * @param tekst de te normaliseren tekst
   * @return tekst met genormaliseerde witruimte
   */
  static String normaliseerWitruimte(String tekst) {
    if (tekst == null) {
      return null;
    }
    // Meerdere spaties/tabs naar enkele spatie
    String resultaat = tekst.replaceAll("[\\t ]+", " ");
    // Spaties rond newlines verwijderen
    resultaat = resultaat.replaceAll(" *\\n *", "\n");
    // 3+ opeenvolgende newlines naar maximaal 2
    resultaat = resultaat.replaceAll("\\n{3,}", "\n\n");
    // Leading/trailing witruimte verwijderen
    return resultaat.strip();
  }
}
