package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.clustering.EmbeddingPoort;
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.Brondocument;
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.BezwaarGroepLidRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExtractieTaakServiceTest {

  @Mock
  private BezwaarDocumentRepository documentRepository;

  @Mock
  private ExtractieNotificatie notificatie;

  @Mock
  private IndividueelBezwaarRepository bezwaarRepository;

  @Mock
  private ProjectPoort projectPoort;

  @Mock
  private IngestiePoort ingestiePoort;

  @Mock
  private PassageValidator passageValidator;

  @Mock
  private EmbeddingPoort embeddingPoort;

  @Mock
  private BezwaarGroepLidRepository bezwaarGroepLidRepository;

  private ExtractieTaakService service;

  @BeforeEach
  void setUp() {
    service = new ExtractieTaakService(documentRepository, notificatie,
        bezwaarRepository, projectPoort, ingestiePoort,
        passageValidator, embeddingPoort, bezwaarGroepLidRepository);
    lenient().when(embeddingPoort.genereerEmbeddings(any())).thenAnswer(inv -> {
      List<String> teksten = inv.getArgument(0);
      return teksten.stream().map(t -> new float[]{0.1f}).toList();
    });
  }

  @Test
  void indienenZetBezwaarExtractieStatusOpBezig() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.GEEN);
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(doc));
    when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    var resultaat = service.indienen("windmolens", List.of("bezwaar-001.txt"));

    assertThat(resultaat).hasSize(1);
    assertThat(doc.getBezwaarExtractieStatus()).isEqualTo(BezwaarExtractieStatus.BEZIG);
    assertThat(doc.getFoutmelding()).isNull();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void indienenGooitExceptieAlsTekstExtractieNietKlaar() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.pdf",
        TekstExtractieStatus.BEZIG, BezwaarExtractieStatus.GEEN);
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.pdf"))
        .thenReturn(Optional.of(doc));

    assertThatThrownBy(() ->
        service.indienen("windmolens", List.of("bezwaar-001.pdf")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Tekst-extractie niet voltooid");
  }

  @Test
  void indienenRuimtBestaandeBezwarenOp() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.KLAAR);
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(doc));
    when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    var bezwaar1 = maakBezwaar(10L, 1L);
    var bezwaar2 = maakBezwaar(11L, 1L);
    when(bezwaarRepository.findByDocumentId(1L)).thenReturn(List.of(bezwaar1, bezwaar2));

    service.indienen("windmolens", List.of("bezwaar-001.txt"));

    verify(bezwaarGroepLidRepository).deleteByBezwaarIdIn(List.of(10L, 11L));
    verify(bezwaarRepository).deleteByDocumentId(1L);
  }

  @Test
  void geefTakenVoorProject() {
    var doc1 = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.BEZIG);
    var doc2 = maakDocument(2L, "windmolens", "bezwaar-002.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.GEEN);
    when(documentRepository.findByProjectNaam("windmolens")).thenReturn(List.of(doc1, doc2));

    var resultaat = service.geefTaken("windmolens");

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).bestandsnaam()).isEqualTo("bezwaar-001.txt");
  }

  @Test
  void geefTakenBevatAantalBezwarenVoorKlaarDocument() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.KLAAR);
    when(documentRepository.findByProjectNaam("windmolens")).thenReturn(List.of(doc));
    when(bezwaarRepository.countByDocumentId(1L)).thenReturn(5);

    var resultaat = service.geefTaken("windmolens");

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).aantalBezwaren()).isEqualTo(5);
  }

  @Test
  void geefTakenGeeftNullVoorAantalBezwarenBijBezigDocument() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.BEZIG);
    when(documentRepository.findByProjectNaam("windmolens")).thenReturn(List.of(doc));

    var resultaat = service.geefTaken("windmolens");

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).aantalBezwaren()).isNull();
  }

  @Test
  void pakOpVoorVerwerkingGeeftDocumentenMetStatusBezig() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.BEZIG);
    when(documentRepository.findByBezwaarExtractieStatus(BezwaarExtractieStatus.BEZIG))
        .thenReturn(List.of(doc));

    var resultaat = service.pakOpVoorVerwerking();

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).getId()).isEqualTo(1L);
  }

  @Test
  void pakOpVoorVerwerkingGeeftLeegAlsGeenBezigDocumenten() {
    when(documentRepository.findByBezwaarExtractieStatus(BezwaarExtractieStatus.BEZIG))
        .thenReturn(List.of());

    var resultaat = service.pakOpVoorVerwerking();

    assertThat(resultaat).isEmpty();
  }

  @Test
  void markeerKlaarZetResultaat() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.BEZIG);
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.markeerKlaar(1L, 500, 7);

    assertThat(doc.getBezwaarExtractieStatus()).isEqualTo(BezwaarExtractieStatus.KLAAR);
    assertThat(doc.getAantalWoorden()).isEqualTo(500);
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void markeerKlaarSlaatBezwarenOpMetDocumentIdEnPassageTekst() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.BEZIG);
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    var resultaat = new ExtractieResultaat(500, 2,
        List.of(new Passage(1, "Passage een"), new Passage(2, "Passage twee")),
        List.of(
            new GeextraheerdBezwaar(1, "Samenvatting een", "milieu"),
            new GeextraheerdBezwaar(2, "Samenvatting twee", "mobiliteit")),
        "Docsamenvatting");

    service.markeerKlaar(1L, resultaat);

    assertThat(doc.getBezwaarExtractieStatus()).isEqualTo(BezwaarExtractieStatus.KLAAR);
    assertThat(doc.getAantalWoorden()).isEqualTo(500);

    @SuppressWarnings("unchecked")
    var bezwaarListCaptor = ArgumentCaptor.forClass(List.class);
    // Alle bezwaren worden in één keer opgeslagen via saveAll (inclusief embeddings)
    verify(bezwaarRepository).saveAll(bezwaarListCaptor.capture());
    var opgeslagenBezwaren = (List<IndividueelBezwaar>) bezwaarListCaptor.getValue();
    assertThat(opgeslagenBezwaren).hasSize(2);
    assertThat(opgeslagenBezwaren.get(0).getSamenvatting()).isEqualTo("Samenvatting een");
    assertThat(opgeslagenBezwaren.get(0).getDocumentId()).isEqualTo(1L);
    assertThat(opgeslagenBezwaren.get(0).getPassageTekst()).isEqualTo("Passage een");
    assertThat(opgeslagenBezwaren.get(0).isManueel()).isFalse();
    assertThat(opgeslagenBezwaren.get(0).getEmbeddingPassage()).isNotNull();
  }

  @Test
  void markeerKlaarValideertPassagesEnZetPassageGevonden() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.BEZIG);
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    var pad = Path.of("/tmp/windmolens/bezwaren/bezwaar-001.txt");
    when(projectPoort.geefTekstBestandsPad("windmolens", "bezwaar-001.txt")).thenReturn(pad);
    when(ingestiePoort.leesBestand(pad)).thenReturn(
        new Brondocument("Volledige documenttekst.", "bezwaar-001.txt", pad.toString(),
            Instant.now()));
    when(passageValidator.valideer(any(), any()))
        .thenReturn(new PassageValidator.ValidatieResultaat(0));

    var resultaat = new ExtractieResultaat(100, 1,
        List.of(new Passage(1, "Volledige documenttekst.")),
        List.of(new GeextraheerdBezwaar(1, "Samenvatting", "milieu")),
        "Samenvatting doc");

    service.markeerKlaar(1L, resultaat);

    assertThat(doc.isHeeftPassagesDieNietInTekstVoorkomen()).isFalse();
    verify(passageValidator).valideer(any(), any());
  }

  @Test
  void markeerKlaarZetHeeftOpmerkingenBijNietGevondenPassages() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.BEZIG);
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    var pad = Path.of("/tmp/windmolens/bezwaren/bezwaar-001.txt");
    when(projectPoort.geefTekstBestandsPad("windmolens", "bezwaar-001.txt")).thenReturn(pad);
    when(ingestiePoort.leesBestand(pad)).thenReturn(
        new Brondocument("Documenttekst.", "bezwaar-001.txt", pad.toString(), Instant.now()));
    when(passageValidator.valideer(any(), any()))
        .thenReturn(new PassageValidator.ValidatieResultaat(2));

    var resultaat = new ExtractieResultaat(100, 2,
        List.of(new Passage(1, "Passage een")),
        List.of(
            new GeextraheerdBezwaar(1, "Samenvatting een", "milieu"),
            new GeextraheerdBezwaar(1, "Samenvatting twee", "mobiliteit")),
        "Doc samenvatting");

    service.markeerKlaar(1L, resultaat);

    assertThat(doc.isHeeftPassagesDieNietInTekstVoorkomen()).isTrue();
  }

  @Test
  void markeerKlaarGracefulBijOnleesbaarDocument() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.BEZIG);
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    when(projectPoort.geefTekstBestandsPad("windmolens", "bezwaar-001.txt"))
        .thenThrow(new RuntimeException("Bestand niet gevonden"));

    var resultaat = new ExtractieResultaat(100, 1,
        List.of(new Passage(1, "Passage")),
        List.of(new GeextraheerdBezwaar(1, "Samenvatting", "milieu")),
        "Samenvatting");

    service.markeerKlaar(1L, resultaat);

    assertThat(doc.getBezwaarExtractieStatus()).isEqualTo(BezwaarExtractieStatus.KLAAR);
    assertThat(doc.isHeeftPassagesDieNietInTekstVoorkomen()).isFalse();
    verify(passageValidator, org.mockito.Mockito.never()).valideer(any(), any());
  }

  @Test
  void markeerFoutZetStatusOpFout() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.BEZIG);
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.markeerFout(1L, "Definitieve fout");

    assertThat(doc.getBezwaarExtractieStatus()).isEqualTo(BezwaarExtractieStatus.FOUT);
    assertThat(doc.getFoutmelding()).isEqualTo("Definitieve fout");
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void geefExtractieDetailsMetBezwaren() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.KLAAR);
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(doc));

    var bezwaar = new IndividueelBezwaar();
    bezwaar.setDocumentId(1L);
    bezwaar.setSamenvatting("Geluidshinder");
    bezwaar.setPassageTekst("De geluidsoverlast zal onze nachtrust verstoren.");
    when(bezwaarRepository.findByDocumentId(1L)).thenReturn(List.of(bezwaar));

    var result = service.geefExtractieDetails("windmolens", "bezwaar-001.txt");

    assertThat(result).isNotNull();
    assertThat(result.bestandsnaam()).isEqualTo("bezwaar-001.txt");
    assertThat(result.aantalBezwaren()).isEqualTo(1);
    assertThat(result.bezwaren().get(0).samenvatting()).isEqualTo("Geluidshinder");
    assertThat(result.bezwaren().get(0).passage())
        .isEqualTo("De geluidsoverlast zal onze nachtrust verstoren.");
  }

  @Test
  void geefExtractieDetailsGeeftNullAlsExtractieNietKlaar() {
    var doc = maakDocument(1L, "windmolens", "bezig.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.BEZIG);
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezig.txt"))
        .thenReturn(Optional.of(doc));

    var result = service.geefExtractieDetails("windmolens", "bezig.txt");
    assertThat(result).isNull();
  }

  @Test
  void geefExtractieDetailsGeeftNullAlsDocumentNietBestaat() {
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "onbekend.txt"))
        .thenReturn(Optional.empty());

    var result = service.geefExtractieDetails("windmolens", "onbekend.txt");
    assertThat(result).isNull();
  }

  @Test
  void voegManueelBezwaarToeMetGeldigePassage() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.KLAAR);
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(doc));

    var pad = Path.of("/tmp/windmolens/bezwaren/bezwaar-001.txt");
    when(projectPoort.geefTekstBestandsPad("windmolens", "bezwaar-001.txt")).thenReturn(pad);
    when(ingestiePoort.leesBestand(pad)).thenReturn(
        new Brondocument("Dit is de volledige documenttekst met relevante inhoud.",
            "bezwaar-001.txt", pad.toString(), Instant.now()));

    when(passageValidator.normaliseer("Dit is de volledige documenttekst met relevante inhoud."))
        .thenReturn("dit is de volledige documenttekst met relevante inhoud.");
    when(passageValidator.normaliseer("volledige documenttekst"))
        .thenReturn("volledige documenttekst");

    when(bezwaarRepository.save(any())).thenAnswer(i -> {
      var b = i.getArgument(0, IndividueelBezwaar.class);
      b.setId(10L);
      return b;
    });
    when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    var detail = service.voegManueelBezwaarToe(
        "windmolens", "bezwaar-001.txt", "Samenvatting test", "volledige documenttekst");

    assertThat(detail).isNotNull();
    assertThat(detail.id()).isEqualTo(10L);
    assertThat(detail.samenvatting()).isEqualTo("Samenvatting test");
    assertThat(detail.manueel()).isTrue();
    assertThat(detail.passageGevonden()).isTrue();

    var bezwaarCaptor = ArgumentCaptor.forClass(IndividueelBezwaar.class);
    verify(bezwaarRepository).save(bezwaarCaptor.capture());
    var opgeslagen = bezwaarCaptor.getValue();
    assertThat(opgeslagen.isManueel()).isTrue();
    assertThat(opgeslagen.getDocumentId()).isEqualTo(1L);
    assertThat(opgeslagen.getPassageTekst()).isEqualTo("volledige documenttekst");
    assertThat(opgeslagen.getEmbeddingPassage()).isNotNull();

    assertThat(doc.isHeeftManueel()).isTrue();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void voegManueelBezwaarToeGooitExceptieBijOngeldigePassage() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.KLAAR);
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(doc));

    var pad = Path.of("/tmp/windmolens/bezwaren/bezwaar-001.txt");
    when(projectPoort.geefTekstBestandsPad("windmolens", "bezwaar-001.txt")).thenReturn(pad);
    when(ingestiePoort.leesBestand(pad)).thenReturn(
        new Brondocument("Dit is de volledige documenttekst.",
            "bezwaar-001.txt", pad.toString(), Instant.now()));

    when(passageValidator.normaliseer("Dit is de volledige documenttekst."))
        .thenReturn("dit is de volledige documenttekst.");
    when(passageValidator.normaliseer("Deze tekst staat er niet in"))
        .thenReturn("deze tekst staat er niet in");

    assertThatThrownBy(() ->
        service.voegManueelBezwaarToe(
            "windmolens", "bezwaar-001.txt", "Samenvatting", "Deze tekst staat er niet in"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Passage komt niet voor");
  }

  @Test
  void voegManueelBezwaarToeGooitExceptieBijLegeSamenvatting() {
    assertThatThrownBy(() ->
        service.voegManueelBezwaarToe("windmolens", "bezwaar-001.txt", "", "passage"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void voegManueelBezwaarToeGooitExceptieBijLegePassage() {
    assertThatThrownBy(() ->
        service.voegManueelBezwaarToe("windmolens", "bezwaar-001.txt", "samenvatting", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void voegManueelBezwaarToeGooitExceptieAlsExtractieNietKlaar() {
    var doc = maakDocument(1L, "windmolens", "bezig.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.BEZIG);
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezig.txt"))
        .thenReturn(Optional.of(doc));

    assertThatThrownBy(() ->
        service.voegManueelBezwaarToe("windmolens", "bezig.txt", "Samenvatting", "passage"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void verwijderManueelBezwaarSuccesvol() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.KLAAR);
    doc.setHeeftManueel(true);

    var bezwaar = new IndividueelBezwaar();
    bezwaar.setId(10L);
    bezwaar.setDocumentId(1L);
    bezwaar.setManueel(true);
    when(bezwaarRepository.findById(10L)).thenReturn(Optional.of(bezwaar));
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));

    // Nog 1 ander manueel bezwaar over na verwijdering
    var anderManueel = new IndividueelBezwaar();
    anderManueel.setManueel(true);
    when(bezwaarRepository.findByDocumentId(1L)).thenReturn(List.of(anderManueel));
    when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.verwijderBezwaar("windmolens", "bezwaar-001.txt", 10L);

    verify(bezwaarGroepLidRepository).deleteByBezwaarIdIn(List.of(10L));
    verify(bezwaarRepository).delete(bezwaar);
    assertThat(doc.isHeeftManueel()).isTrue();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void verwijderLaatstManueelBezwaarZetHeeftManueelUit() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.KLAAR);
    doc.setHeeftManueel(true);

    var bezwaar = new IndividueelBezwaar();
    bezwaar.setId(10L);
    bezwaar.setDocumentId(1L);
    bezwaar.setManueel(true);
    when(bezwaarRepository.findById(10L)).thenReturn(Optional.of(bezwaar));
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));

    when(bezwaarRepository.findByDocumentId(1L)).thenReturn(List.of());
    when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.verwijderBezwaar("windmolens", "bezwaar-001.txt", 10L);

    assertThat(doc.isHeeftManueel()).isFalse();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void verwijderAiBezwaarZetHeeftManueelOp() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.KLAAR);
    doc.setHeeftManueel(false);

    var bezwaar = new IndividueelBezwaar();
    bezwaar.setId(10L);
    bezwaar.setDocumentId(1L);
    bezwaar.setManueel(false);
    when(bezwaarRepository.findById(10L)).thenReturn(Optional.of(bezwaar));
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
    when(bezwaarRepository.findByDocumentId(1L)).thenReturn(List.of());
    when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.verwijderBezwaar("windmolens", "bezwaar-001.txt", 10L);

    verify(bezwaarRepository).delete(bezwaar);
    assertThat(doc.isHeeftManueel()).isTrue();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void verwijderBezwaarMetNietGevondenPassageZetFlagUit() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.KLAAR);
    doc.setHeeftPassagesDieNietInTekstVoorkomen(true);
    doc.setHeeftManueel(false);

    var bezwaar = new IndividueelBezwaar();
    bezwaar.setId(10L);
    bezwaar.setDocumentId(1L);
    bezwaar.setManueel(false);
    bezwaar.setPassageGevonden(false);
    when(bezwaarRepository.findById(10L)).thenReturn(Optional.of(bezwaar));
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));

    when(bezwaarRepository.findByDocumentId(1L)).thenReturn(List.of());
    when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.verwijderBezwaar("windmolens", "bezwaar-001.txt", 10L);

    assertThat(doc.isHeeftPassagesDieNietInTekstVoorkomen()).isFalse();
    assertThat(doc.isHeeftManueel()).isTrue();
  }

  @Test
  void verwijderBezwaarGooitExceptieBijVerkeerdeProject() {
    var bezwaar = new IndividueelBezwaar();
    bezwaar.setId(10L);
    bezwaar.setDocumentId(1L);
    bezwaar.setManueel(true);
    when(bezwaarRepository.findById(10L)).thenReturn(Optional.of(bezwaar));

    var doc = maakDocument(1L, "ander-project", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.KLAAR);
    when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));

    assertThatThrownBy(() ->
        service.verwijderBezwaar("windmolens", "bezwaar-001.txt", 10L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("behoort niet tot project");
  }

  @Test
  void verwerkOnafgerondeHerstartFoutDocumenten() {
    var foutDoc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.FOUT);
    foutDoc.setFoutmelding("Timeout");

    var klaarDoc = maakDocument(2L, "windmolens", "bezwaar-002.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.KLAAR);

    when(documentRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(foutDoc, klaarDoc));
    when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    int aantal = service.verwerkOnafgeronde("windmolens");

    assertThat(aantal).isEqualTo(1);
    assertThat(foutDoc.getBezwaarExtractieStatus()).isEqualTo(BezwaarExtractieStatus.BEZIG);
    assertThat(foutDoc.getFoutmelding()).isNull();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void verwerkOnafgerondeGeeftNulAlsAllesKlaar() {
    var klaarDoc = maakDocument(1L, "windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.KLAAR);
    when(documentRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(klaarDoc));

    int aantal = service.verwerkOnafgeronde("windmolens");

    assertThat(aantal).isZero();
    verify(notificatie, org.mockito.Mockito.never()).taakGewijzigd(any());
  }

  private BezwaarDocument maakDocument(Long id, String projectNaam, String bestandsnaam,
      TekstExtractieStatus tekstStatus, BezwaarExtractieStatus bezwaarStatus) {
    var doc = new BezwaarDocument();
    doc.setId(id);
    doc.setProjectNaam(projectNaam);
    doc.setBestandsnaam(bestandsnaam);
    doc.setTekstExtractieStatus(tekstStatus);
    doc.setBezwaarExtractieStatus(bezwaarStatus);
    return doc;
  }

  private IndividueelBezwaar maakBezwaar(Long id, Long documentId) {
    var bezwaar = new IndividueelBezwaar();
    bezwaar.setId(id);
    bezwaar.setDocumentId(documentId);
    bezwaar.setSamenvatting("Test bezwaar");
    return bezwaar;
  }
}
