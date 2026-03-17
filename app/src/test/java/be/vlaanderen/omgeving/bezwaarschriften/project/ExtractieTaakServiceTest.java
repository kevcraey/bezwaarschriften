package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.clustering.EmbeddingPoort;
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.Brondocument;
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.TekstExtractieService;
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
  private ExtractieTaakRepository repository;

  @Mock
  private ExtractieNotificatie notificatie;

  @Mock
  private ProjectService projectService;

  @Mock
  private ExtractiePassageRepository passageRepository;

  @Mock
  private GeextraheerdBezwaarRepository bezwaarRepository;

  @Mock
  private ProjectPoort projectPoort;

  @Mock
  private IngestiePoort ingestiePoort;

  @Mock
  private PassageValidator passageValidator;

  @Mock
  private EmbeddingPoort embeddingPoort;

  @Mock
  private TekstExtractieService tekstExtractieService;

  @Mock
  private BezwaarBestandRepository bezwaarBestandRepository;

  private ExtractieTaakService service;

  @BeforeEach
  void setUp() {
    service = new ExtractieTaakService(repository, notificatie, projectService,
        passageRepository, bezwaarRepository, projectPoort, ingestiePoort,
        passageValidator, embeddingPoort, tekstExtractieService,
        bezwaarBestandRepository, 3, 3);
    lenient().when(embeddingPoort.genereerEmbeddings(any())).thenAnswer(inv -> {
      List<String> teksten = inv.getArgument(0);
      return teksten.stream().map(t -> new float[]{0.1f}).toList();
    });
  }

  @Test
  void dienTakenInMetStatusWachtend() {
    when(repository.save(any())).thenAnswer(i -> {
      var t = i.getArgument(0, ExtractieTaak.class);
      t.setId(1L);
      return t;
    });

    var resultaat = service.indienen("windmolens", List.of("bezwaar-001.txt", "bezwaar-002.txt"));

    assertThat(resultaat).hasSize(2);
    var captor = ArgumentCaptor.forClass(ExtractieTaak.class);
    verify(repository, times(2)).save(captor.capture());
    captor.getAllValues().forEach(taak -> {
      assertThat(taak.getStatus()).isEqualTo(ExtractieTaakStatus.WACHTEND);
      assertThat(taak.getProjectNaam()).isEqualTo("windmolens");
      assertThat(taak.getAantalPogingen()).isZero();
    });
    verify(notificatie, times(2)).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void indienenGooitExceptieAlsTekstExtractieNietKlaar() {
    when(tekstExtractieService.isTekstExtractieKlaar("windmolens", "bezwaar-001.pdf"))
        .thenReturn(false);

    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
        service.indienen("windmolens", List.of("bezwaar-001.pdf")))
        .isInstanceOf(TekstExtractieNietVoltooidException.class)
        .hasMessageContaining("Kan geen bezwaren extraheren zonder eerst tekst te extraheren");
  }

  @Test
  void indienenVoorTxtBestandSlaatTekstExtractieCheckOver() {
    when(repository.save(any(ExtractieTaak.class))).thenAnswer(inv -> {
      var taak = inv.getArgument(0, ExtractieTaak.class);
      taak.setId(1L);
      return taak;
    });

    var resultaat = service.indienen("windmolens", List.of("bezwaar-001.txt"));

    assertThat(resultaat).hasSize(1);
  }

  @Test
  void geefTakenVoorProject() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.WACHTEND);
    when(repository.findByProjectNaam("windmolens")).thenReturn(List.of(taak));

    var resultaat = service.geefTaken("windmolens");

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).projectNaam()).isEqualTo("windmolens");
    assertThat(resultaat.get(0).bestandsnaam()).isEqualTo("bezwaar-001.txt");
    verify(repository).findByProjectNaam("windmolens");
  }

  @Test
  void pakOpVoorVerwerkingZetStatusOpBezig() {
    when(repository.countByStatus(ExtractieTaakStatus.BEZIG)).thenReturn(0L);
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.WACHTEND);
    when(repository.findByStatusOrderByAangemaaktOpAsc(ExtractieTaakStatus.WACHTEND))
        .thenReturn(List.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    var resultaat = service.pakOpVoorVerwerking();

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).getStatus()).isEqualTo(ExtractieTaakStatus.BEZIG);
    assertThat(resultaat.get(0).getVerwerkingGestartOp()).isNotNull();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void pakOpRespecteerMaxConcurrent() {
    when(repository.countByStatus(ExtractieTaakStatus.BEZIG)).thenReturn(3L);

    var resultaat = service.pakOpVoorVerwerking();

    assertThat(resultaat).isEmpty();
  }

  @Test
  void markeerKlaarZetResultaat() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.markeerKlaar(1L, 500, 7);

    assertThat(taak.getStatus()).isEqualTo(ExtractieTaakStatus.KLAAR);
    assertThat(taak.getAantalWoorden()).isEqualTo(500);
    assertThat(taak.getAantalBezwaren()).isEqualTo(7);
    assertThat(taak.getAfgerondOp()).isNotNull();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void markeerKlaarSlaatPassagesEnBezwarenOp() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    var resultaat = new ExtractieResultaat(500, 2,
        List.of(new Passage(1, "Passage een"), new Passage(2, "Passage twee")),
        List.of(
            new GeextraheerdBezwaar(1, "Samenvatting een", "milieu"),
            new GeextraheerdBezwaar(2, "Samenvatting twee", "mobiliteit")),
        "Docsamenvatting");

    service.markeerKlaar(1L, resultaat);

    assertThat(taak.getStatus()).isEqualTo(ExtractieTaakStatus.KLAAR);
    assertThat(taak.getAantalWoorden()).isEqualTo(500);
    assertThat(taak.getAantalBezwaren()).isEqualTo(2);

    var passageCaptor = ArgumentCaptor.forClass(ExtractiePassageEntiteit.class);
    verify(passageRepository, times(2)).save(passageCaptor.capture());
    assertThat(passageCaptor.getAllValues().get(0).getTekst()).isEqualTo("Passage een");
    assertThat(passageCaptor.getAllValues().get(0).getTaakId()).isEqualTo(1L);

    var bezwaarCaptor = ArgumentCaptor.forClass(GeextraheerdBezwaarEntiteit.class);
    // Elke bezwaar wordt 2x opgeslagen: eerst zonder embedding, dan met embedding
    verify(bezwaarRepository, times(4)).save(bezwaarCaptor.capture());
    assertThat(bezwaarCaptor.getAllValues().get(0).getSamenvatting()).isEqualTo("Samenvatting een");
    assertThat(bezwaarCaptor.getAllValues().get(0).getTaakId()).isEqualTo(1L);
    assertThat(bezwaarCaptor.getAllValues().get(2).getEmbeddingPassage()).isNotNull();
  }

  @Test
  void markeerFoutMetRetryZetTerugNaarWachtend() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    taak.setVerwerkingGestartOp(Instant.now());
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.markeerFout(1L, "Timeout bij AI-call");

    assertThat(taak.getStatus()).isEqualTo(ExtractieTaakStatus.WACHTEND);
    assertThat(taak.getAantalPogingen()).isEqualTo(1);
    assertThat(taak.getVerwerkingGestartOp()).isNull();
    assertThat(taak.getFoutmelding()).isNull();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void markeerFoutDefinitiefBijMaxPogingen() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    taak.setAantalPogingen(2);
    taak.setMaxPogingen(3);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.markeerFout(1L, "Definitieve fout");

    assertThat(taak.getStatus()).isEqualTo(ExtractieTaakStatus.FOUT);
    assertThat(taak.getAantalPogingen()).isEqualTo(3);
    assertThat(taak.getFoutmelding()).isEqualTo("Definitieve fout");
    assertThat(taak.getAfgerondOp()).isNotNull();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void verwerkOnafgerondeHerstartGefaaldeTaken() {
    var taak1 = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.FOUT);
    taak1.setAantalPogingen(3);
    taak1.setMaxPogingen(3);
    taak1.setFoutmelding("Timeout");
    taak1.setAfgerondOp(Instant.now());

    when(repository.findByProjectNaamAndStatus("windmolens", ExtractieTaakStatus.FOUT))
        .thenReturn(List.of(taak1));
    when(projectService.geefBezwaren("windmolens"))
        .thenReturn(List.of(new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.BEZWAAR_EXTRACTIE_FOUT)));

    int aantal = service.verwerkOnafgeronde("windmolens");

    assertThat(aantal).isEqualTo(1);
    assertThat(taak1.getStatus()).isEqualTo(ExtractieTaakStatus.WACHTEND);
    assertThat(taak1.getMaxPogingen()).isEqualTo(4);
    assertThat(taak1.getFoutmelding()).isNull();
    assertThat(taak1.getAfgerondOp()).isNull();
    assertThat(taak1.getVerwerkingGestartOp()).isNull();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void verwerkOnafgerondeNeemtAlleenTekstExtractieKlareDocumenten() {
    var klaarBestand = new BezwaarBestand("bezwaar-klaar.pdf", BezwaarBestandStatus.TEKST_EXTRACTIE_KLAAR);
    var todoBestand = new BezwaarBestand("bezwaar-todo.pdf", BezwaarBestandStatus.TODO);
    when(projectService.geefBezwaren("windmolens")).thenReturn(List.of(klaarBestand, todoBestand));
    when(repository.findByProjectNaamAndStatus("windmolens", ExtractieTaakStatus.FOUT))
        .thenReturn(List.of());
    when(repository.save(any(ExtractieTaak.class))).thenAnswer(inv -> {
      var taak = inv.getArgument(0, ExtractieTaak.class);
      taak.setId(1L);
      return taak;
    });

    int resultaat = service.verwerkOnafgeronde("windmolens");

    assertThat(resultaat).isEqualTo(1); // alleen klaarBestand
    verify(repository, times(1)).save(any(ExtractieTaak.class));
  }

  @Test
  void verwerkOnafgerondeCreertTakenVoorDocumentenMetKlareTekstExtractie() {
    when(repository.findByProjectNaamAndStatus("windmolens", ExtractieTaakStatus.FOUT))
        .thenReturn(List.of());
    when(projectService.geefBezwaren("windmolens"))
        .thenReturn(List.of(
            new BezwaarBestand("nieuw-001.txt", BezwaarBestandStatus.TEKST_EXTRACTIE_KLAAR),
            new BezwaarBestand("klaar-001.txt", BezwaarBestandStatus.BEZWAAR_EXTRACTIE_KLAAR),
            new BezwaarBestand("foto.jpg", BezwaarBestandStatus.NIET_ONDERSTEUND)
        ));
    when(repository.save(any())).thenAnswer(i -> {
      var t = i.getArgument(0, ExtractieTaak.class);
      t.setId(10L);
      return t;
    });

    int aantal = service.verwerkOnafgeronde("windmolens");

    assertThat(aantal).isEqualTo(1);
    var captor = ArgumentCaptor.forClass(ExtractieTaak.class);
    verify(repository).save(captor.capture());
    var nieuweTaak = captor.getValue();
    assertThat(nieuweTaak.getProjectNaam()).isEqualTo("windmolens");
    assertThat(nieuweTaak.getBestandsnaam()).isEqualTo("nieuw-001.txt");
    assertThat(nieuweTaak.getStatus()).isEqualTo(ExtractieTaakStatus.WACHTEND);
    assertThat(nieuweTaak.getAantalPogingen()).isZero();
  }

  @Test
  void verwerkOnafgerondeSluitTodoDocumentenUit() {
    when(repository.findByProjectNaamAndStatus("windmolens", ExtractieTaakStatus.FOUT))
        .thenReturn(List.of());
    when(projectService.geefBezwaren("windmolens"))
        .thenReturn(List.of(
            new BezwaarBestand("nieuw-001.txt", BezwaarBestandStatus.TODO)
        ));

    int aantal = service.verwerkOnafgeronde("windmolens");

    assertThat(aantal).isZero();
  }

  @Test
  void verwerkOnafgerondeCombinatieVanFoutEnTodo() {
    var foutTaak = maakTaak(1L, "windmolens", "fout-001.txt", ExtractieTaakStatus.FOUT);
    foutTaak.setAantalPogingen(3);
    foutTaak.setMaxPogingen(3);
    foutTaak.setFoutmelding("Error");
    foutTaak.setAfgerondOp(Instant.now());

    when(repository.findByProjectNaamAndStatus("windmolens", ExtractieTaakStatus.FOUT))
        .thenReturn(List.of(foutTaak));
    when(projectService.geefBezwaren("windmolens"))
        .thenReturn(List.of(
            new BezwaarBestand("fout-001.txt", BezwaarBestandStatus.BEZWAAR_EXTRACTIE_FOUT),
            new BezwaarBestand("nieuw-001.txt", BezwaarBestandStatus.TEKST_EXTRACTIE_KLAAR)
        ));
    when(repository.save(any())).thenAnswer(i -> {
      var t = i.getArgument(0, ExtractieTaak.class);
      if (t.getId() == null) {
        t.setId(10L);
      }
      return t;
    });

    int aantal = service.verwerkOnafgeronde("windmolens");

    assertThat(aantal).isEqualTo(2);
  }

  @Test
  void verwerkOnafgerondeGeeftNulAlsAllesKlaar() {
    when(repository.findByProjectNaamAndStatus("windmolens", ExtractieTaakStatus.FOUT))
        .thenReturn(List.of());
    when(projectService.geefBezwaren("windmolens"))
        .thenReturn(List.of(
            new BezwaarBestand("klaar-001.txt", BezwaarBestandStatus.BEZWAAR_EXTRACTIE_KLAAR)
        ));

    int aantal = service.verwerkOnafgeronde("windmolens");

    assertThat(aantal).isZero();
    verify(notificatie, org.mockito.Mockito.never()).taakGewijzigd(any());
  }

  @Test
  void verwijderTaakVerwijdertUitRepository() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.WACHTEND);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));

    service.verwijderTaak("windmolens", 1L);

    verify(repository).delete(taak);
  }

  @Test
  void verwijderTaakGooitExceptieBijOnbekendeTaak() {
    when(repository.findById(999L)).thenReturn(Optional.empty());

    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
        service.verwijderTaak("windmolens", 999L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void verwijderTaakGooitExceptieBijVerkeerdeProject() {
    var taak = maakTaak(1L, "snelweg", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));

    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
        service.verwijderTaak("windmolens", 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void geefExtractieDetailsJointPassagesMetBezwaren() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.KLAAR);
    when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
        "windmolens", "bezwaar-001.txt")).thenReturn(Optional.of(taak));

    var passage = new ExtractiePassageEntiteit();
    passage.setTaakId(1L);
    passage.setPassageNr(1);
    passage.setTekst("De geluidsoverlast zal onze nachtrust verstoren.");
    when(passageRepository.findByTaakId(1L)).thenReturn(List.of(passage));

    var bezwaar = new GeextraheerdBezwaarEntiteit();
    bezwaar.setTaakId(1L);
    bezwaar.setPassageNr(1);
    bezwaar.setSamenvatting("Geluidshinder");
    when(bezwaarRepository.findByTaakId(1L)).thenReturn(List.of(bezwaar));

    var result = service.geefExtractieDetails("windmolens", "bezwaar-001.txt");

    assertThat(result).isNotNull();
    assertThat(result.bestandsnaam()).isEqualTo("bezwaar-001.txt");
    assertThat(result.aantalBezwaren()).isEqualTo(1);
    assertThat(result.bezwaren().get(0).samenvatting()).isEqualTo("Geluidshinder");
    assertThat(result.bezwaren().get(0).passage())
        .isEqualTo("De geluidsoverlast zal onze nachtrust verstoren.");
  }

  @Test
  void geefExtractieDetailsGeeftNullAlsTaakNietKlaar() {
    var taak = maakTaak(1L, "windmolens", "bezig.txt", ExtractieTaakStatus.BEZIG);
    when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
        "windmolens", "bezig.txt")).thenReturn(Optional.of(taak));

    var result = service.geefExtractieDetails("windmolens", "bezig.txt");
    assertThat(result).isNull();
  }

  @Test
  void geefExtractieDetailsGeeftNullAlsTaakNietBestaat() {
    when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
        "windmolens", "onbekend.txt")).thenReturn(Optional.empty());

    var result = service.geefExtractieDetails("windmolens", "onbekend.txt");
    assertThat(result).isNull();
  }

  @Test
  void markeerKlaarValideertPassagesEnZetPassageGevonden() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    var pad = Path.of("/tmp/windmolens/bezwaren/bezwaar-001.txt");
    when(projectPoort.geefTekstBestandsPad("windmolens", "bezwaar-001.txt")).thenReturn(pad);
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

    assertThat(taak.isHeeftPassagesDieNietInTekstVoorkomen()).isFalse();
    verify(passageValidator).valideer(any(), any(), any());
  }

  @Test
  void markeerKlaarZetHeeftOpmerkingenBijNietGevondenPassages() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    var pad = Path.of("/tmp/windmolens/bezwaren/bezwaar-001.txt");
    when(projectPoort.geefTekstBestandsPad("windmolens", "bezwaar-001.txt")).thenReturn(pad);
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

    assertThat(taak.isHeeftPassagesDieNietInTekstVoorkomen()).isTrue();
  }

  @Test
  void markeerKlaarGracefulBijOnleesbaarDocument() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    when(projectPoort.geefTekstBestandsPad("windmolens", "bezwaar-001.txt"))
        .thenThrow(new RuntimeException("Bestand niet gevonden"));

    var resultaat = new ExtractieResultaat(100, 1,
        List.of(new Passage(1, "Passage")),
        List.of(new GeextraheerdBezwaar(1, "Samenvatting", "milieu")),
        "Samenvatting");

    service.markeerKlaar(1L, resultaat);

    assertThat(taak.getStatus()).isEqualTo(ExtractieTaakStatus.KLAAR);
    assertThat(taak.isHeeftPassagesDieNietInTekstVoorkomen()).isFalse();
    verify(passageValidator, org.mockito.Mockito.never()).valideer(any(), any(), any());
  }

  @Test
  void voegManueelBezwaarToeMetGeldigePassage() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.KLAAR);
    when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
        "windmolens", "bezwaar-001.txt")).thenReturn(Optional.of(taak));

    var pad = Path.of("/tmp/windmolens/bezwaren/bezwaar-001.txt");
    when(projectPoort.geefTekstBestandsPad("windmolens", "bezwaar-001.txt")).thenReturn(pad);
    when(ingestiePoort.leesBestand(pad)).thenReturn(
        new Brondocument("Dit is de volledige documenttekst met relevante inhoud.",
            "bezwaar-001.txt", pad.toString(), Instant.now()));

    when(passageValidator.normaliseer("Dit is de volledige documenttekst met relevante inhoud."))
        .thenReturn("dit is de volledige documenttekst met relevante inhoud.");
    when(passageValidator.normaliseer("volledige documenttekst"))
        .thenReturn("volledige documenttekst");

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
    // Bezwaar wordt 2x opgeslagen: eerst zonder embedding, dan met embedding
    verify(bezwaarRepository, times(2)).save(bezwaarCaptor.capture());
    assertThat(bezwaarCaptor.getAllValues().get(0).isManueel()).isTrue();
    assertThat(bezwaarCaptor.getAllValues().get(0).getPassageNr()).isEqualTo(4);
    assertThat(bezwaarCaptor.getAllValues().get(1).getEmbeddingPassage()).isNotNull();

    assertThat(taak.isHeeftManueel()).isTrue();
    assertThat(taak.getAantalBezwaren()).isEqualTo(1);

    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void voegManueelBezwaarToeGooitExceptieBijOngeldigePassage() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.KLAAR);
    when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
        "windmolens", "bezwaar-001.txt")).thenReturn(Optional.of(taak));

    var pad = Path.of("/tmp/windmolens/bezwaren/bezwaar-001.txt");
    when(projectPoort.geefTekstBestandsPad("windmolens", "bezwaar-001.txt")).thenReturn(pad);
    when(ingestiePoort.leesBestand(pad)).thenReturn(
        new Brondocument("Dit is de volledige documenttekst.",
            "bezwaar-001.txt", pad.toString(), Instant.now()));

    when(passageValidator.normaliseer("Dit is de volledige documenttekst."))
        .thenReturn("dit is de volledige documenttekst.");
    when(passageValidator.normaliseer("Deze tekst staat er niet in"))
        .thenReturn("deze tekst staat er niet in");

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

    service.verwijderBezwaar("windmolens", "bezwaar-001.txt", 10L);

    verify(bezwaarRepository).delete(bezwaar);
    assertThat(taak.getAantalBezwaren()).isEqualTo(2);
    assertThat(taak.isHeeftManueel()).isTrue();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
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

    service.verwijderBezwaar("windmolens", "bezwaar-001.txt", 10L);

    assertThat(taak.isHeeftManueel()).isFalse();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void verwijderAiBezwaarZetHeeftManueelOp() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.KLAAR);
    taak.setAantalBezwaren(3);
    taak.setHeeftManueel(false);

    var bezwaar = new GeextraheerdBezwaarEntiteit();
    bezwaar.setId(10L);
    bezwaar.setTaakId(1L);
    bezwaar.setManueel(false); // AI-bezwaar
    when(bezwaarRepository.findById(10L)).thenReturn(Optional.of(bezwaar));
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(bezwaarRepository.findByTaakId(1L)).thenReturn(List.of());
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.verwijderBezwaar("windmolens", "bezwaar-001.txt", 10L);

    verify(bezwaarRepository).delete(bezwaar);
    assertThat(taak.getAantalBezwaren()).isEqualTo(2);
    assertThat(taak.isHeeftManueel()).isTrue();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void verwijderBezwaarMetNietGevondenPassageZetHeeftPassagesDieNietInTekstVoorkomendUit() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.KLAAR);
    taak.setAantalBezwaren(2);
    taak.setHeeftPassagesDieNietInTekstVoorkomen(true);
    taak.setHeeftManueel(false);

    var bezwaar = new GeextraheerdBezwaarEntiteit();
    bezwaar.setId(10L);
    bezwaar.setTaakId(1L);
    bezwaar.setManueel(false);
    bezwaar.setPassageGevonden(false); // dit bezwaar veroorzaakte de ⚠️ flag
    when(bezwaarRepository.findById(10L)).thenReturn(Optional.of(bezwaar));
    when(repository.findById(1L)).thenReturn(Optional.of(taak));

    // Na verwijdering: geen bezwaren meer met passageGevonden = false
    when(bezwaarRepository.findByTaakId(1L)).thenReturn(List.of());
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.verwijderBezwaar("windmolens", "bezwaar-001.txt", 10L);

    assertThat(taak.isHeeftPassagesDieNietInTekstVoorkomen()).isFalse();
    assertThat(taak.isHeeftManueel()).isTrue(); // AI-bezwaar verwijderd → manuele wijziging flag
  }

  @Test
  void verwijderBezwaarGooitExceptieBijVerkeerdeProject() {
    var bezwaar = new GeextraheerdBezwaarEntiteit();
    bezwaar.setId(10L);
    bezwaar.setTaakId(1L);
    bezwaar.setManueel(true);
    when(bezwaarRepository.findById(10L)).thenReturn(Optional.of(bezwaar));

    var taak = maakTaak(1L, "ander-project", "bezwaar-001.txt", ExtractieTaakStatus.KLAAR);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));

    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
        service.verwijderBezwaar("windmolens", "bezwaar-001.txt", 10L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("behoort niet tot project");
  }

  @Test
  void indienenRuimtOudeBezwarenEnTakenOpBijHerExtractie() {
    // Maak twee oude taken voor hetzelfde bestand
    var oudeTaak1 = maakTaak(10L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.KLAAR);
    var oudeTaak2 = maakTaak(11L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.FOUT);
    when(repository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt"))
        .thenReturn(List.of(oudeTaak1, oudeTaak2));

    when(repository.save(any(ExtractieTaak.class))).thenAnswer(i -> {
      var t = i.getArgument(0, ExtractieTaak.class);
      t.setId(20L);
      return t;
    });

    service.indienen("windmolens", List.of("bezwaar-001.txt"));

    // Verifieer dat oude bezwaren en passages opgeruimd zijn
    verify(bezwaarRepository).deleteByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt");
    verify(passageRepository).deleteByTaakId(10L);
    verify(passageRepository).deleteByTaakId(11L);
    verify(repository).deleteAll(List.of(oudeTaak1, oudeTaak2));

    // Verifieer dat er een nieuwe WACHTEND taak is aangemaakt
    var captor = ArgumentCaptor.forClass(ExtractieTaak.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ExtractieTaakStatus.WACHTEND);
    assertThat(captor.getValue().getBestandsnaam()).isEqualTo("bezwaar-001.txt");
  }

  @Test
  void markeerKlaarZetProjectNaamEnBestandsnaamOpBezwaren() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    var resultaat = new ExtractieResultaat(100, 1,
        List.of(new Passage(1, "Passage tekst")),
        List.of(new GeextraheerdBezwaar(1, "Samenvatting", "milieu")),
        "Docsamenvatting");

    service.markeerKlaar(1L, resultaat);

    var bezwaarCaptor = ArgumentCaptor.forClass(GeextraheerdBezwaarEntiteit.class);
    verify(bezwaarRepository, times(2)).save(bezwaarCaptor.capture());
    var eersteBezwaar = bezwaarCaptor.getAllValues().get(0);
    assertThat(eersteBezwaar.getProjectNaam()).isEqualTo("windmolens");
    assertThat(eersteBezwaar.getBestandsnaam()).isEqualTo("bezwaar-001.txt");
  }

  @Test
  void voegManueelBezwaarToeZetProjectNaamEnBestandsnaam() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.KLAAR);
    when(repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
        "windmolens", "bezwaar-001.txt")).thenReturn(Optional.of(taak));

    var pad = Path.of("/tmp/windmolens/bezwaren/bezwaar-001.txt");
    when(projectPoort.geefTekstBestandsPad("windmolens", "bezwaar-001.txt")).thenReturn(pad);
    when(ingestiePoort.leesBestand(pad)).thenReturn(
        new Brondocument("Dit is de volledige documenttekst.",
            "bezwaar-001.txt", pad.toString(), Instant.now()));
    when(passageValidator.normaliseer("Dit is de volledige documenttekst."))
        .thenReturn("dit is de volledige documenttekst.");
    when(passageValidator.normaliseer("volledige documenttekst"))
        .thenReturn("volledige documenttekst");
    when(passageRepository.findTopByTaakIdOrderByPassageNrDesc(1L))
        .thenReturn(Optional.empty());
    when(passageRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(bezwaarRepository.save(any())).thenAnswer(i -> {
      var b = i.getArgument(0, GeextraheerdBezwaarEntiteit.class);
      b.setId(10L);
      return b;
    });
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.voegManueelBezwaarToe(
        "windmolens", "bezwaar-001.txt", "Samenvatting test", "volledige documenttekst");

    var bezwaarCaptor = ArgumentCaptor.forClass(GeextraheerdBezwaarEntiteit.class);
    verify(bezwaarRepository, times(2)).save(bezwaarCaptor.capture());
    var eersteBezwaar = bezwaarCaptor.getAllValues().get(0);
    assertThat(eersteBezwaar.getProjectNaam()).isEqualTo("windmolens");
    assertThat(eersteBezwaar.getBestandsnaam()).isEqualTo("bezwaar-001.txt");
  }

  @Test
  void indienenUpdatetBezwaarBestandNaarBezwaarExtractieWachtend() {
    when(repository.save(any())).thenAnswer(i -> {
      var t = i.getArgument(0, ExtractieTaak.class);
      t.setId(1L);
      return t;
    });
    var bestandEntiteit = new BezwaarBestandEntiteit();
    when(bezwaarBestandRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(bestandEntiteit));
    when(bezwaarBestandRepository.save(any(BezwaarBestandEntiteit.class)))
        .thenAnswer(i -> i.getArgument(0));

    service.indienen("windmolens", List.of("bezwaar-001.txt"));

    assertThat(bestandEntiteit.getStatus())
        .isEqualTo(BezwaarBestandStatus.BEZWAAR_EXTRACTIE_WACHTEND);
    verify(bezwaarBestandRepository).save(bestandEntiteit);
  }

  @Test
  void pakOpVoorVerwerkingUpdatetBezwaarBestandNaarBezwaarExtractieBezig() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.WACHTEND);
    when(repository.countByStatus(ExtractieTaakStatus.BEZIG)).thenReturn(0L);
    when(repository.findByStatusOrderByAangemaaktOpAsc(ExtractieTaakStatus.WACHTEND))
        .thenReturn(List.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    var bestandEntiteit = new BezwaarBestandEntiteit();
    when(bezwaarBestandRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(bestandEntiteit));
    when(bezwaarBestandRepository.save(any(BezwaarBestandEntiteit.class)))
        .thenAnswer(i -> i.getArgument(0));

    service.pakOpVoorVerwerking();

    assertThat(bestandEntiteit.getStatus())
        .isEqualTo(BezwaarBestandStatus.BEZWAAR_EXTRACTIE_BEZIG);
    verify(bezwaarBestandRepository).save(bestandEntiteit);
  }

  @Test
  void markeerKlaarUpdatetBezwaarBestandNaarBezwaarExtractieKlaar() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    var bestandEntiteit = new BezwaarBestandEntiteit();
    when(bezwaarBestandRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(bestandEntiteit));
    when(bezwaarBestandRepository.save(any(BezwaarBestandEntiteit.class)))
        .thenAnswer(i -> i.getArgument(0));

    service.markeerKlaar(1L, 500, 7);

    assertThat(bestandEntiteit.getStatus())
        .isEqualTo(BezwaarBestandStatus.BEZWAAR_EXTRACTIE_KLAAR);
    verify(bezwaarBestandRepository).save(bestandEntiteit);
  }

  @Test
  void markeerFoutDefinitiefUpdatetBezwaarBestandNaarBezwaarExtractieFout() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    taak.setAantalPogingen(2);
    taak.setMaxPogingen(3);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    var bestandEntiteit = new BezwaarBestandEntiteit();
    when(bezwaarBestandRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(bestandEntiteit));
    when(bezwaarBestandRepository.save(any(BezwaarBestandEntiteit.class)))
        .thenAnswer(i -> i.getArgument(0));

    service.markeerFout(1L, "Definitieve fout");

    assertThat(bestandEntiteit.getStatus())
        .isEqualTo(BezwaarBestandStatus.BEZWAAR_EXTRACTIE_FOUT);
    verify(bezwaarBestandRepository).save(bestandEntiteit);
  }

  @Test
  void markeerFoutRetryUpdatetBezwaarBestandNaarBezwaarExtractieWachtend() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    var bestandEntiteit = new BezwaarBestandEntiteit();
    when(bezwaarBestandRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(bestandEntiteit));
    when(bezwaarBestandRepository.save(any(BezwaarBestandEntiteit.class)))
        .thenAnswer(i -> i.getArgument(0));

    service.markeerFout(1L, "Timeout bij AI-call");

    assertThat(bestandEntiteit.getStatus())
        .isEqualTo(BezwaarBestandStatus.BEZWAAR_EXTRACTIE_WACHTEND);
    verify(bezwaarBestandRepository).save(bestandEntiteit);
  }

  private ExtractiePassageEntiteit maakPassage(Long taakId, int passageNr) {
    var p = new ExtractiePassageEntiteit();
    p.setTaakId(taakId);
    p.setPassageNr(passageNr);
    p.setTekst("Passage tekst");
    return p;
  }

  private ExtractieTaak maakTaak(Long id, String projectNaam, String bestandsnaam,
      ExtractieTaakStatus status) {
    var taak = new ExtractieTaak();
    taak.setId(id);
    taak.setProjectNaam(projectNaam);
    taak.setBestandsnaam(bestandsnaam);
    taak.setStatus(status);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    taak.setAangemaaktOp(Instant.now());
    return taak;
  }
}
