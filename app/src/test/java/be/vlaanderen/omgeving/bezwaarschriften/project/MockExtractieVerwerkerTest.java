package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.Brondocument;
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
class MockExtractieVerwerkerTest {

  private static final String PROJECT = "windmolens";
  private static final List<String> TXT_BESTANDEN =
      List.of("bezwaar-001.txt", "bezwaar-002.txt", "bezwaar-003.txt", "bezwaar-004.txt");

  @Mock
  private ProjectPoort projectPoort;

  @Mock
  private IngestiePoort ingestiePoort;

  private MockExtractieVerwerker verwerker;

  @BeforeEach
  void setUp() {
    verwerker = new MockExtractieVerwerker(
        projectPoort, ingestiePoort, "input", 0, 0);
  }

  @Test
  void eersteBestandGeeft3Bezwaren() {
    String bestandsnaam = "bezwaar-001.txt";
    mockBestand(bestandsnaam, "dit is een test tekst");

    var resultaat = verwerker.verwerk(PROJECT, bestandsnaam, 0);

    assertThat(resultaat.aantalBezwaren()).isEqualTo(3);
    assertThat(resultaat.aantalWoorden()).isEqualTo(5);
  }

  @Test
  void derdeBestandGeeft5Bezwaren() {
    String bestandsnaam = "bezwaar-003.txt";
    mockBestand(bestandsnaam, "tekst van het derde bezwaar");

    var resultaat = verwerker.verwerk(PROJECT, bestandsnaam, 0);

    assertThat(resultaat.aantalBezwaren()).isEqualTo(5);
    assertThat(resultaat.aantalWoorden()).isEqualTo(5);
  }

  @Test
  void tweedeBestandFaaltBijEerstePoging() {
    String bestandsnaam = "bezwaar-002.txt";
    mockBestand(bestandsnaam, "tekst");

    assertThatThrownBy(() -> verwerker.verwerk(PROJECT, bestandsnaam, 0))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("bezwaar-002.txt");
  }

  @Test
  void tweedeBestandFaaltBijTweedePoging() {
    String bestandsnaam = "bezwaar-002.txt";
    mockBestand(bestandsnaam, "tekst");

    assertThatThrownBy(() -> verwerker.verwerk(PROJECT, bestandsnaam, 1))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("bezwaar-002.txt");
  }

  @Test
  void tweedeBestandSlaagdBijDerdePoging() {
    String bestandsnaam = "bezwaar-002.txt";
    mockBestand(bestandsnaam, "derde poging tekst hier nu");

    var resultaat = verwerker.verwerk(PROJECT, bestandsnaam, 2);

    assertThat(resultaat.aantalBezwaren()).isEqualTo(4);
    assertThat(resultaat.aantalWoorden()).isEqualTo(5);
  }

  @Test
  void overigeBestandenGeven2Bezwaren() {
    String bestandsnaam = "bezwaar-004.txt";
    mockBestand(bestandsnaam, "een overig bestand");

    var resultaat = verwerker.verwerk(PROJECT, bestandsnaam, 0);

    assertThat(resultaat.aantalBezwaren()).isEqualTo(2);
    assertThat(resultaat.aantalWoorden()).isEqualTo(3);
  }

  private void mockBestand(String bestandsnaam, String tekst) {
    when(projectPoort.geefBestandsnamen(PROJECT)).thenReturn(TXT_BESTANDEN);
    when(ingestiePoort.leesBestand(
        Path.of("input", PROJECT, "bezwaren", bestandsnaam)))
        .thenReturn(new Brondocument(tekst, bestandsnaam,
            "input/" + PROJECT + "/bezwaren/" + bestandsnaam, Instant.now()));
  }
}
