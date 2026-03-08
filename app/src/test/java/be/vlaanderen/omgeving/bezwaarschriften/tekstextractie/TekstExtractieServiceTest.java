package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.project.ProjectPoort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TekstExtractieServiceTest {

  @Mock
  private TekstExtractieTaakRepository repository;

  @Mock
  private PdfTekstExtractor pdfExtractor;

  @Mock
  private TekstKwaliteitsControle kwaliteitsControle;

  @Mock
  private ProjectPoort projectPoort;

  private TekstExtractieService service;

  @BeforeEach
  void setUp() {
    service = new TekstExtractieService(
        repository, pdfExtractor, kwaliteitsControle, projectPoort, 2);
  }

  @Test
  void indienen_maaktTaakAanMetStatusWachtend() {
    var taak = new TekstExtractieTaak();
    taak.setId(1L);
    when(repository.save(any(TekstExtractieTaak.class))).thenReturn(taak);

    var resultaat = service.indienen("windmolens", "bezwaar.pdf");

    assertThat(resultaat.getId()).isEqualTo(1L);

    var captor = ArgumentCaptor.forClass(TekstExtractieTaak.class);
    verify(repository).save(captor.capture());
    var opgeslagen = captor.getValue();
    assertThat(opgeslagen.getProjectNaam()).isEqualTo("windmolens");
    assertThat(opgeslagen.getBestandsnaam()).isEqualTo("bezwaar.pdf");
    assertThat(opgeslagen.getStatus()).isEqualTo(TekstExtractieTaakStatus.WACHTEND);
    assertThat(opgeslagen.getAangemaaktOp()).isNotNull();
  }

  @Test
  void pakOpVoorVerwerking_geeftWachtendeTakenEnZetStatusOpBezig() {
    var taak1 = maakTaak(1L, "windmolens", "a.pdf", TekstExtractieTaakStatus.WACHTEND);
    var taak2 = maakTaak(2L, "windmolens", "b.pdf", TekstExtractieTaakStatus.WACHTEND);

    when(repository.countByStatus(TekstExtractieTaakStatus.BEZIG)).thenReturn(0L);
    when(repository.findByStatusOrderByAangemaaktOpAsc(TekstExtractieTaakStatus.WACHTEND))
        .thenReturn(List.of(taak1, taak2));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    var resultaat = service.pakOpVoorVerwerking();

    assertThat(resultaat).hasSize(2);
    assertThat(taak1.getStatus()).isEqualTo(TekstExtractieTaakStatus.BEZIG);
    assertThat(taak2.getStatus()).isEqualTo(TekstExtractieTaakStatus.BEZIG);
    assertThat(taak1.getVerwerkingGestartOp()).isNotNull();
    assertThat(taak2.getVerwerkingGestartOp()).isNotNull();
  }

  @Test
  void pakOpVoorVerwerking_respecteertMaxConcurrentLimiet() {
    var taak1 = maakTaak(1L, "windmolens", "a.pdf", TekstExtractieTaakStatus.WACHTEND);
    var taak2 = maakTaak(2L, "windmolens", "b.pdf", TekstExtractieTaakStatus.WACHTEND);
    var taak3 = maakTaak(3L, "windmolens", "c.pdf", TekstExtractieTaakStatus.WACHTEND);

    // maxConcurrent = 2, er is al 1 bezig
    when(repository.countByStatus(TekstExtractieTaakStatus.BEZIG)).thenReturn(1L);
    when(repository.findByStatusOrderByAangemaaktOpAsc(TekstExtractieTaakStatus.WACHTEND))
        .thenReturn(List.of(taak1, taak2, taak3));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    var resultaat = service.pakOpVoorVerwerking();

    // Slechts 1 slot beschikbaar (max 2, al 1 bezig)
    assertThat(resultaat).hasSize(1);
  }

  @Test
  void pakOpVoorVerwerking_geeftLegeLijstAlsGeenSlotsVrij() {
    when(repository.countByStatus(TekstExtractieTaakStatus.BEZIG)).thenReturn(2L);

    var resultaat = service.pakOpVoorVerwerking();

    assertThat(resultaat).isEmpty();
    verify(repository, never())
        .findByStatusOrderByAangemaaktOpAsc(any());
  }

  @Test
  void markeerKlaar_zetStatusKlaarEnExtractieMethode() {
    var taak = maakTaak(1L, "windmolens", "a.pdf", TekstExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.markeerKlaar(1L, ExtractieMethode.DIGITAAL);

    assertThat(taak.getStatus()).isEqualTo(TekstExtractieTaakStatus.KLAAR);
    assertThat(taak.getExtractieMethode()).isEqualTo(ExtractieMethode.DIGITAAL);
    assertThat(taak.getAfgerondOp()).isNotNull();
  }

  @Test
  void markeerMislukt_zetStatusMisluktEnFoutmelding() {
    var taak = maakTaak(1L, "windmolens", "a.pdf", TekstExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.markeerMislukt(1L, "Bestand niet gevonden");

    assertThat(taak.getStatus()).isEqualTo(TekstExtractieTaakStatus.MISLUKT);
    assertThat(taak.getFoutmelding()).isEqualTo("Bestand niet gevonden");
    assertThat(taak.getAfgerondOp()).isNotNull();
  }

  @Test
  void markeerOcrNietBeschikbaar_zetStatusOcrNietBeschikbaar() {
    var taak = maakTaak(1L, "windmolens", "a.pdf", TekstExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.markeerOcrNietBeschikbaar(1L, "Tesseract niet gevonden");

    assertThat(taak.getStatus()).isEqualTo(TekstExtractieTaakStatus.OCR_NIET_BESCHIKBAAR);
    assertThat(taak.getFoutmelding()).isEqualTo("Tesseract niet gevonden");
    assertThat(taak.getAfgerondOp()).isNotNull();
  }

  @Test
  void isTekstExtractieKlaar_geeftTrueWanneerStatusKlaar() {
    var taak = maakTaak(1L, "windmolens", "a.pdf", TekstExtractieTaakStatus.KLAAR);
    when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
        "windmolens", "a.pdf")).thenReturn(Optional.of(taak));

    assertThat(service.isTekstExtractieKlaar("windmolens", "a.pdf")).isTrue();
  }

  @Test
  void isTekstExtractieKlaar_geeftFalseWanneerStatusNietKlaar() {
    var taak = maakTaak(1L, "windmolens", "a.pdf", TekstExtractieTaakStatus.BEZIG);
    when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
        "windmolens", "a.pdf")).thenReturn(Optional.of(taak));

    assertThat(service.isTekstExtractieKlaar("windmolens", "a.pdf")).isFalse();
  }

  @Test
  void isTekstExtractieKlaar_geeftFalseWanneerGeenTaakBestaat() {
    when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
        "windmolens", "a.pdf")).thenReturn(Optional.empty());

    assertThat(service.isTekstExtractieKlaar("windmolens", "a.pdf")).isFalse();
  }

  @Test
  void verwerkTaak_verwerktPdfSuccesvol() throws IOException {
    var taak = maakTaak(1L, "windmolens", "bezwaar.pdf", TekstExtractieTaakStatus.BEZIG);
    var pad = Path.of("/tmp/bezwaar.pdf");
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar.pdf")).thenReturn(pad);
    when(pdfExtractor.extraheer(pad)).thenReturn(
        new TekstExtractieResultaat("Geextraheerde tekst", ExtractieMethode.DIGITAAL,
            "OK"));
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(taak);

    verify(projectPoort).slaTekstOp("windmolens", "bezwaar.pdf", "Geextraheerde tekst");
    assertThat(taak.getStatus()).isEqualTo(TekstExtractieTaakStatus.KLAAR);
    assertThat(taak.getExtractieMethode()).isEqualTo(ExtractieMethode.DIGITAAL);
  }

  @Test
  void verwerkTaak_verwerktTxtSuccesvol(@TempDir Path tempDir) throws IOException {
    var txtBestand = tempDir.resolve("bezwaar.txt");
    // Maak een tekst met voldoende woorden voor de kwaliteitscontrole
    var tekst = "Dit is een test tekst met voldoende woorden ".repeat(10);
    Files.writeString(txtBestand, tekst);

    var taak = maakTaak(1L, "windmolens", "bezwaar.txt", TekstExtractieTaakStatus.BEZIG);
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar.txt")).thenReturn(txtBestand);
    when(kwaliteitsControle.controleer(tekst))
        .thenReturn(TekstKwaliteitsControle.Resultaat.valide());
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(taak);

    verify(projectPoort).slaTekstOp("windmolens", "bezwaar.txt", tekst);
    assertThat(taak.getStatus()).isEqualTo(TekstExtractieTaakStatus.KLAAR);
  }

  @Test
  void verwerkTaak_txtMetOnvoldoendeKwaliteitWordtMislukt(@TempDir Path tempDir)
      throws IOException {
    var txtBestand = tempDir.resolve("slecht.txt");
    Files.writeString(txtBestand, "korte tekst");

    var taak = maakTaak(1L, "windmolens", "slecht.txt", TekstExtractieTaakStatus.BEZIG);
    when(projectPoort.geefBestandsPad("windmolens", "slecht.txt")).thenReturn(txtBestand);
    when(kwaliteitsControle.controleer("korte tekst"))
        .thenReturn(TekstKwaliteitsControle.Resultaat.ongeldig("Te weinig woorden"));
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(taak);

    verify(projectPoort, never()).slaTekstOp(anyString(), anyString(), anyString());
    assertThat(taak.getStatus()).isEqualTo(TekstExtractieTaakStatus.MISLUKT);
    assertThat(taak.getFoutmelding()).contains("Te weinig woorden");
  }

  @Test
  void verwerkTaak_ocrNietBeschikbaarWordtCorrectAfgehandeld() throws IOException {
    var taak = maakTaak(1L, "windmolens", "scan.pdf", TekstExtractieTaakStatus.BEZIG);
    var pad = Path.of("/tmp/scan.pdf");
    when(projectPoort.geefBestandsPad("windmolens", "scan.pdf")).thenReturn(pad);
    when(pdfExtractor.extraheer(pad))
        .thenThrow(new OcrNietBeschikbaarException("Tesseract niet gevonden"));
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(taak);

    assertThat(taak.getStatus()).isEqualTo(TekstExtractieTaakStatus.OCR_NIET_BESCHIKBAAR);
    assertThat(taak.getFoutmelding()).contains("Tesseract niet gevonden");
  }

  @Test
  void verwerkTaak_onverwachteFoutWordtMislukt() throws IOException {
    var taak = maakTaak(1L, "windmolens", "fout.pdf", TekstExtractieTaakStatus.BEZIG);
    var pad = Path.of("/tmp/fout.pdf");
    when(projectPoort.geefBestandsPad("windmolens", "fout.pdf")).thenReturn(pad);
    when(pdfExtractor.extraheer(pad)).thenThrow(new IOException("Bestand corrupt"));
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(taak);

    assertThat(taak.getStatus()).isEqualTo(TekstExtractieTaakStatus.MISLUKT);
    assertThat(taak.getFoutmelding()).contains("Bestand corrupt");
  }

  @Test
  void verwerkTaak_nietOndersteundBestandstype() {
    var taak = maakTaak(1L, "windmolens", "foto.jpg", TekstExtractieTaakStatus.BEZIG);
    var pad = Path.of("/tmp/foto.jpg");
    when(projectPoort.geefBestandsPad("windmolens", "foto.jpg")).thenReturn(pad);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(taak);

    assertThat(taak.getStatus()).isEqualTo(TekstExtractieTaakStatus.MISLUKT);
    assertThat(taak.getFoutmelding()).contains("Niet-ondersteund bestandstype");
  }

  private TekstExtractieTaak maakTaak(Long id, String projectNaam, String bestandsnaam,
      TekstExtractieTaakStatus status) {
    var taak = new TekstExtractieTaak();
    taak.setId(id);
    taak.setProjectNaam(projectNaam);
    taak.setBestandsnaam(bestandsnaam);
    taak.setStatus(status);
    return taak;
  }
}
