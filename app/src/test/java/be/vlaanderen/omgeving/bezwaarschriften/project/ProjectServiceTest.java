package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

  private ProjectService service;

  @BeforeEach
  void setUp() {
    service = new ProjectService(projectPoort, extractieTaakRepository);
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
}
