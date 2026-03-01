package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.project.ProjectService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KernbezwaarServiceTest {

  @Mock
  private KernbezwaarPoort kernbezwaarPoort;

  @Mock
  private ProjectService projectService;

  private KernbezwaarService service;

  @BeforeEach
  void setUp() {
    service = new KernbezwaarService(kernbezwaarPoort, projectService);
  }

  @Test
  void groepeertBezwarenVanProject() {
    when(projectService.geefBezwaartekstenVoorGroepering("windmolens"))
        .thenReturn(List.of(
            new KernbezwaarPoort.BezwaarInvoer(1L, "bezwaar1.txt", "tekst")));
    var thema = new Thema("Geluid", List.of(
        new Kernbezwaar(1L, "samenvatting", List.of(
            new IndividueelBezwaarReferentie(1L, "bezwaar1.txt", "passage")), null)));
    when(kernbezwaarPoort.groepeer(anyList())).thenReturn(List.of(thema));

    var resultaat = service.groepeer("windmolens");

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).naam()).isEqualTo("Geluid");
    verify(projectService).geefBezwaartekstenVoorGroepering("windmolens");
    verify(kernbezwaarPoort).groepeer(anyList());
  }

  @Test
  void cachetResultaatNaGroepering() {
    when(projectService.geefBezwaartekstenVoorGroepering("windmolens"))
        .thenReturn(List.of(
            new KernbezwaarPoort.BezwaarInvoer(1L, "b1.txt", "t")));
    var thema = new Thema("Mobiliteit", List.of());
    when(kernbezwaarPoort.groepeer(anyList())).thenReturn(List.of(thema));

    service.groepeer("windmolens");

    var gecached = service.geefKernbezwaren("windmolens");
    assertThat(gecached).isPresent();
    assertThat(gecached.get()).hasSize(1);
    assertThat(gecached.get().get(0).naam()).isEqualTo("Mobiliteit");
  }

  @Test
  void geeftLeegOptionalAlsNogNietGegroepeerd() {
    var resultaat = service.geefKernbezwaren("onbekend");

    assertThat(resultaat).isEmpty();
  }
}
