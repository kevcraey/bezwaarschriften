package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarBestandEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarBestandRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarBestandStatus;
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

  @Mock
  private BezwaarBestandRepository bezwaarBestandRepository;

  @Mock
  private TekstExtractieNotificatie notificatie;

  @Mock
  private PseudonimiseringPoort pseudonimiseringPoort;

  private TekstExtractieService service;

  @BeforeEach
  void setUp() {
    service = new TekstExtractieService(
        repository, pdfExtractor, kwaliteitsControle, projectPoort,
        pseudonimiseringPoort, bezwaarBestandRepository, notificatie, 2);
  }

  @Test
  void indienen_maaktTaakAanMetStatusWachtend() {
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> {
      TekstExtractieTaak t = i.getArgument(0);
      t.setId(1L);
      return t;
    });

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
    when(pseudonimiseringPoort.pseudonimiseer("Geextraheerde tekst")).thenReturn(
        new PseudonimiseringResultaat("Geextraheerde tekst", "stub-mapping-id"));
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
    when(pseudonimiseringPoort.pseudonimiseer(tekst)).thenReturn(
        new PseudonimiseringResultaat(tekst, "stub-mapping-id"));
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

  @Test
  void geefGeextraheerdetekstLeestVanBestandssysteem(@TempDir Path tempDir) throws IOException {
    var taak = new TekstExtractieTaak();
    taak.setStatus(TekstExtractieTaakStatus.KLAAR);
    when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
        "windmolens", "bezwaar.pdf"))
        .thenReturn(Optional.of(taak));
    var pad = tempDir.resolve("test-tekst.txt");
    Files.writeString(pad, "Testinhoud");
    when(projectPoort.geefTekstBestandsPad("windmolens", "bezwaar.pdf"))
        .thenReturn(pad);

    var resultaat = service.geefGeextraheerdetekst("windmolens", "bezwaar.pdf");

    assertThat(resultaat).isEqualTo("Testinhoud");
  }

  @Test
  void geefGeextraheerdetekstRetourneertNullAlsTaakNietKlaar() {
    var taak = new TekstExtractieTaak();
    taak.setStatus(TekstExtractieTaakStatus.BEZIG);
    when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
        "windmolens", "bezwaar.pdf"))
        .thenReturn(Optional.of(taak));

    var resultaat = service.geefGeextraheerdetekst("windmolens", "bezwaar.pdf");

    assertThat(resultaat).isNull();
  }

  @Test
  void geefGeextraheerdetekstRetourneertNullAlsGeenTaak() {
    when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
        "windmolens", "bezwaar.pdf"))
        .thenReturn(Optional.empty());

    var resultaat = service.geefGeextraheerdetekst("windmolens", "bezwaar.pdf");

    assertThat(resultaat).isNull();
  }

  @Test
  void indienen_stuurtWebSocketNotificatie() {
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> {
      TekstExtractieTaak t = i.getArgument(0);
      t.setId(1L);
      return t;
    });

    service.indienen("windmolens", "bezwaar.pdf");

    var captor = ArgumentCaptor.forClass(TekstExtractieTaakDto.class);
    verify(notificatie).tekstExtractieTaakGewijzigd(captor.capture());
    var dto = captor.getValue();
    assertThat(dto.status()).isEqualTo("tekst-extractie-wachtend");
    assertThat(dto.projectNaam()).isEqualTo("windmolens");
    assertThat(dto.bestandsnaam()).isEqualTo("bezwaar.pdf");
  }

  @Test
  void markeerKlaar_stuurtWebSocketNotificatie() {
    var taak = maakTaak(1L, "windmolens", "a.pdf", TekstExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.markeerKlaar(1L, ExtractieMethode.DIGITAAL);

    var captor = ArgumentCaptor.forClass(TekstExtractieTaakDto.class);
    verify(notificatie).tekstExtractieTaakGewijzigd(captor.capture());
    assertThat(captor.getValue().status()).isEqualTo("tekst-extractie-klaar");
  }

  @Test
  void pakOpVoorVerwerking_stuurtWebSocketNotificatiePerTaak() {
    var taak1 = maakTaak(1L, "windmolens", "a.pdf", TekstExtractieTaakStatus.WACHTEND);
    when(repository.countByStatus(TekstExtractieTaakStatus.BEZIG)).thenReturn(0L);
    when(repository.findByStatusOrderByAangemaaktOpAsc(TekstExtractieTaakStatus.WACHTEND))
        .thenReturn(List.of(taak1));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.pakOpVoorVerwerking();

    var captor = ArgumentCaptor.forClass(TekstExtractieTaakDto.class);
    verify(notificatie).tekstExtractieTaakGewijzigd(captor.capture());
    assertThat(captor.getValue().status()).isEqualTo("tekst-extractie-bezig");
  }

  @Test
  void indienenUpdatetBezwaarBestandNaarTekstExtractieWachtend() {
    when(repository.save(any(TekstExtractieTaak.class)))
        .thenAnswer(i -> i.getArgument(0));
    var bestandEntiteit = new BezwaarBestandEntiteit();
    when(bezwaarBestandRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar.pdf"))
        .thenReturn(Optional.of(bestandEntiteit));
    when(bezwaarBestandRepository.save(any(BezwaarBestandEntiteit.class)))
        .thenAnswer(i -> i.getArgument(0));

    service.indienen("windmolens", "bezwaar.pdf");

    assertThat(bestandEntiteit.getStatus())
        .isEqualTo(BezwaarBestandStatus.TEKST_EXTRACTIE_WACHTEND);
    verify(bezwaarBestandRepository).save(bestandEntiteit);
  }

  @Test
  void pakOpVoorVerwerkingUpdatetBezwaarBestandNaarTekstExtractieBezig() {
    var taak = maakTaak(1L, "windmolens", "a.pdf", TekstExtractieTaakStatus.WACHTEND);
    when(repository.countByStatus(TekstExtractieTaakStatus.BEZIG)).thenReturn(0L);
    when(repository.findByStatusOrderByAangemaaktOpAsc(TekstExtractieTaakStatus.WACHTEND))
        .thenReturn(List.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    var bestandEntiteit = new BezwaarBestandEntiteit();
    when(bezwaarBestandRepository.findByProjectNaamAndBestandsnaam("windmolens", "a.pdf"))
        .thenReturn(Optional.of(bestandEntiteit));
    when(bezwaarBestandRepository.save(any(BezwaarBestandEntiteit.class)))
        .thenAnswer(i -> i.getArgument(0));

    service.pakOpVoorVerwerking();

    assertThat(bestandEntiteit.getStatus())
        .isEqualTo(BezwaarBestandStatus.TEKST_EXTRACTIE_BEZIG);
    verify(bezwaarBestandRepository).save(bestandEntiteit);
  }

  @Test
  void markeerKlaarUpdatetBezwaarBestandNaarTekstExtractieKlaar() {
    var taak = maakTaak(1L, "windmolens", "a.pdf", TekstExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    var bestandEntiteit = new BezwaarBestandEntiteit();
    when(bezwaarBestandRepository.findByProjectNaamAndBestandsnaam("windmolens", "a.pdf"))
        .thenReturn(Optional.of(bestandEntiteit));
    when(bezwaarBestandRepository.save(any(BezwaarBestandEntiteit.class)))
        .thenAnswer(i -> i.getArgument(0));

    service.markeerKlaar(1L, ExtractieMethode.DIGITAAL);

    assertThat(bestandEntiteit.getStatus())
        .isEqualTo(BezwaarBestandStatus.TEKST_EXTRACTIE_KLAAR);
    verify(bezwaarBestandRepository).save(bestandEntiteit);
  }

  @Test
  void markeerMisluktUpdatetBezwaarBestandNaarTekstExtractieMislukt() {
    var taak = maakTaak(1L, "windmolens", "a.pdf", TekstExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    var bestandEntiteit = new BezwaarBestandEntiteit();
    when(bezwaarBestandRepository.findByProjectNaamAndBestandsnaam("windmolens", "a.pdf"))
        .thenReturn(Optional.of(bestandEntiteit));
    when(bezwaarBestandRepository.save(any(BezwaarBestandEntiteit.class)))
        .thenAnswer(i -> i.getArgument(0));

    service.markeerMislukt(1L, "Bestand niet gevonden");

    assertThat(bestandEntiteit.getStatus())
        .isEqualTo(BezwaarBestandStatus.TEKST_EXTRACTIE_MISLUKT);
    verify(bezwaarBestandRepository).save(bestandEntiteit);
  }

  @Test
  void markeerOcrNietBeschikbaarUpdatetBezwaarBestandNaarOcrNietBeschikbaar() {
    var taak = maakTaak(1L, "windmolens", "a.pdf", TekstExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    var bestandEntiteit = new BezwaarBestandEntiteit();
    when(bezwaarBestandRepository.findByProjectNaamAndBestandsnaam("windmolens", "a.pdf"))
        .thenReturn(Optional.of(bestandEntiteit));
    when(bezwaarBestandRepository.save(any(BezwaarBestandEntiteit.class)))
        .thenAnswer(i -> i.getArgument(0));

    service.markeerOcrNietBeschikbaar(1L, "Tesseract niet gevonden");

    assertThat(bestandEntiteit.getStatus())
        .isEqualTo(BezwaarBestandStatus.TEKST_EXTRACTIE_OCR_NIET_BESCHIKBAAR);
    verify(bezwaarBestandRepository).save(bestandEntiteit);
  }

  @Test
  void verwerkTaak_pseudonimiseertPdfTekstVoorOpslag() throws IOException {
    var taak = maakTaak(1L, "windmolens", "bezwaar.pdf", TekstExtractieTaakStatus.BEZIG);
    var pad = Path.of("/tmp/bezwaar.pdf");
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar.pdf")).thenReturn(pad);
    when(pdfExtractor.extraheer(pad)).thenReturn(
        new TekstExtractieResultaat("Jan uit Gent", ExtractieMethode.DIGITAAL, "OK"));
    when(pseudonimiseringPoort.pseudonimiseer("Jan uit Gent")).thenReturn(
        new PseudonimiseringResultaat("{persoon_1} uit {adres_1}", "mapping-uuid-123"));
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(taak);

    // Gepseudonimiseerde tekst wordt opgeslagen, niet de originele
    verify(projectPoort).slaTekstOp("windmolens", "bezwaar.pdf", "{persoon_1} uit {adres_1}");
    assertThat(taak.getPseudonimiseringMappingId()).isEqualTo("mapping-uuid-123");
    assertThat(taak.getStatus()).isEqualTo(TekstExtractieTaakStatus.KLAAR);
  }

  @Test
  void verwerkTaak_pseudonimiseertTxtTekstVoorOpslag(@TempDir Path tempDir) throws IOException {
    var txtBestand = tempDir.resolve("bezwaar.txt");
    var tekst = "Maria uit Antwerpen " + "heeft een bezwaar ".repeat(10);
    Files.writeString(txtBestand, tekst);

    var taak = maakTaak(1L, "windmolens", "bezwaar.txt", TekstExtractieTaakStatus.BEZIG);
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar.txt")).thenReturn(txtBestand);
    when(kwaliteitsControle.controleer(tekst))
        .thenReturn(TekstKwaliteitsControle.Resultaat.valide());
    when(pseudonimiseringPoort.pseudonimiseer(tekst)).thenReturn(
        new PseudonimiseringResultaat("{persoon_1} uit {adres_1}", "mapping-uuid-456"));
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(taak);

    verify(projectPoort).slaTekstOp("windmolens", "bezwaar.txt", "{persoon_1} uit {adres_1}");
    assertThat(taak.getPseudonimiseringMappingId()).isEqualTo("mapping-uuid-456");
  }

  @Test
  void verwerkTaak_pseudonimiseringFoutWordtMislukt() throws IOException {
    var taak = maakTaak(1L, "windmolens", "bezwaar.pdf", TekstExtractieTaakStatus.BEZIG);
    var pad = Path.of("/tmp/bezwaar.pdf");
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar.pdf")).thenReturn(pad);
    when(pdfExtractor.extraheer(pad)).thenReturn(
        new TekstExtractieResultaat("tekst", ExtractieMethode.DIGITAAL, "OK"));
    when(pseudonimiseringPoort.pseudonimiseer("tekst"))
        .thenThrow(new PseudonimiseringException("Obscuro onbereikbaar"));
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(taak);

    assertThat(taak.getStatus()).isEqualTo(TekstExtractieTaakStatus.MISLUKT);
    assertThat(taak.getFoutmelding()).contains("Obscuro onbereikbaar");
    verify(projectPoort, never()).slaTekstOp(anyString(), anyString(), anyString());
  }

  private TekstExtractieTaak maakTaak(Long id, String projectNaam, String bestandsnaam,
      TekstExtractieTaakStatus status) {
    var taak = new TekstExtractieTaak();
    taak.setId(id);
    taak.setProjectNaam(projectNaam);
    taak.setBestandsnaam(bestandsnaam);
    taak.setStatus(status);
    taak.setAangemaaktOp(java.time.Instant.parse("2026-03-11T10:00:00Z"));
    return taak;
  }

  @Test
  void verwijderTaak_verwijdertBestaandeTaak() {
    var taak = new TekstExtractieTaak();
    taak.setProjectNaam("windmolens");
    taak.setBestandsnaam("bezwaar.pdf");
    when(repository.findById(1L)).thenReturn(Optional.of(taak));

    service.verwijderTaak("windmolens", 1L);

    verify(repository).delete(taak);
  }

  @Test
  void verwijderTaak_gooitExceptieAlsTaakNietBestaat() {
    when(repository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.verwijderTaak("windmolens", 99L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void verwijderTaak_gooitExceptieAlsProjectNietKlopt() {
    var taak = new TekstExtractieTaak();
    taak.setProjectNaam("anderProject");
    when(repository.findById(1L)).thenReturn(Optional.of(taak));

    assertThatThrownBy(() -> service.verwijderTaak("windmolens", 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
