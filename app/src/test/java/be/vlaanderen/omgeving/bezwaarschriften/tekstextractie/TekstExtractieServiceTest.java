package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.PassageGroepLidRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocument;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocumentRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarExtractieStatus;
import be.vlaanderen.omgeving.bezwaarschriften.project.IndividueelBezwaar;
import be.vlaanderen.omgeving.bezwaarschriften.project.IndividueelBezwaarRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.ProjectPoort;
import be.vlaanderen.omgeving.bezwaarschriften.project.TekstExtractieStatus;
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
  private BezwaarDocumentRepository documentRepository;

  @Mock
  private IndividueelBezwaarRepository bezwaarRepository;

  @Mock
  private PassageGroepLidRepository passageGroepLidRepository;

  @Mock
  private PdfTekstExtractor pdfExtractor;

  @Mock
  private TekstKwaliteitsControle kwaliteitsControle;

  @Mock
  private ProjectPoort projectPoort;

  @Mock
  private TekstExtractieNotificatie notificatie;

  @Mock
  private PseudonimiseringPoort pseudonimiseringPoort;

  @Mock
  private PseudonimiseringChunker chunker;

  @Mock
  private PseudonimiseringChunkRepository chunkRepository;

  private TekstExtractieService service;

  @BeforeEach
  void setUp() {
    service = new TekstExtractieService(
        documentRepository, bezwaarRepository, passageGroepLidRepository,
        pdfExtractor, kwaliteitsControle, projectPoort,
        pseudonimiseringPoort, notificatie,
        chunker, chunkRepository, 2);
  }

  @Test
  void indienen_maaktNieuwDocumentAanMetStatusBezig() {
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar.pdf"))
        .thenReturn(Optional.empty());
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> {
      BezwaarDocument d = i.getArgument(0);
      d.setId(1L);
      return d;
    });

    var resultaat = service.indienen("windmolens", "bezwaar.pdf");

    assertThat(resultaat.getId()).isEqualTo(1L);
    assertThat(resultaat.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.BEZIG);

    var captor = ArgumentCaptor.forClass(BezwaarDocument.class);
    verify(documentRepository).save(captor.capture());
    var opgeslagen = captor.getValue();
    assertThat(opgeslagen.getProjectNaam()).isEqualTo("windmolens");
    assertThat(opgeslagen.getBestandsnaam()).isEqualTo("bezwaar.pdf");
    assertThat(opgeslagen.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.BEZIG);
    assertThat(opgeslagen.getFoutmelding()).isNull();
  }

  @Test
  void indienen_hergebruiktBestaandDocument() {
    var bestaand = maakDocument(1L, "windmolens", "bezwaar.pdf", TekstExtractieStatus.KLAAR);
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar.pdf"))
        .thenReturn(Optional.of(bestaand));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    var resultaat = service.indienen("windmolens", "bezwaar.pdf");

    assertThat(resultaat.getId()).isEqualTo(1L);
    assertThat(resultaat.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.BEZIG);
  }

  @Test
  void indienen_herExtractieRuimtBezwarenOp() {
    var bestaand = maakDocument(1L, "windmolens", "bezwaar.pdf", TekstExtractieStatus.KLAAR);
    bestaand.setBezwaarExtractieStatus(BezwaarExtractieStatus.KLAAR);

    var bezwaar1 = new IndividueelBezwaar();
    bezwaar1.setId(10L);
    bezwaar1.setDocumentId(1L);
    var bezwaar2 = new IndividueelBezwaar();
    bezwaar2.setId(11L);
    bezwaar2.setDocumentId(1L);

    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar.pdf"))
        .thenReturn(Optional.of(bestaand));
    when(bezwaarRepository.findByDocumentId(1L)).thenReturn(List.of(bezwaar1, bezwaar2));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    var resultaat = service.indienen("windmolens", "bezwaar.pdf");

    verify(passageGroepLidRepository).deleteByBezwaarIdIn(List.of(10L, 11L));
    verify(bezwaarRepository).deleteByDocumentId(1L);
    assertThat(resultaat.getBezwaarExtractieStatus()).isEqualTo(BezwaarExtractieStatus.GEEN);
    assertThat(resultaat.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.BEZIG);
  }

  @Test
  void pakOpVoorVerwerking_geeftDocumentenMetStatusBezig() {
    var doc1 = maakDocument(1L, "windmolens", "a.pdf", TekstExtractieStatus.BEZIG);
    var doc2 = maakDocument(2L, "windmolens", "b.pdf", TekstExtractieStatus.BEZIG);

    when(documentRepository.findByTekstExtractieStatus(TekstExtractieStatus.BEZIG))
        .thenReturn(List.of(doc1, doc2));

    var resultaat = service.pakOpVoorVerwerking();

    assertThat(resultaat).hasSize(2);
  }

  @Test
  void pakOpVoorVerwerking_respecteertMaxConcurrentLimiet() {
    var doc1 = maakDocument(1L, "windmolens", "a.pdf", TekstExtractieStatus.BEZIG);
    var doc2 = maakDocument(2L, "windmolens", "b.pdf", TekstExtractieStatus.BEZIG);
    var doc3 = maakDocument(3L, "windmolens", "c.pdf", TekstExtractieStatus.BEZIG);

    when(documentRepository.findByTekstExtractieStatus(TekstExtractieStatus.BEZIG))
        .thenReturn(List.of(doc1, doc2, doc3));

    var resultaat = service.pakOpVoorVerwerking();

    // maxConcurrent = 2
    assertThat(resultaat).hasSize(2);
  }

  @Test
  void pakOpVoorVerwerking_geeftLegeLijstAlsGeenDocumenten() {
    when(documentRepository.findByTekstExtractieStatus(TekstExtractieStatus.BEZIG))
        .thenReturn(List.of());

    var resultaat = service.pakOpVoorVerwerking();

    assertThat(resultaat).isEmpty();
  }

  @Test
  void markeerKlaar_zetStatusKlaarEnExtractieMethode() {
    var doc = maakDocument(1L, "windmolens", "a.pdf", TekstExtractieStatus.BEZIG);
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    service.markeerKlaar(1L, ExtractieMethode.DIGITAAL);

    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.KLAAR);
    assertThat(doc.getExtractieMethode()).isEqualTo("DIGITAAL");
  }

  @Test
  void markeerFout_zetStatusFoutEnFoutmelding() {
    var doc = maakDocument(1L, "windmolens", "a.pdf", TekstExtractieStatus.BEZIG);
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    service.markeerFout(1L, "Bestand niet gevonden");

    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.FOUT);
    assertThat(doc.getFoutmelding()).isEqualTo("Bestand niet gevonden");
  }

  @Test
  void isTekstExtractieKlaar_geeftTrueWanneerStatusKlaar() {
    var doc = maakDocument(1L, "windmolens", "a.pdf", TekstExtractieStatus.KLAAR);
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "a.pdf"))
        .thenReturn(Optional.of(doc));

    assertThat(service.isTekstExtractieKlaar("windmolens", "a.pdf")).isTrue();
  }

  @Test
  void isTekstExtractieKlaar_geeftFalseWanneerStatusNietKlaar() {
    var doc = maakDocument(1L, "windmolens", "a.pdf", TekstExtractieStatus.BEZIG);
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "a.pdf"))
        .thenReturn(Optional.of(doc));

    assertThat(service.isTekstExtractieKlaar("windmolens", "a.pdf")).isFalse();
  }

  @Test
  void isTekstExtractieKlaar_geeftFalseWanneerGeenDocumentBestaat() {
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "a.pdf"))
        .thenReturn(Optional.empty());

    assertThat(service.isTekstExtractieKlaar("windmolens", "a.pdf")).isFalse();
  }

  @Test
  void verwerkTaak_verwerktPdfSuccesvol() throws IOException {
    var doc = maakDocument(1L, "windmolens", "bezwaar.pdf", TekstExtractieStatus.BEZIG);
    var pad = Path.of("/tmp/bezwaar.pdf");
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar.pdf")).thenReturn(pad);
    when(pdfExtractor.extraheer(pad)).thenReturn(
        new TekstExtractieResultaat("Geextraheerde tekst", ExtractieMethode.DIGITAAL, "OK"));
    when(chunker.chunk("Geextraheerde tekst")).thenReturn(List.of("Geextraheerde tekst"));
    when(pseudonimiseringPoort.pseudonimiseer("Geextraheerde tekst")).thenReturn(
        new PseudonimiseringResultaat("Geextraheerde tekst", "stub-mapping-id"));
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(doc);

    verify(projectPoort).slaTekstOp("windmolens", "bezwaar.pdf", "Geextraheerde tekst");
    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.KLAAR);
    assertThat(doc.getExtractieMethode()).isEqualTo("DIGITAAL");
  }

  @Test
  void verwerkTaak_verwerktTxtSuccesvol(@TempDir Path tempDir) throws IOException {
    var txtBestand = tempDir.resolve("bezwaar.txt");
    var tekst = "Dit is een test tekst met voldoende woorden ".repeat(10);
    Files.writeString(txtBestand, tekst);

    var doc = maakDocument(1L, "windmolens", "bezwaar.txt", TekstExtractieStatus.BEZIG);
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar.txt")).thenReturn(txtBestand);
    when(kwaliteitsControle.controleer(tekst))
        .thenReturn(TekstKwaliteitsControle.Resultaat.valide());
    when(chunker.chunk(tekst)).thenReturn(List.of(tekst));
    when(pseudonimiseringPoort.pseudonimiseer(tekst)).thenReturn(
        new PseudonimiseringResultaat(tekst, "stub-mapping-id"));
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(doc);

    verify(projectPoort).slaTekstOp("windmolens", "bezwaar.txt", tekst);
    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.KLAAR);
  }

  @Test
  void verwerkTaak_txtMetOnvoldoendeKwaliteitWordtFout(@TempDir Path tempDir)
      throws IOException {
    var txtBestand = tempDir.resolve("slecht.txt");
    Files.writeString(txtBestand, "korte tekst");

    var doc = maakDocument(1L, "windmolens", "slecht.txt", TekstExtractieStatus.BEZIG);
    when(projectPoort.geefBestandsPad("windmolens", "slecht.txt")).thenReturn(txtBestand);
    when(kwaliteitsControle.controleer("korte tekst"))
        .thenReturn(TekstKwaliteitsControle.Resultaat.ongeldig("Te weinig woorden"));
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(doc);

    verify(projectPoort, never()).slaTekstOp(anyString(), anyString(), anyString());
    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.FOUT);
    assertThat(doc.getFoutmelding()).contains("Te weinig woorden");
  }

  @Test
  void verwerkTaak_ocrNietBeschikbaarWordtFout() throws IOException {
    var doc = maakDocument(1L, "windmolens", "scan.pdf", TekstExtractieStatus.BEZIG);
    var pad = Path.of("/tmp/scan.pdf");
    when(projectPoort.geefBestandsPad("windmolens", "scan.pdf")).thenReturn(pad);
    when(pdfExtractor.extraheer(pad))
        .thenThrow(new OcrNietBeschikbaarException("Tesseract niet gevonden"));
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(doc);

    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.FOUT);
    assertThat(doc.getFoutmelding()).contains("Tesseract niet gevonden");
  }

  @Test
  void verwerkTaak_onverwachteFoutWordtFout() throws IOException {
    var doc = maakDocument(1L, "windmolens", "fout.pdf", TekstExtractieStatus.BEZIG);
    var pad = Path.of("/tmp/fout.pdf");
    when(projectPoort.geefBestandsPad("windmolens", "fout.pdf")).thenReturn(pad);
    when(pdfExtractor.extraheer(pad)).thenThrow(new IOException("Bestand corrupt"));
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(doc);

    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.FOUT);
    assertThat(doc.getFoutmelding()).contains("Bestand corrupt");
  }

  @Test
  void verwerkTaak_nietOndersteundBestandstype() {
    var doc = maakDocument(1L, "windmolens", "foto.jpg", TekstExtractieStatus.BEZIG);
    var pad = Path.of("/tmp/foto.jpg");
    when(projectPoort.geefBestandsPad("windmolens", "foto.jpg")).thenReturn(pad);
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(doc);

    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.FOUT);
    assertThat(doc.getFoutmelding()).contains("Niet-ondersteund bestandstype");
  }

  @Test
  void geefGeextraheerdetekstLeestVanBestandssysteem(@TempDir Path tempDir) throws IOException {
    var doc = maakDocument(1L, "windmolens", "bezwaar.pdf", TekstExtractieStatus.KLAAR);
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar.pdf"))
        .thenReturn(Optional.of(doc));
    var pad = tempDir.resolve("test-tekst.txt");
    Files.writeString(pad, "Testinhoud");
    when(projectPoort.geefTekstBestandsPad("windmolens", "bezwaar.pdf"))
        .thenReturn(pad);

    var resultaat = service.geefGeextraheerdetekst("windmolens", "bezwaar.pdf");

    assertThat(resultaat).isEqualTo("Testinhoud");
  }

  @Test
  void geefGeextraheerdetekstRetourneertNullAlsDocumentNietKlaar() {
    var doc = maakDocument(1L, "windmolens", "bezwaar.pdf", TekstExtractieStatus.BEZIG);
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar.pdf"))
        .thenReturn(Optional.of(doc));

    var resultaat = service.geefGeextraheerdetekst("windmolens", "bezwaar.pdf");

    assertThat(resultaat).isNull();
  }

  @Test
  void geefGeextraheerdetekstRetourneertNullAlsGeenDocument() {
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar.pdf"))
        .thenReturn(Optional.empty());

    var resultaat = service.geefGeextraheerdetekst("windmolens", "bezwaar.pdf");

    assertThat(resultaat).isNull();
  }

  @Test
  void indienen_stuurtWebSocketNotificatie() {
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar.pdf"))
        .thenReturn(Optional.empty());
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> {
      BezwaarDocument d = i.getArgument(0);
      d.setId(1L);
      return d;
    });

    service.indienen("windmolens", "bezwaar.pdf");

    var captor = ArgumentCaptor.forClass(TekstExtractieTaakDto.class);
    verify(notificatie).tekstExtractieTaakGewijzigd(captor.capture());
    var dto = captor.getValue();
    assertThat(dto.tekstExtractieStatus()).isEqualTo("BEZIG");
    assertThat(dto.projectNaam()).isEqualTo("windmolens");
    assertThat(dto.bestandsnaam()).isEqualTo("bezwaar.pdf");
  }

  @Test
  void markeerKlaar_stuurtWebSocketNotificatie() {
    var doc = maakDocument(1L, "windmolens", "a.pdf", TekstExtractieStatus.BEZIG);
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    service.markeerKlaar(1L, ExtractieMethode.DIGITAAL);

    var captor = ArgumentCaptor.forClass(TekstExtractieTaakDto.class);
    verify(notificatie).tekstExtractieTaakGewijzigd(captor.capture());
    assertThat(captor.getValue().tekstExtractieStatus()).isEqualTo("KLAAR");
  }

  @Test
  void verwerkTaak_pseudonimiseertPdfTekstVoorOpslag() throws IOException {
    var doc = maakDocument(1L, "windmolens", "bezwaar.pdf", TekstExtractieStatus.BEZIG);
    var pad = Path.of("/tmp/bezwaar.pdf");
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar.pdf")).thenReturn(pad);
    when(pdfExtractor.extraheer(pad)).thenReturn(
        new TekstExtractieResultaat("Jan uit Gent", ExtractieMethode.DIGITAAL, "OK"));
    when(chunker.chunk("Jan uit Gent")).thenReturn(List.of("Jan uit Gent"));
    when(pseudonimiseringPoort.pseudonimiseer("Jan uit Gent")).thenReturn(
        new PseudonimiseringResultaat("{persoon_1} uit {adres_1}", "mapping-uuid-123"));
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(doc);

    // Gepseudonimiseerde tekst wordt opgeslagen, niet de originele
    verify(projectPoort).slaTekstOp("windmolens", "bezwaar.pdf", "{persoon_1} uit {adres_1}");
    verify(chunkRepository).save(any(PseudonimiseringChunk.class));
    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.KLAAR);
  }

  @Test
  void verwerkTaak_pseudonimiseertTxtTekstVoorOpslag(@TempDir Path tempDir) throws IOException {
    var txtBestand = tempDir.resolve("bezwaar.txt");
    var tekst = "Maria uit Antwerpen " + "heeft een bezwaar ".repeat(10);
    Files.writeString(txtBestand, tekst);

    var doc = maakDocument(1L, "windmolens", "bezwaar.txt", TekstExtractieStatus.BEZIG);
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar.txt")).thenReturn(txtBestand);
    when(kwaliteitsControle.controleer(tekst))
        .thenReturn(TekstKwaliteitsControle.Resultaat.valide());
    when(chunker.chunk(tekst)).thenReturn(List.of(tekst));
    when(pseudonimiseringPoort.pseudonimiseer(tekst)).thenReturn(
        new PseudonimiseringResultaat("{persoon_1} uit {adres_1}", "mapping-uuid-456"));
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(doc);

    verify(projectPoort).slaTekstOp("windmolens", "bezwaar.txt", "{persoon_1} uit {adres_1}");
    verify(chunkRepository).save(any(PseudonimiseringChunk.class));
  }

  @Test
  void verwerkTaak_pseudonimiseringFoutWordtFout() throws IOException {
    var doc = maakDocument(1L, "windmolens", "bezwaar.pdf", TekstExtractieStatus.BEZIG);
    var pad = Path.of("/tmp/bezwaar.pdf");
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar.pdf")).thenReturn(pad);
    when(pdfExtractor.extraheer(pad)).thenReturn(
        new TekstExtractieResultaat("tekst", ExtractieMethode.DIGITAAL, "OK"));
    when(chunker.chunk("tekst")).thenReturn(List.of("tekst"));
    when(pseudonimiseringPoort.pseudonimiseer("tekst"))
        .thenThrow(new PseudonimiseringException("Obscuro onbereikbaar"));
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(doc);

    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.FOUT);
    assertThat(doc.getFoutmelding()).contains("Obscuro onbereikbaar");
    verify(projectPoort, never()).slaTekstOp(anyString(), anyString(), anyString());
  }

  @Test
  void verwerkTaak_multiChunkSlaatMeerdereChunksOp() throws IOException {
    var doc = maakDocument(1L, "windmolens", "groot.pdf", TekstExtractieStatus.BEZIG);
    var pad = Path.of("/tmp/groot.pdf");
    when(projectPoort.geefBestandsPad("windmolens", "groot.pdf")).thenReturn(pad);
    when(pdfExtractor.extraheer(pad)).thenReturn(
        new TekstExtractieResultaat("deel1\n\ndeel2\n\ndeel3", ExtractieMethode.DIGITAAL, "OK"));
    when(chunker.chunk("deel1\n\ndeel2\n\ndeel3"))
        .thenReturn(List.of("deel1", "deel2", "deel3"));
    when(pseudonimiseringPoort.pseudonimiseer("deel1")).thenReturn(
        new PseudonimiseringResultaat("{p1}", "map-0"));
    when(pseudonimiseringPoort.pseudonimiseer("deel2")).thenReturn(
        new PseudonimiseringResultaat("{p2}", "map-1"));
    when(pseudonimiseringPoort.pseudonimiseer("deel3")).thenReturn(
        new PseudonimiseringResultaat("{p3}", "map-2"));
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(doc);

    // Chunks worden samengevoegd met dubbele newline
    verify(projectPoort).slaTekstOp("windmolens", "groot.pdf", "{p1}\n\n{p2}\n\n{p3}");
    // Eerdere chunks worden opgeruimd
    verify(chunkRepository).deleteByDocumentId(1L);
    // 3 chunks opgeslagen
    verify(chunkRepository, times(3)).save(any(PseudonimiseringChunk.class));
    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.KLAAR);
  }

  @Test
  void verwerkTaak_partieleChunkFoutRolltTerug() throws IOException {
    var doc = maakDocument(1L, "windmolens", "groot.pdf", TekstExtractieStatus.BEZIG);
    var pad = Path.of("/tmp/groot.pdf");
    when(projectPoort.geefBestandsPad("windmolens", "groot.pdf")).thenReturn(pad);
    when(pdfExtractor.extraheer(pad)).thenReturn(
        new TekstExtractieResultaat("deel1\n\ndeel2", ExtractieMethode.DIGITAAL, "OK"));
    when(chunker.chunk("deel1\n\ndeel2")).thenReturn(List.of("deel1", "deel2"));
    when(pseudonimiseringPoort.pseudonimiseer("deel1")).thenReturn(
        new PseudonimiseringResultaat("{p1}", "map-0"));
    when(pseudonimiseringPoort.pseudonimiseer("deel2"))
        .thenThrow(new PseudonimiseringException("Timeout bij chunk 2"));
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(doc);

    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.FOUT);
    assertThat(doc.getFoutmelding()).contains("Timeout bij chunk 2");
    verify(projectPoort, never()).slaTekstOp(anyString(), anyString(), anyString());
  }

  @Test
  void verwijderTaak_resetDocumentStatus() {
    var doc = maakDocument(1L, "windmolens", "bezwaar.pdf", TekstExtractieStatus.KLAAR);
    doc.setExtractieMethode("DIGITAAL");
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    service.verwijderTaak("windmolens", 1L);

    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.GEEN);
    assertThat(doc.getExtractieMethode()).isNull();
    assertThat(doc.getFoutmelding()).isNull();
    verify(documentRepository).save(doc);
  }

  @Test
  void verwijderTaak_gooitExceptieAlsDocumentNietBestaat() {
    when(documentRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.verwijderTaak("windmolens", 99L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void verwijderTaak_gooitExceptieAlsProjectNietKlopt() {
    var doc = maakDocument(1L, "anderProject", "bezwaar.pdf", TekstExtractieStatus.KLAAR);
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));

    assertThatThrownBy(() -> service.verwijderTaak("windmolens", 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void herstartTekstExtractie_vanFoutNaarBezig() {
    var doc = maakDocument(1L, "windmolens", "bezwaar.pdf", TekstExtractieStatus.FOUT);
    doc.setFoutmelding("Eerdere fout");
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar.pdf"))
        .thenReturn(Optional.of(doc));
    when(documentRepository.save(any(BezwaarDocument.class))).thenAnswer(i -> i.getArgument(0));

    var dto = service.herstartTekstExtractie("windmolens", "bezwaar.pdf");

    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.BEZIG);
    assertThat(doc.getFoutmelding()).isNull();
    assertThat(dto.tekstExtractieStatus()).isEqualTo("BEZIG");
  }

  @Test
  void herstartTekstExtractie_gooitExceptieAlsNietInFoutStatus() {
    var doc = maakDocument(1L, "windmolens", "bezwaar.pdf", TekstExtractieStatus.KLAAR);
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar.pdf"))
        .thenReturn(Optional.of(doc));

    assertThatThrownBy(() -> service.herstartTekstExtractie("windmolens", "bezwaar.pdf"))
        .isInstanceOf(IllegalStateException.class);
  }

  private BezwaarDocument maakDocument(Long id, String projectNaam, String bestandsnaam,
      TekstExtractieStatus status) {
    var doc = new BezwaarDocument();
    doc.setId(id);
    doc.setProjectNaam(projectNaam);
    doc.setBestandsnaam(bestandsnaam);
    doc.setTekstExtractieStatus(status);
    return doc;
  }
}
