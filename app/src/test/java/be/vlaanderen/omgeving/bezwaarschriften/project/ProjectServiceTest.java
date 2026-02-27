package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.Brondocument;
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.FileIngestionException;
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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
  private IngestiePoort ingestiePoort;

  private ProjectService service;

  @BeforeEach
  void setUp() {
    service = new ProjectService(projectPoort, ingestiePoort, "input");
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
  void verwerkingZetStatusOpExtractieKlaarBijSucces() throws Exception {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-001.txt"));
    when(ingestiePoort.leesBestand(Path.of("input", "windmolens", "bezwaren", "bezwaar-001.txt")))
        .thenReturn(new Brondocument("dit is een test tekst", "bezwaar-001.txt",
            "input/windmolens/bezwaren/bezwaar-001.txt", Instant.now()));

    var resultaat = service.verwerk("windmolens");

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).status()).isEqualTo(BezwaarBestandStatus.EXTRACTIE_KLAAR);
    assertThat(resultaat.get(0).aantalWoorden()).isEqualTo(5);
  }

  @Test
  void verwerkingGaatDoorBijFoutOpEnkelBestand() throws Exception {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar-goed.txt", "bezwaar-kapot.txt"));
    when(ingestiePoort.leesBestand(Path.of("input", "windmolens", "bezwaren", "bezwaar-goed.txt")))
        .thenReturn(new Brondocument("inhoud", "bezwaar-goed.txt",
            "input/windmolens/bezwaren/bezwaar-goed.txt", Instant.now()));
    when(ingestiePoort.leesBestand(Path.of("input", "windmolens", "bezwaren", "bezwaar-kapot.txt")))
        .thenThrow(new FileIngestionException("Kan niet lezen"));

    var resultaat = service.verwerk("windmolens");

    assertThat(resultaat).hasSize(2);
    assertThat(resultaat).anySatisfy(b -> {
      assertThat(b.bestandsnaam()).isEqualTo("bezwaar-goed.txt");
      assertThat(b.status()).isEqualTo(BezwaarBestandStatus.EXTRACTIE_KLAAR);
    });
    assertThat(resultaat).anySatisfy(b -> {
      assertThat(b.bestandsnaam()).isEqualTo("bezwaar-kapot.txt");
      assertThat(b.status()).isEqualTo(BezwaarBestandStatus.FOUT);
    });
  }

  @Test
  void slaatNietOndersteundeBestandenOverBijVerwerking() throws Exception {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar.txt", "bijlage.pdf"));
    when(ingestiePoort.leesBestand(Path.of("input", "windmolens", "bezwaren", "bezwaar.txt")))
        .thenReturn(new Brondocument("inhoud", "bezwaar.txt",
            "input/windmolens/bezwaren/bezwaar.txt", Instant.now()));

    var resultaat = service.verwerk("windmolens");

    verify(ingestiePoort, never())
        .leesBestand(Path.of("input", "windmolens", "bezwaren", "bijlage.pdf"));
    assertThat(resultaat).anySatisfy(b -> {
      assertThat(b.bestandsnaam()).isEqualTo("bijlage.pdf");
      assertThat(b.status()).isEqualTo(BezwaarBestandStatus.NIET_ONDERSTEUND);
    });
  }

  @Test
  void herverwerktNietWatAlExtractieKlaarIs() throws Exception {
    when(projectPoort.geefBestandsnamen("windmolens"))
        .thenReturn(List.of("bezwaar.txt"));
    when(ingestiePoort.leesBestand(Path.of("input", "windmolens", "bezwaren", "bezwaar.txt")))
        .thenReturn(new Brondocument("inhoud", "bezwaar.txt",
            "input/windmolens/bezwaren/bezwaar.txt", Instant.now()));

    service.verwerk("windmolens"); // eerste verwerking
    service.verwerk("windmolens"); // tweede verwerking

    // IngestiePoort slechts één keer aangeroepen
    verify(ingestiePoort, times(1))
        .leesBestand(Path.of("input", "windmolens", "bezwaren", "bezwaar.txt"));
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
}
