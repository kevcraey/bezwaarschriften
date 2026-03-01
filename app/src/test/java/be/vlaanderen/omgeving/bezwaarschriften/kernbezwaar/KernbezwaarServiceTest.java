package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.project.ProjectService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KernbezwaarServiceTest {

  @Mock
  private KernbezwaarPoort kernbezwaarPoort;

  @Mock
  private ProjectService projectService;

  @Mock
  private KernbezwaarAntwoordRepository antwoordRepository;

  private KernbezwaarService service;

  @BeforeEach
  void setUp() {
    service = new KernbezwaarService(kernbezwaarPoort, projectService, antwoordRepository);
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

  @Test
  void verrijktKernbezwarenMetAntwoorden() {
    var kern = new Kernbezwaar(5L, "samenvatting", List.of(), null);
    var thema = new Thema("Geluid", List.of(kern));
    when(projectService.geefBezwaartekstenVoorGroepering("windmolens"))
        .thenReturn(List.of(new KernbezwaarPoort.BezwaarInvoer(1L, "b.txt", "t")));
    when(kernbezwaarPoort.groepeer(anyList())).thenReturn(List.of(thema));
    service.groepeer("windmolens");

    var antwoord = new KernbezwaarAntwoordEntiteit();
    antwoord.setKernbezwaarId(5L);
    antwoord.setInhoud("<p>Weerwoord</p>");
    when(antwoordRepository.findByKernbezwaarIdIn(List.of(5L)))
        .thenReturn(List.of(antwoord));

    var resultaat = service.geefKernbezwaren("windmolens");

    assertThat(resultaat).isPresent();
    assertThat(resultaat.get().get(0).kernbezwaren().get(0).antwoord())
        .isEqualTo("<p>Weerwoord</p>");
  }

  @Test
  void slaatAntwoordOp() {
    var entiteit = new KernbezwaarAntwoordEntiteit();
    when(antwoordRepository.save(any())).thenReturn(entiteit);

    service.slaAntwoordOp(42L, "<p>Het weerwoord</p>");

    var captor = ArgumentCaptor.forClass(KernbezwaarAntwoordEntiteit.class);
    verify(antwoordRepository).save(captor.capture());
    assertThat(captor.getValue().getKernbezwaarId()).isEqualTo(42L);
    assertThat(captor.getValue().getInhoud()).isEqualTo("<p>Het weerwoord</p>");
    assertThat(captor.getValue().getBijgewerktOp()).isNotNull();
  }
}
