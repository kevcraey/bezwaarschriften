package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

  private ProjectService service;

  @BeforeEach
  void setUp() {
    service = new ProjectService(projectPoort, extractieTaakRepository,
        kernbezwaarService, consolidatieTaakRepository);
  }

  @Test
  void geeftProjectenTerug() {
    when(projectPoort.geefProjecten()).thenReturn(List.of("windmolens", "zonnepanelen"));

    var projecten = service.geefProjecten();

    assertThat(projecten).containsExactly("windmolens", "zonnepanelen");
  }

  @Test
  void geeftBezwarenMetInitieleStatussen() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.txt", "bijlage.pdf"));
    when(extractieTaakRepository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.empty());

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren).hasSize(2);
    assertThat(bezwaren).anySatisfy(b -> {
      assertThat(b.bestandsnaam()).isEqualTo("bezwaar-001.txt");
      assertThat(b.status()).isEqualTo(BezwaarBestandStatus.TODO);
    });
    assertThat(bezwaren).anySatisfy(b -> {
      assertThat(b.bestandsnaam()).isEqualTo("bijlage.pdf");
      assertThat(b.status()).isEqualTo(BezwaarBestandStatus.NIET_ONDERSTEUND);
    });
  }

  @Test
  void leidtStatusAfUitExtractieTaak() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.txt"));
    var taak = maakTaak(ExtractieTaakStatus.KLAAR, 150, 3);
    when(extractieTaakRepository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(taak));

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren).hasSize(1);
    assertThat(bezwaren.get(0).status()).isEqualTo(BezwaarBestandStatus.EXTRACTIE_KLAAR);
    assertThat(bezwaren.get(0).aantalWoorden()).isEqualTo(150);
    assertThat(bezwaren.get(0).aantalBezwaren()).isEqualTo(3);
  }

  @Test
  void leidtWachtendStatusAfUitExtractieTaak() {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.txt"));
    var taak = maakTaak(ExtractieTaakStatus.WACHTEND, null, null);
    when(extractieTaakRepository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(taak));

    var bezwaren = service.geefBezwaren("windmolens");

    assertThat(bezwaren).hasSize(1);
    assertThat(bezwaren.get(0).status()).isEqualTo(BezwaarBestandStatus.WACHTEND);
    assertThat(bezwaren.get(0).aantalWoorden()).isNull();
    assertThat(bezwaren.get(0).aantalBezwaren()).isNull();
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

  @Test
  void maakProjectAan_delegeertNaarPoort() {
    service.maakProjectAan("nieuw-project");

    verify(projectPoort).maakProjectAan("nieuw-project");
  }

  @Test
  void verwijderBezwaar_ruimtKernbezwaarDataOpVoorVerwijdering() {
    when(projectPoort.verwijderBestand("windmolens", "bezwaar-001.txt")).thenReturn(true);

    service.verwijderBezwaar("windmolens", "bezwaar-001.txt");

    var inOrder = inOrder(kernbezwaarService, extractieTaakRepository, projectPoort);
    inOrder.verify(kernbezwaarService).ruimOpNaDocumentVerwijdering("windmolens", "bezwaar-001.txt");
    inOrder.verify(extractieTaakRepository).deleteByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt");
    inOrder.verify(projectPoort).verwijderBestand("windmolens", "bezwaar-001.txt");
  }

  @Test
  void verwijderProject_verwijdertAlleDataEnDelegeerNaarPoort() {
    when(projectPoort.verwijderProject("oud-project")).thenReturn(true);

    boolean result = service.verwijderProject("oud-project");

    assertThat(result).isTrue();
    var inOrder = inOrder(kernbezwaarService, consolidatieTaakRepository,
        extractieTaakRepository, projectPoort);
    inOrder.verify(kernbezwaarService).ruimAllesOpVoorProject("oud-project");
    inOrder.verify(consolidatieTaakRepository).deleteByProjectNaam("oud-project");
    inOrder.verify(extractieTaakRepository).deleteByProjectNaam("oud-project");
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
}
