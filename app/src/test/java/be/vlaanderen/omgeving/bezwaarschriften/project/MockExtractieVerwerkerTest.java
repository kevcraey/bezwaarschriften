package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.Brondocument;
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockExtractieVerwerkerTest {

  private static final String PROJECT = "windmolens";

  @Mock
  private IngestiePoort ingestiePoort;

  private MockExtractieVerwerker verwerker;

  @BeforeEach
  void setUp() {
    verwerker = new MockExtractieVerwerker(ingestiePoort, "input", 0, 0);
  }

  @Test
  void bestandZonderTweeFaaltNiet() {
    String bestandsnaam = "bezwaar-001.txt";
    mockBestand(bestandsnaam, "dit is een test tekst");

    var resultaat = verwerker.verwerk(PROJECT, bestandsnaam, 0);

    assertThat(resultaat.aantalWoorden()).isEqualTo(5);
    assertThat(resultaat.aantalBezwaren()).isBetween(2, 5);
  }

  @Test
  void bestandMetTweeFaaltBijAanroep1En2SlaagdBij3() {
    String bestandsnaam = "bezwaar2.txt";
    mockBestand(bestandsnaam, "derde poging tekst hier nu");

    // aanroep 1: faalt
    assertThatThrownBy(() -> verwerker.verwerk(PROJECT, bestandsnaam, 0))
        .isInstanceOf(RuntimeException.class);

    // aanroep 2: faalt
    assertThatThrownBy(() -> verwerker.verwerk(PROJECT, bestandsnaam, 1))
        .isInstanceOf(RuntimeException.class);

    // aanroep 3: slaagt (veelvoud van 3)
    var resultaat = verwerker.verwerk(PROJECT, bestandsnaam, 2);
    assertThat(resultaat.aantalBezwaren()).isEqualTo(4);
  }

  @Test
  void bestandMetTweeFaaltOpnieuwNaSucces() {
    String bestandsnaam = "bezwaar2.txt";
    mockBestand(bestandsnaam, "tekst voor cyclus test");

    // aanroep 1-3: faal, faal, slaag
    assertThatThrownBy(() -> verwerker.verwerk(PROJECT, bestandsnaam, 0));
    assertThatThrownBy(() -> verwerker.verwerk(PROJECT, bestandsnaam, 0));
    verwerker.verwerk(PROJECT, bestandsnaam, 0);

    // aanroep 4-6: faal, faal, slaag (cyclus herhaalt)
    assertThatThrownBy(() -> verwerker.verwerk(PROJECT, bestandsnaam, 0))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> verwerker.verwerk(PROJECT, bestandsnaam, 0))
        .isInstanceOf(RuntimeException.class);
    var resultaat = verwerker.verwerk(PROJECT, bestandsnaam, 0);
    assertThat(resultaat.aantalBezwaren()).isEqualTo(4);
  }

  @Test
  void woordenTellingWerktCorrect() {
    String bestandsnaam = "bezwaar-003.txt";
    mockBestand(bestandsnaam, "een twee drie");

    var resultaat = verwerker.verwerk(PROJECT, bestandsnaam, 0);

    assertThat(resultaat.aantalWoorden()).isEqualTo(3);
  }

  private void mockBestand(String bestandsnaam, String tekst) {
    when(ingestiePoort.leesBestand(
        Path.of("input", PROJECT, "bezwaren", bestandsnaam)))
        .thenReturn(new Brondocument(tekst, bestandsnaam,
            "input/" + PROJECT + "/bezwaren/" + bestandsnaam, Instant.now()));
  }
}
