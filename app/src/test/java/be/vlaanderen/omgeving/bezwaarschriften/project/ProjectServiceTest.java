package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarService;
import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.ExtractieMethode;
import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.TekstExtractieService;
import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.TekstExtractieTaak;
import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.TekstExtractieTaakRepository;
import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.TekstExtractieTaakStatus;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

  @Mock
  private ProjectPoort projectPoort;

  @Mock
  private ExtractieTaakRepository extractieTaakRepository;

  @Mock
  private KernbezwaarService kernbezwaarService;

  @Mock
  private ConsolidatieTaakRepository consolidatieTaakRepository;

  @Mock
  private TekstExtractieService tekstExtractieService;

  @Mock
  private TekstExtractieTaakRepository tekstExtractieTaakRepository;

  @Mock
  private BezwaarBestandRepository bezwaarBestandRepository;

  private ProjectService service;

  @BeforeEach
  void setUp() {
    service = new ProjectService(projectPoort, extractieTaakRepository,
        kernbezwaarService, consolidatieTaakRepository,
        tekstExtractieService, tekstExtractieTaakRepository,
        bezwaarBestandRepository);
  }

  @Test
  void geeftProjectenTerug() {
    when(projectPoort.geefProjecten()).thenReturn(List.of("windmolens", "zonnepanelen"));

    var projecten = service.geefProjecten();

    assertThat(projecten).containsExactly("windmolens", "zonnepanelen");
  }

  @Test
  void geefBezwarenLeestStatusUitDatabase() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.txt"));
    when(bezwaarBestandRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(new BezwaarBestandEntiteit("windmolens", "bezwaar-001.txt",
            BezwaarBestandStatus.TEKST_EXTRACTIE_BEZIG)));

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren).hasSize(1);
    assertThat(bezwaren.get(0).bestandsnaam()).isEqualTo("bezwaar-001.txt");
    assertThat(bezwaren.get(0).status()).isEqualTo(BezwaarBestandStatus.TEKST_EXTRACTIE_BEZIG);
  }

  @Test
  void geefBezwarenGeeftNietOndersteundVoorOnbekendFormaat() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bijlage.jpg"));
    when(bezwaarBestandRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of());

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren).hasSize(1);
    assertThat(bezwaren.get(0).bestandsnaam()).isEqualTo("bijlage.jpg");
    assertThat(bezwaren.get(0).status()).isEqualTo(BezwaarBestandStatus.NIET_ONDERSTEUND);
  }

  @Test
  void geefBezwarenGeeftTodoVoorLegacyBestandZonderEntiteit() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.pdf"));
    when(bezwaarBestandRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of());

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren).hasSize(1);
    assertThat(bezwaren.get(0).status()).isEqualTo(BezwaarBestandStatus.TODO);
  }

  @Test
  void geefBezwarenGeeftTekstExtractieKlaarVoorLegacyTxtZonderEntiteit() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.txt"));
    when(bezwaarBestandRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of());

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren).hasSize(1);
    assertThat(bezwaren.get(0).status()).isEqualTo(BezwaarBestandStatus.TEKST_EXTRACTIE_KLAAR);
  }

  @Test
  void geefBezwarenVultMetadataAanUitTaakTabellen() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.txt"));
    when(bezwaarBestandRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(new BezwaarBestandEntiteit("windmolens", "bezwaar-001.txt",
            BezwaarBestandStatus.BEZWAAR_EXTRACTIE_KLAAR)));
    var tekstTaak = maakTekstExtractieTaak(TekstExtractieTaakStatus.KLAAR,
        ExtractieMethode.DIGITAAL);
    when(tekstExtractieTaakRepository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(tekstTaak));
    var taak = maakTaak(ExtractieTaakStatus.KLAAR, 150, 3);
    when(extractieTaakRepository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(taak));

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren).hasSize(1);
    assertThat(bezwaren.get(0).status()).isEqualTo(BezwaarBestandStatus.BEZWAAR_EXTRACTIE_KLAAR);
    assertThat(bezwaren.get(0).aantalWoorden()).isEqualTo(150);
    assertThat(bezwaren.get(0).aantalBezwaren()).isEqualTo(3);
    assertThat(bezwaren.get(0).extractieMethode()).isEqualTo("DIGITAAL");
  }

  @Test
  void geefBezwarenVultTimerInfoAanUitTekstExtractieTaak() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.pdf"));
    when(bezwaarBestandRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(new BezwaarBestandEntiteit("windmolens", "bezwaar-001.pdf",
            BezwaarBestandStatus.TEKST_EXTRACTIE_BEZIG)));
    var tekstTaak = maakTekstExtractieTaak(TekstExtractieTaakStatus.BEZIG, null);
    when(tekstExtractieTaakRepository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc("windmolens", "bezwaar-001.pdf"))
        .thenReturn(Optional.of(tekstTaak));

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren.get(0).tekstExtractieAangemaaktOp()).isNotNull();
    assertThat(bezwaren.get(0).tekstExtractieTaakId()).isNotNull();
  }

  @Test
  void geefBezwarenVultFoutmeldingAanUitTekstExtractieTaak() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.pdf"));
    when(bezwaarBestandRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(new BezwaarBestandEntiteit("windmolens", "bezwaar-001.pdf",
            BezwaarBestandStatus.TEKST_EXTRACTIE_MISLUKT)));
    var tekstTaak = maakTekstExtractieTaak(TekstExtractieTaakStatus.MISLUKT, null);
    tekstTaak.setFoutmelding("Te weinig woorden: 28 (minimum 40)");
    when(tekstExtractieTaakRepository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc("windmolens", "bezwaar-001.pdf"))
        .thenReturn(Optional.of(tekstTaak));

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren.get(0).status()).isEqualTo(BezwaarBestandStatus.TEKST_EXTRACTIE_MISLUKT);
    assertThat(bezwaren.get(0).tekstExtractieFoutmelding())
        .isEqualTo("Te weinig woorden: 28 (minimum 40)");
  }

  @Test
  void gooidExceptionVoorOnbekendProjectBijGeefBezwaren() {
    when(projectPoort.geefBestandsnamen("bestaat-niet"))
        .thenThrow(new ProjectNietGevondenException("bestaat-niet"));

    assertThrows(
        ProjectNietGevondenException.class,
        () -> service.geefBezwaren("bestaat-niet")
    );
  }

  @Test
  void geefBestandsPad_delegeertNaarPoort() {
    var verwacht = Path.of("/tmp/test/bezwaren/bezwaar1.txt");
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar1.txt")).thenReturn(verwacht);

    var result = service.geefBestandsPad("windmolens", "bezwaar1.txt");

    assertThat(result).isEqualTo(verwacht);
    verify(projectPoort).geefBestandsPad("windmolens", "bezwaar1.txt");
  }

  @Test
  void uploadStartAutomatischTekstExtractie() {
    when(projectPoort.geefBestandsnamen("windmolens")).thenReturn(List.of());

    var bestanden = new LinkedHashMap<String, byte[]>();
    bestanden.put("bezwaar.txt", "inhoud".getBytes());
    bestanden.put("bezwaar.pdf", "pdf-inhoud".getBytes());

    service.uploadBezwaren("windmolens", bestanden);

    verify(tekstExtractieService).indienen("windmolens", "bezwaar.txt");
    verify(tekstExtractieService).indienen("windmolens", "bezwaar.pdf");
  }

  @Test
  void uploadMaaktBezwaarBestandEntiteitAanMetStatusTodo() {
    when(projectPoort.geefBestandsnamen("windmolens")).thenReturn(List.of());

    var bestanden = new LinkedHashMap<String, byte[]>();
    bestanden.put("bezwaar.txt", "inhoud".getBytes());
    bestanden.put("bezwaar.pdf", "pdf-inhoud".getBytes());

    service.uploadBezwaren("windmolens", bestanden);

    var captor = ArgumentCaptor.forClass(BezwaarBestandEntiteit.class);
    verify(bezwaarBestandRepository, times(2)).save(captor.capture());
    var entiteiten = captor.getAllValues();
    assertThat(entiteiten).allSatisfy(e -> {
      assertThat(e.getProjectNaam()).isEqualTo("windmolens");
      assertThat(e.getStatus()).isEqualTo(BezwaarBestandStatus.TODO);
    });
    assertThat(entiteiten).extracting(BezwaarBestandEntiteit::getBestandsnaam)
        .containsExactly("bezwaar.txt", "bezwaar.pdf");
  }

  @Test
  void uploadMaaktGeenEntiteitVoorNietOndersteundFormaat() {
    when(projectPoort.geefBestandsnamen("windmolens")).thenReturn(List.of());

    var bestanden = new LinkedHashMap<String, byte[]>();
    bestanden.put("foto.jpg", "jpg-inhoud".getBytes());

    service.uploadBezwaren("windmolens", bestanden);

    verify(bezwaarBestandRepository, never()).save(any());
  }

  @Test
  void uploadWeigertNietOndersteundFormaatMaarAccepteertPdfEnTxt() {
    when(projectPoort.geefBestandsnamen("windmolens")).thenReturn(List.of());

    var bestanden = new LinkedHashMap<String, byte[]>();
    bestanden.put("bezwaar.txt", "inhoud".getBytes());
    bestanden.put("bezwaar.pdf", "pdf-inhoud".getBytes());
    bestanden.put("foto.jpg", "jpg-inhoud".getBytes());

    var resultaat = service.uploadBezwaren("windmolens", bestanden);

    assertThat(resultaat.geupload()).containsExactly("bezwaar.txt", "bezwaar.pdf");
    assertThat(resultaat.fouten()).hasSize(1);
    assertThat(resultaat.fouten().get(0).bestandsnaam()).isEqualTo("foto.jpg");
  }

  private ExtractieTaak maakTaak(ExtractieTaakStatus status, Integer aantalWoorden,
      Integer aantalBezwaren) {
    var taak = new ExtractieTaak();
    taak.setProjectNaam("windmolens");
    taak.setBestandsnaam("bezwaar-001.txt");
    taak.setStatus(status);
    taak.setAantalWoorden(aantalWoorden);
    taak.setAantalBezwaren(aantalBezwaren);
    taak.setAangemaaktOp(Instant.now());
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    return taak;
  }

  private TekstExtractieTaak maakTekstExtractieTaak(TekstExtractieTaakStatus status,
      ExtractieMethode methode) {
    var taak = new TekstExtractieTaak();
    taak.setId(42L);
    taak.setProjectNaam("windmolens");
    taak.setBestandsnaam("bezwaar-001.txt");
    taak.setStatus(status);
    taak.setExtractieMethode(methode);
    taak.setAangemaaktOp(Instant.now());
    return taak;
  }

  @Test
  void maakProjectAan_delegeertNaarPoort() {
    service.maakProjectAan("nieuw-project");

    verify(projectPoort).maakProjectAan("nieuw-project");
  }

  @Test
  void verwijderBezwaar_ruimtKernbezwaarDataEnTekstExtractieOpVoorVerwijdering() {
    when(projectPoort.verwijderBestand("windmolens", "bezwaar-001.txt")).thenReturn(true);

    service.verwijderBezwaar("windmolens", "bezwaar-001.txt");

    var inOrder = inOrder(kernbezwaarService, extractieTaakRepository,
        tekstExtractieTaakRepository, projectPoort);
    inOrder.verify(kernbezwaarService).ruimOpNaDocumentVerwijdering("windmolens", "bezwaar-001.txt");
    inOrder.verify(extractieTaakRepository).deleteByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt");
    inOrder.verify(tekstExtractieTaakRepository).deleteByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt");
    inOrder.verify(projectPoort).verwijderBestand("windmolens", "bezwaar-001.txt");
  }

  @Test
  void verwijderProject_verwijdertAlleDataInclusiefTekstExtractieTaken() {
    when(projectPoort.verwijderProject("oud-project")).thenReturn(true);

    boolean result = service.verwijderProject("oud-project");

    assertThat(result).isTrue();
    var inOrder = inOrder(kernbezwaarService, consolidatieTaakRepository,
        extractieTaakRepository, tekstExtractieTaakRepository, projectPoort);
    inOrder.verify(kernbezwaarService).ruimAllesOpVoorProject("oud-project");
    inOrder.verify(consolidatieTaakRepository).deleteByProjectNaam("oud-project");
    inOrder.verify(extractieTaakRepository).deleteByProjectNaam("oud-project");
    inOrder.verify(tekstExtractieTaakRepository).deleteByProjectNaam("oud-project");
    inOrder.verify(projectPoort).verwijderProject("oud-project");
  }

  @Test
  void verwijderProject_geeftFalseAlsProjectNietBestaat() {
    when(projectPoort.verwijderProject("bestaat-niet")).thenReturn(false);

    boolean result = service.verwijderProject("bestaat-niet");

    assertThat(result).isFalse();
    verify(kernbezwaarService).ruimAllesOpVoorProject("bestaat-niet");
    verify(consolidatieTaakRepository).deleteByProjectNaam("bestaat-niet");
    verify(extractieTaakRepository).deleteByProjectNaam("bestaat-niet");
    verify(tekstExtractieTaakRepository).deleteByProjectNaam("bestaat-niet");
  }

  @Test
  void geeftProjectenMetAantalDocumenten() {
    when(projectPoort.geefProjecten()).thenReturn(List.of("windmolens", "leeg-project"));
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar1.txt", "bezwaar2.txt", "bijlage.pdf"));
    when(projectPoort.geefBestandsnamen("leeg-project"))
        .thenReturn(List.of());

    var resultaat = service.geefProjectenMetAantalDocumenten();

    assertThat(resultaat).hasSize(2);
    assertThat(resultaat.get(0).naam()).isEqualTo("windmolens");
    assertThat(resultaat.get(0).aantalDocumenten()).isEqualTo(3);
    assertThat(resultaat.get(1).naam()).isEqualTo("leeg-project");
    assertThat(resultaat.get(1).aantalDocumenten()).isEqualTo(0);
  }

  @Test
  void verwijderBezwaren_verwijdertAlleBestandenEnRuimtKernbezwaarDataOp() {
    var bestandsnamen = List.of("bezwaar-001.txt", "bezwaar-002.txt", "bezwaar-003.txt");
    when(projectPoort.verwijderBestand(eq("windmolens"), anyString())).thenReturn(true);

    int aantalVerwijderd = service.verwijderBezwaren("windmolens", bestandsnamen);

    assertThat(aantalVerwijderd).isEqualTo(3);

    verify(extractieTaakRepository).deleteByProjectNaamAndBestandsnaamIn(
        "windmolens", bestandsnamen);

    verify(kernbezwaarService, times(1)).ruimOpNaBestandenVerwijdering("windmolens", bestandsnamen);

    for (String naam : bestandsnamen) {
      verify(projectPoort).verwijderBestand("windmolens", naam);
    }
  }

  @Test
  void verwijderBezwaren_teltEnkelSuccesvolVerwijderdeBestandenMee() {
    when(projectPoort.verwijderBestand("windmolens", "bestaat.txt")).thenReturn(true);
    when(projectPoort.verwijderBestand("windmolens", "bestaat-niet.txt")).thenReturn(false);

    int aantalVerwijderd = service.verwijderBezwaren("windmolens",
        List.of("bestaat.txt", "bestaat-niet.txt"));

    assertThat(aantalVerwijderd).isEqualTo(1);
  }
}
