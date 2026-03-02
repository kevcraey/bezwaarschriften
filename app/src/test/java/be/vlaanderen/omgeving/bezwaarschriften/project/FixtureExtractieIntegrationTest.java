package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.Brondocument;
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integratietest die verifieert dat AiExtractieVerwerker + FixtureChatModel
 * samen correcte resultaten opleveren voor de synthetische testdata.
 *
 * <p>Draait ZONDER Spring context — test enkel de verwerker + chatmodel samenwerking.
 */
class FixtureExtractieIntegrationTest {

  private static Path testdataPad;
  private static Path bezwarenDir;
  private static AiExtractieVerwerker verwerker;
  private static ObjectMapper objectMapper;

  @BeforeAll
  static void setUp() {
    testdataPad = resolveTestdataPad();
    bezwarenDir = testdataPad.resolve("bezwaren");
    var chatModel = new FixtureChatModel(testdataPad.toString());
    var ingestiePoort = new TestdataIngestiePoort();
    verwerker = new AiExtractieVerwerker(chatModel, ingestiePoort, testdataPad.toString());
    objectMapper = new ObjectMapper();
  }

  @Test
  void verwerktAlleFixtureBestanden() throws IOException {
    try (var stream = Files.list(bezwarenDir)) {
      var txtBestanden = stream
          .filter(p -> p.toString().endsWith(".txt"))
          .toList();

      assertFalse(txtBestanden.isEmpty(), "Geen .txt bestanden gevonden in " + bezwarenDir);

      for (var txtPath : txtBestanden) {
        var bestandsnaam = txtPath.getFileName().toString();
        var resultaat = verwerker.verwerk("", bestandsnaam, 0);

        assertNotNull(resultaat, "Null resultaat voor " + bestandsnaam);
        assertTrue(resultaat.aantalWoorden() > 0,
            "Geen woorden voor " + bestandsnaam);
        assertTrue(resultaat.aantalBezwaren() > 0,
            "Geen bezwaren voor " + bestandsnaam);
        assertFalse(resultaat.passages().isEmpty(),
            "Geen passages voor " + bestandsnaam);
        assertFalse(resultaat.bezwaren().isEmpty(),
            "Geen bezwaren-lijst voor " + bestandsnaam);
        assertNotNull(resultaat.documentSamenvatting(),
            "Geen samenvatting voor " + bestandsnaam);

        var passageIds = resultaat.passages().stream().map(Passage::id).toList();
        for (var bezwaar : resultaat.bezwaren()) {
          assertTrue(passageIds.contains(bezwaar.passageId()),
              "Bezwaar verwijst naar onbestaande passage " + bezwaar.passageId()
                  + " in " + bestandsnaam);
        }
      }
    }
  }

  @Test
  void manifestKloptMetFixtures() throws IOException {
    var manifestPath = testdataPad.resolve("manifest.json");
    assertTrue(Files.exists(manifestPath), "manifest.json ontbreekt");

    var manifest = objectMapper.readTree(Files.readString(manifestPath));
    var bestanden = manifest.get("bestanden");
    assertNotNull(bestanden);
    assertTrue(bestanden.isArray());

    for (var entry : bestanden) {
      var txtPad = bezwarenDir.resolve(
          Path.of(entry.get("txtBestand").asText()).getFileName());
      var jsonPad = bezwarenDir.resolve(
          Path.of(entry.get("fixtureBestand").asText()).getFileName());
      assertTrue(Files.exists(txtPad), "Ontbrekend txt: " + txtPad);
      assertTrue(Files.exists(jsonPad), "Ontbrekend json: " + jsonPad);
    }
  }

  private static Path resolveTestdataPad() {
    var pad = Path.of("testdata/gaverbeek-stadion");
    if (Files.isDirectory(pad)) {
      return pad;
    }
    pad = Path.of("../testdata/gaverbeek-stadion");
    if (Files.isDirectory(pad)) {
      return pad;
    }
    throw new IllegalStateException(
        "testdata directory niet gevonden (cwd=" + Path.of("").toAbsolutePath() + ")");
  }

  /**
   * Simpele IngestiePoort die .txt bestanden leest uit de bezwaren directory.
   */
  private static class TestdataIngestiePoort implements IngestiePoort {

    @Override
    public Brondocument leesBestand(Path pad) {
      try {
        var tekst = Files.readString(pad, StandardCharsets.UTF_8);
        return new Brondocument(
            tekst, pad.getFileName().toString(), pad.toString(), Instant.now());
      } catch (IOException e) {
        throw new RuntimeException("Kan testbestand niet lezen: " + pad, e);
      }
    }
  }
}
