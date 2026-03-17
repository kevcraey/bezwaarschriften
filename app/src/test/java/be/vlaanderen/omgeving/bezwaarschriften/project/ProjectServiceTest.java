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
import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.TekstExtractieService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
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
  private BezwaarDocumentRepository bezwaarDocumentRepository;

  @Mock
  private IndividueelBezwaarRepository bezwaarRepository;

  @Mock
  private KernbezwaarService kernbezwaarService;

  @Mock
  private ConsolidatieTaakRepository consolidatieTaakRepository;

  @Mock
  private TekstExtractieService tekstExtractieService;

  private ProjectService service;

  @BeforeEach
  void setUp() {
    service = new ProjectService(projectPoort, bezwaarDocumentRepository,
        bezwaarRepository, kernbezwaarService, consolidatieTaakRepository,
        tekstExtractieService);
  }

  @Test
  void geeftProjectenTerug() {
    when(projectPoort.geefProjecten()).thenReturn(List.of("windmolens", "zonnepanelen"));

    var projecten = service.geefProjecten();

    assertThat(projecten).containsExactly("windmolens", "zonnepanelen");
  }

  @Test
  void geefBezwarenLeestStatusUitBezwaarDocument() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.txt"));
    var doc = maakDocument("windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.BEZIG, BezwaarExtractieStatus.GEEN);
    when(bezwaarDocumentRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(doc));

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren).hasSize(1);
    assertThat(bezwaren.get(0).bestandsnaam()).isEqualTo("bezwaar-001.txt");
    assertThat(bezwaren.get(0).tekstExtractieStatus()).isEqualTo("BEZIG");
    assertThat(bezwaren.get(0).bezwaarExtractieStatus()).isEqualTo("GEEN");
  }

  @Test
  void geefBezwarenGeeftNietOndersteundVoorOnbekendFormaat() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bijlage.jpg"));
    when(bezwaarDocumentRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of());

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren).hasSize(1);
    assertThat(bezwaren.get(0).bestandsnaam()).isEqualTo("bijlage.jpg");
    assertThat(bezwaren.get(0).tekstExtractieStatus()).isEqualTo("NIET_ONDERSTEUND");
    assertThat(bezwaren.get(0).bezwaarExtractieStatus()).isEqualTo("GEEN");
  }

  @Test
  void geefBezwarenGeeftGeenVoorBestandZonderDocument() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.pdf"));
    when(bezwaarDocumentRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of());

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren).hasSize(1);
    assertThat(bezwaren.get(0).tekstExtractieStatus()).isEqualTo("GEEN");
    assertThat(bezwaren.get(0).bezwaarExtractieStatus()).isEqualTo("GEEN");
  }

  @Test
  void geefBezwarenVultMetadataUitBezwaarDocument() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.txt"));
    var doc = maakDocument("windmolens", "bezwaar-001.txt",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.KLAAR);
    doc.setAantalWoorden(150);
    doc.setExtractieMethode("DIGITAAL");
    doc.setId(1L);
    when(bezwaarDocumentRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(doc));
    when(bezwaarRepository.countByDocumentIdIn(List.of(1L)))
        .thenReturn(List.of(new Object[]{1L, 3L}));

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren).hasSize(1);
    assertThat(bezwaren.get(0).tekstExtractieStatus()).isEqualTo("KLAAR");
    assertThat(bezwaren.get(0).bezwaarExtractieStatus()).isEqualTo("KLAAR");
    assertThat(bezwaren.get(0).aantalWoorden()).isEqualTo(150);
    assertThat(bezwaren.get(0).aantalBezwaren()).isEqualTo(3);
    assertThat(bezwaren.get(0).extractieMethode()).isEqualTo("DIGITAAL");
  }

  @Test
  void geefBezwarenVultFoutmeldingUitBezwaarDocument() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.pdf"));
    var doc = maakDocument("windmolens", "bezwaar-001.pdf",
        TekstExtractieStatus.FOUT, BezwaarExtractieStatus.GEEN);
    doc.setFoutmelding("Te weinig woorden: 28 (minimum 40)");
    when(bezwaarDocumentRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(doc));

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren.get(0).tekstExtractieStatus()).isEqualTo("FOUT");
    assertThat(bezwaren.get(0).foutmelding())
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
  void uploadMaaktBezwaarDocumentAan() {
    when(projectPoort.geefBestandsnamen("windmolens")).thenReturn(List.of());

    var bestanden = new LinkedHashMap<String, byte[]>();
    bestanden.put("bezwaar.txt", "inhoud".getBytes());
    bestanden.put("bezwaar.pdf", "pdf-inhoud".getBytes());

    service.uploadBezwaren("windmolens", bestanden);

    var captor = ArgumentCaptor.forClass(BezwaarDocument.class);
    verify(bezwaarDocumentRepository, times(2)).save(captor.capture());
    var documenten = captor.getAllValues();
    assertThat(documenten).allSatisfy(d -> {
      assertThat(d.getProjectNaam()).isEqualTo("windmolens");
      assertThat(d.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.GEEN);
      assertThat(d.getBezwaarExtractieStatus()).isEqualTo(BezwaarExtractieStatus.GEEN);
    });
    assertThat(documenten).extracting(BezwaarDocument::getBestandsnaam)
        .containsExactly("bezwaar.txt", "bezwaar.pdf");
  }

  @Test
  void uploadMaaktGeenDocumentVoorNietOndersteundFormaat() {
    when(projectPoort.geefBestandsnamen("windmolens")).thenReturn(List.of());

    var bestanden = new LinkedHashMap<String, byte[]>();
    bestanden.put("foto.jpg", "jpg-inhoud".getBytes());

    service.uploadBezwaren("windmolens", bestanden);

    verify(bezwaarDocumentRepository, never()).save(any());
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

  @Test
  void maakProjectAan_delegeertNaarPoort() {
    service.maakProjectAan("nieuw-project");

    verify(projectPoort).maakProjectAan("nieuw-project");
  }

  @Test
  void verwijderBezwaar_ruimtKernbezwaarDataEnDocumentOp() {
    when(projectPoort.verwijderBestand("windmolens", "bezwaar-001.txt")).thenReturn(true);

    service.verwijderBezwaar("windmolens", "bezwaar-001.txt");

    var inOrder = inOrder(kernbezwaarService, bezwaarDocumentRepository, projectPoort);
    inOrder.verify(kernbezwaarService).ruimOpNaDocumentVerwijdering("windmolens", "bezwaar-001.txt");
    inOrder.verify(bezwaarDocumentRepository).deleteByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt");
    inOrder.verify(projectPoort).verwijderBestand("windmolens", "bezwaar-001.txt");
  }

  @Test
  void verwijderProject_verwijdertAlleData() {
    when(projectPoort.verwijderProject("oud-project")).thenReturn(true);

    boolean result = service.verwijderProject("oud-project");

    assertThat(result).isTrue();
    var inOrder = inOrder(kernbezwaarService, consolidatieTaakRepository,
        bezwaarDocumentRepository, projectPoort);
    inOrder.verify(kernbezwaarService).ruimAllesOpVoorProject("oud-project");
    inOrder.verify(consolidatieTaakRepository).deleteByProjectNaam("oud-project");
    inOrder.verify(bezwaarDocumentRepository).deleteByProjectNaam("oud-project");
    inOrder.verify(projectPoort).verwijderProject("oud-project");
  }

  @Test
  void verwijderProject_geeftFalseAlsProjectNietBestaat() {
    when(projectPoort.verwijderProject("bestaat-niet")).thenReturn(false);

    boolean result = service.verwijderProject("bestaat-niet");

    assertThat(result).isFalse();
    verify(kernbezwaarService).ruimAllesOpVoorProject("bestaat-niet");
    verify(consolidatieTaakRepository).deleteByProjectNaam("bestaat-niet");
    verify(bezwaarDocumentRepository).deleteByProjectNaam("bestaat-niet");
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

    verify(bezwaarDocumentRepository).deleteByProjectNaamAndBestandsnaamIn(
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

  @Test
  void uploadRuimtBestandOpAlsDatabaseSaveFaalt() {
    when(projectPoort.geefBestandsnamen("windmolens")).thenReturn(List.of());
    when(bezwaarDocumentRepository.save(any(BezwaarDocument.class)))
        .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
            "unique constraint violated"));

    var bestanden = new LinkedHashMap<String, byte[]>();
    bestanden.put("v2-001.pdf", "pdf-inhoud".getBytes());

    org.junit.jupiter.api.Assertions.assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> service.uploadBezwaren("windmolens", bestanden));

    verify(projectPoort).slaBestandOp("windmolens", "v2-001.pdf", "pdf-inhoud".getBytes());
    verify(projectPoort).verwijderBestand("windmolens", "v2-001.pdf");
  }

  private BezwaarDocument maakDocument(String projectNaam, String bestandsnaam,
      TekstExtractieStatus tekstStatus, BezwaarExtractieStatus bezwaarStatus) {
    var doc = new BezwaarDocument();
    doc.setProjectNaam(projectNaam);
    doc.setBestandsnaam(bestandsnaam);
    doc.setTekstExtractieStatus(tekstStatus);
    doc.setBezwaarExtractieStatus(bezwaarStatus);
    return doc;
  }
}
