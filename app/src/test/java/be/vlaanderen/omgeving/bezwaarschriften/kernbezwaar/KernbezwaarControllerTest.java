package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakService;
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
  private PassageGroepLidRepository passageGroepLidRepository;

  private KernbezwaarController controller;

  @BeforeEach
  void setUp() {
    controller = new KernbezwaarController(
        kernbezwaarService, referentieRepository, consolidatieTaakService,
        passageGroepLidRepository);
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
    ref.setPassageGroepId(100L);
    when(referentieRepository.findByKernbezwaarIdIn(List.of(42L)))
        .thenReturn(List.of(ref));

    // Passage groep lid met bestandsnaam
    var lid = new PassageGroepLidEntiteit();
    lid.setPassageGroepId(100L);
    lid.setBezwaarId(1L);
    lid.setBestandsnaam("bezwaar.pdf");
    when(passageGroepLidRepository.findByPassageGroepIdIn(List.of(100L)))
        .thenReturn(List.of(lid));

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
    ref.setPassageGroepId(100L);
    when(referentieRepository.findByKernbezwaarIdIn(List.of(42L)))
        .thenReturn(List.of(ref));

    var lid = new PassageGroepLidEntiteit();
    lid.setPassageGroepId(100L);
    lid.setBezwaarId(1L);
    lid.setBestandsnaam("bezwaar-001.txt");
    when(passageGroepLidRepository.findByPassageGroepIdIn(List.of(100L)))
        .thenReturn(List.of(lid));

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
    ref.setPassageGroepId(100L);
    when(referentieRepository.findByKernbezwaarIdIn(List.of(42L)))
        .thenReturn(List.of(ref));

    var lid = new PassageGroepLidEntiteit();
    lid.setPassageGroepId(100L);
    lid.setBezwaarId(1L);
    lid.setBestandsnaam("bezwaar-001.txt");
    when(passageGroepLidRepository.findByPassageGroepIdIn(List.of(100L)))
        .thenReturn(List.of(lid));

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
