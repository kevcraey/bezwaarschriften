package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.Brondocument;
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
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
    verwerker = new MockExtractieVerwerker(ingestiePoort, "input", "");
  }

  @Test
  void gooitFoutZonderFixture() {
    String bestandsnaam = "bezwaar-001.txt";
    mockBestand(bestandsnaam, "dit is een test tekst");

    assertThatThrownBy(() -> verwerker.verwerk(PROJECT, bestandsnaam, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Geen fixture gevonden");
  }

  @Test
  void zoektFixtureViaProjectNaamEnBezwarenMap(@TempDir Path tempDir) throws IOException {
    var projectNaam = "testproject";
    var bestandsnaam = "Bezwaar_01.txt";
    var fixtureJson = "{\"passages\":[{\"id\":1,\"tekst\":\"passage tekst\"}],"
        + "\"bezwaren\":[{\"passageId\":1,\"samenvatting\":\"samenvatting\","
        + "\"categorie\":\"mobiliteit\"}],"
        + "\"metadata\":{\"aantalWoorden\":5,\"documentSamenvatting\":\"test samenvatting\"}}";

    var bezwarenDir = tempDir.resolve(projectNaam).resolve("bezwaren");
    Files.createDirectories(bezwarenDir);
    Files.writeString(bezwarenDir.resolve("Bezwaar_01.json"), fixtureJson);

    var verwerkerMetFixture = new MockExtractieVerwerker(
        ingestiePoort, "input", tempDir.toString());
    when(ingestiePoort.leesBestand(Path.of("input", projectNaam, "bezwaren-text", bestandsnaam)))
        .thenReturn(new Brondocument("dit is een test tekst", bestandsnaam,
            "input/" + projectNaam + "/bezwaren-text/" + bestandsnaam, Instant.now()));

    var resultaat = verwerkerMetFixture.verwerk(projectNaam, bestandsnaam, 0);

    assertThat(resultaat.aantalBezwaren()).isEqualTo(1);
    assertThat(resultaat.bezwaren()).hasSize(1);
    assertThat(resultaat.bezwaren().get(0).samenvatting()).isEqualTo("samenvatting");
  }

  private void mockBestand(String bestandsnaam, String tekst) {
    when(ingestiePoort.leesBestand(
        Path.of("input", PROJECT, "bezwaren-text", bestandsnaam)))
        .thenReturn(new Brondocument(tekst, bestandsnaam,
            "input/" + PROJECT + "/bezwaren-text/" + bestandsnaam, Instant.now()));
  }
}
