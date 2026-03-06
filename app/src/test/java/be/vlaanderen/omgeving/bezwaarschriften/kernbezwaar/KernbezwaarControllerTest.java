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

  private KernbezwaarController controller;

  @BeforeEach
  void setUp() {
    controller = new KernbezwaarController(
        kernbezwaarService, referentieRepository, consolidatieTaakService);
  }

  @Test
  void groepeerRetourneertThemas() {
    var thema = new Thema("Geluid", List.of(
        new Kernbezwaar(1L, "samenvatting", List.of(
            new IndividueelBezwaarReferentie(1L, "b1.txt", "passage", null)), null)));
    when(kernbezwaarService.groepeer("windmolens")).thenReturn(List.of(thema));

    var response = controller.groepeer("windmolens");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().themas()).hasSize(1);
    assertThat(response.getBody().themas().get(0).naam()).isEqualTo("Geluid");
    verify(kernbezwaarService).groepeer("windmolens");
  }

  @Test
  void geefKernbezwarenRetourneertCachedResultaat() {
    var thema = new Thema("Mobiliteit", List.of());
    when(kernbezwaarService.geefKernbezwaren("windmolens"))
        .thenReturn(Optional.of(List.of(thema)));

    var response = controller.geefKernbezwaren("windmolens");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().themas()).hasSize(1);
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
    var request = new KernbezwaarController.AntwoordRequest("<p>Weerwoord</p>");
    when(referentieRepository.findBestandsnamenByKernbezwaarId(42L))
        .thenReturn(List.of("bezwaar-001.txt"));
    when(consolidatieTaakService.vindKlareBestandsnamen("windmolens",
        List.of("bezwaar-001.txt"))).thenReturn(List.of());

    var response = controller.slaAntwoordOp("windmolens", 42L, request, false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(kernbezwaarService).slaAntwoordOp(42L, "<p>Weerwoord</p>");
  }

  @Test
  void retourneert409BijKlareConsolidaties() {
    var request = new KernbezwaarController.AntwoordRequest("<p>Nieuw</p>");
    when(referentieRepository.findBestandsnamenByKernbezwaarId(42L))
        .thenReturn(List.of("bezwaar-001.txt"));
    when(consolidatieTaakService.vindKlareBestandsnamen("windmolens",
        List.of("bezwaar-001.txt"))).thenReturn(List.of("bezwaar-001.txt"));

    var response = controller.slaAntwoordOp("windmolens", 42L, request, false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    verifyNoInteractions(kernbezwaarService);
  }

  @Test
  void verwijdertKlareConsolidatiesBijBevestiging() {
    var request = new KernbezwaarController.AntwoordRequest("<p>Nieuw</p>");
    when(referentieRepository.findBestandsnamenByKernbezwaarId(42L))
        .thenReturn(List.of("bezwaar-001.txt"));
    when(consolidatieTaakService.vindKlareBestandsnamen("windmolens",
        List.of("bezwaar-001.txt"))).thenReturn(List.of("bezwaar-001.txt"));

    var response = controller.slaAntwoordOp("windmolens", 42L, request, true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(consolidatieTaakService).verwijderKlareTaken("windmolens",
        List.of("bezwaar-001.txt"));
    verify(kernbezwaarService).slaAntwoordOp(42L, "<p>Nieuw</p>");
  }
}
