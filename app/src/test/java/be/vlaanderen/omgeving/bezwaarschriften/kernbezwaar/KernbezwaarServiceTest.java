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

  @Mock
  private ThemaRepository themaRepository;

  @Mock
  private KernbezwaarRepository kernbezwaarRepository;

  @Mock
  private KernbezwaarReferentieRepository referentieRepository;

  private KernbezwaarService service;

  @BeforeEach
  void setUp() {
    service = new KernbezwaarService(kernbezwaarPoort, projectService,
        antwoordRepository, themaRepository, kernbezwaarRepository, referentieRepository);
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
    when(themaRepository.save(any())).thenAnswer(inv -> {
      var e = (ThemaEntiteit) inv.getArgument(0);
      e.setId(100L);
      return e;
    });
    when(kernbezwaarRepository.save(any())).thenAnswer(inv -> {
      var e = (KernbezwaarEntiteit) inv.getArgument(0);
      e.setId(200L);
      return e;
    });
    when(referentieRepository.save(any())).thenAnswer(inv -> {
      var e = (KernbezwaarReferentieEntiteit) inv.getArgument(0);
      e.setId(300L);
      return e;
    });

    var resultaat = service.groepeer("windmolens");

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).naam()).isEqualTo("Geluid");
    assertThat(resultaat.get(0).kernbezwaren().get(0).id()).isEqualTo(200L);
    verify(themaRepository).deleteByProjectNaam("windmolens");
    verify(themaRepository).save(any());
    verify(kernbezwaarRepository).save(any());
    verify(referentieRepository).save(any());
  }

  @Test
  void laadtKernbezwarenUitDatabase() {
    var themaEntiteit = new ThemaEntiteit();
    themaEntiteit.setId(10L);
    themaEntiteit.setProjectNaam("windmolens");
    themaEntiteit.setNaam("Mobiliteit");
    when(themaRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(themaEntiteit));

    var kernEntiteit = new KernbezwaarEntiteit();
    kernEntiteit.setId(20L);
    kernEntiteit.setThemaId(10L);
    kernEntiteit.setSamenvatting("Verkeershinder");
    when(kernbezwaarRepository.findByThemaIdIn(List.of(10L)))
        .thenReturn(List.of(kernEntiteit));

    var refEntiteit = new KernbezwaarReferentieEntiteit();
    refEntiteit.setId(30L);
    refEntiteit.setKernbezwaarId(20L);
    refEntiteit.setBezwaarId(1L);
    refEntiteit.setBestandsnaam("b1.txt");
    refEntiteit.setPassage("passage");
    when(referentieRepository.findByKernbezwaarIdIn(List.of(20L)))
        .thenReturn(List.of(refEntiteit));

    when(antwoordRepository.findByKernbezwaarIdIn(List.of(20L)))
        .thenReturn(List.of());

    var resultaat = service.geefKernbezwaren("windmolens");

    assertThat(resultaat).isPresent();
    assertThat(resultaat.get()).hasSize(1);
    assertThat(resultaat.get().get(0).naam()).isEqualTo("Mobiliteit");
    assertThat(resultaat.get().get(0).kernbezwaren().get(0).samenvatting())
        .isEqualTo("Verkeershinder");
    assertThat(resultaat.get().get(0).kernbezwaren().get(0).individueleBezwaren())
        .hasSize(1);
  }

  @Test
  void geeftLeegOptionalAlsNogNietGegroepeerd() {
    when(themaRepository.findByProjectNaam("onbekend")).thenReturn(List.of());

    var resultaat = service.geefKernbezwaren("onbekend");

    assertThat(resultaat).isEmpty();
  }

  @Test
  void verrijktKernbezwarenMetAntwoorden() {
    var themaEntiteit = new ThemaEntiteit();
    themaEntiteit.setId(10L);
    themaEntiteit.setProjectNaam("windmolens");
    themaEntiteit.setNaam("Geluid");
    when(themaRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(themaEntiteit));

    var kernEntiteit = new KernbezwaarEntiteit();
    kernEntiteit.setId(5L);
    kernEntiteit.setThemaId(10L);
    kernEntiteit.setSamenvatting("samenvatting");
    when(kernbezwaarRepository.findByThemaIdIn(List.of(10L)))
        .thenReturn(List.of(kernEntiteit));

    when(referentieRepository.findByKernbezwaarIdIn(List.of(5L)))
        .thenReturn(List.of());

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
