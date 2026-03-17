package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakService;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocument;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocumentRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.IndividueelBezwaar;
import be.vlaanderen.omgeving.bezwaarschriften.project.IndividueelBezwaarRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class KernbezwaarControllerTest {

  @Mock
  private KernbezwaarService kernbezwaarService;

  @Mock
  private KernbezwaarReferentieRepository referentieRepository;

  @Mock
  private ConsolidatieTaakService consolidatieTaakService;

  @Mock
  private BezwaarGroepLidRepository bezwaarGroepLidRepository;

  @Mock
  private IndividueelBezwaarRepository bezwaarRepository;

  @Mock
  private BezwaarDocumentRepository documentRepository;

  private KernbezwaarController controller;

  @BeforeEach
  void setUp() {
    controller = new KernbezwaarController(
        kernbezwaarService, referentieRepository, consolidatieTaakService,
        bezwaarGroepLidRepository, bezwaarRepository, documentRepository);
  }

  @Test
  void geefKernbezwarenRetourneertCachedResultaat() {
    var kernbezwaar = new Kernbezwaar(1L, "samenvatting", List.of(
        new IndividueelBezwaarReferentie(
            100L, "samenvatting", "passage", null, ToewijzingsMethode.HDBSCAN, null)),
        null);
    when(kernbezwaarService.geefKernbezwaren("windmolens"))
        .thenReturn(Optional.of(List.of(kernbezwaar)));

    var response = controller.geefKernbezwaren("windmolens");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().kernbezwaren()).hasSize(1);
  }

  @Test
  void geefKernbezwarenRetourneert404AlsNogNietGegroepeerd() {
    when(kernbezwaarService.geefKernbezwaren("windmolens"))
        .thenReturn(Optional.empty());

    var response = controller.geefKernbezwaren("windmolens");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void slaatAntwoordOp() {
    // Referentie met passage groep
    var ref = new KernbezwaarReferentieEntiteit();
    ref.setId(1L);
    ref.setKernbezwaarId(42L);
    ref.setBezwaarGroepId(100L);
    when(referentieRepository.findByKernbezwaarIdIn(List.of(42L)))
        .thenReturn(List.of(ref));

    // Bezwaar groep lid
    var lid = new BezwaarGroepLid();
    lid.setBezwaarGroepId(100L);
    lid.setBezwaarId(1L);
    when(bezwaarGroepLidRepository.findByBezwaarGroepIdIn(List.of(100L)))
        .thenReturn(List.of(lid));

    // Bezwaar -> document -> bestandsnaam
    var bezwaar = new IndividueelBezwaar();
    bezwaar.setId(1L);
    bezwaar.setDocumentId(500L);
    when(bezwaarRepository.findAllById(List.of(1L)))
        .thenReturn(List.of(bezwaar));

    var document = new BezwaarDocument();
    document.setId(500L);
    document.setBestandsnaam("bezwaar.pdf");
    when(documentRepository.findAllById(List.of(500L)))
        .thenReturn(List.of(document));

    when(consolidatieTaakService.vindKlareBestandsnamen("windmolens",
        List.of("bezwaar.pdf"))).thenReturn(List.of());

    var request = new KernbezwaarController.AntwoordRequest("<p>Weerwoord</p>");
    var response = controller.slaAntwoordOp("windmolens", 42L, request, false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(kernbezwaarService).slaAntwoordOp(42L, "<p>Weerwoord</p>");
  }

  @Test
  void retourneert409BijKlareConsolidaties() {
    var ref = new KernbezwaarReferentieEntiteit();
    ref.setId(1L);
    ref.setKernbezwaarId(42L);
    ref.setBezwaarGroepId(100L);
    when(referentieRepository.findByKernbezwaarIdIn(List.of(42L)))
        .thenReturn(List.of(ref));

    var lid = new BezwaarGroepLid();
    lid.setBezwaarGroepId(100L);
    lid.setBezwaarId(1L);
    when(bezwaarGroepLidRepository.findByBezwaarGroepIdIn(List.of(100L)))
        .thenReturn(List.of(lid));

    var bezwaar = new IndividueelBezwaar();
    bezwaar.setId(1L);
    bezwaar.setDocumentId(500L);
    when(bezwaarRepository.findAllById(List.of(1L)))
        .thenReturn(List.of(bezwaar));

    var document = new BezwaarDocument();
    document.setId(500L);
    document.setBestandsnaam("bezwaar-001.txt");
    when(documentRepository.findAllById(List.of(500L)))
        .thenReturn(List.of(document));

    when(consolidatieTaakService.vindKlareBestandsnamen("windmolens",
        List.of("bezwaar-001.txt"))).thenReturn(List.of("bezwaar-001.txt"));

    var request = new KernbezwaarController.AntwoordRequest("<p>Nieuw</p>");
    var response = controller.slaAntwoordOp("windmolens", 42L, request, false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    verifyNoInteractions(kernbezwaarService);
  }

  @Test
  void verwijdertKlareConsolidatiesBijBevestiging() {
    var ref = new KernbezwaarReferentieEntiteit();
    ref.setId(1L);
    ref.setKernbezwaarId(42L);
    ref.setBezwaarGroepId(100L);
    when(referentieRepository.findByKernbezwaarIdIn(List.of(42L)))
        .thenReturn(List.of(ref));

    var lid = new BezwaarGroepLid();
    lid.setBezwaarGroepId(100L);
    lid.setBezwaarId(1L);
    when(bezwaarGroepLidRepository.findByBezwaarGroepIdIn(List.of(100L)))
        .thenReturn(List.of(lid));

    var bezwaar = new IndividueelBezwaar();
    bezwaar.setId(1L);
    bezwaar.setDocumentId(500L);
    when(bezwaarRepository.findAllById(List.of(1L)))
        .thenReturn(List.of(bezwaar));

    var document = new BezwaarDocument();
    document.setId(500L);
    document.setBestandsnaam("bezwaar-001.txt");
    when(documentRepository.findAllById(List.of(500L)))
        .thenReturn(List.of(document));

    when(consolidatieTaakService.vindKlareBestandsnamen("windmolens",
        List.of("bezwaar-001.txt"))).thenReturn(List.of("bezwaar-001.txt"));

    var request = new KernbezwaarController.AntwoordRequest("<p>Nieuw</p>");
    var response = controller.slaAntwoordOp("windmolens", 42L, request, true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(consolidatieTaakService).verwijderKlareTaken("windmolens",
        List.of("bezwaar-001.txt"));
    verify(kernbezwaarService).slaAntwoordOp(42L, "<p>Nieuw</p>");
  }
}
