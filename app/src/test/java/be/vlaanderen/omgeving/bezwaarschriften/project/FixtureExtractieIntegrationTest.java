package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.Brondocument;
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Integratietest die verifieert dat AiExtractieVerwerker + FixtureChatModel
 * samen correcte resultaten opleveren voor de synthetische testdata.
 *
 * <p>Draait ZONDER Spring context — test enkel de verwerker + chatmodel samenwerking.
 * Loopt over alle testdata-projecten die zowel documenten/ als bezwaren/ bevatten.
 */
class FixtureExtractieIntegrationTest {

  @TestFactory
  Stream<DynamicTest> verwerktAlleFixtureBestanden() throws IOException {
    var testdataRoot = resolveTestdataRoot();
    var projecten = vindCompleteProjecten(testdataRoot);
    assumeTrue(!projecten.isEmpty(),
        "Geen testdata-projecten met documenten/ en bezwaren/ gevonden");

    return projecten.stream().flatMap(projectDir -> {
      var projectNaam = projectDir.getFileName().toString();
      var chatModel = new FixtureChatModel(testdataRoot.toString());
      var ingestiePoort = new TestdataIngestiePoort(projectDir);
      var verwerker = new AiExtractieVerwerker(
          chatModel, ingestiePoort, projectDir.toString());

      try (var stream = Files.list(projectDir.resolve("documenten"))) {
        return stream
            .filter(p -> p.toString().endsWith(".txt"))
            .map(txtPath -> {
              var bestandsnaam = txtPath.getFileName().toString();
              return DynamicTest.dynamicTest(
                  projectNaam + "/" + bestandsnaam,
                  () -> verifieerExtractie(verwerker, bestandsnaam));
            })
            .toList()
            .stream();
      } catch (IOException e) {
        throw new RuntimeException("Kan documenten niet lezen voor " + projectNaam, e);
      }
    });
  }

  private void verifieerExtractie(AiExtractieVerwerker verwerker, String bestandsnaam) {
    var resultaat = verwerker.verwerk("", bestandsnaam, 0);

    assertNotNull(resultaat, "Null resultaat voor " + bestandsnaam);
    assertTrue(resultaat.aantalWoorden() >= 0,
        "Negatief woordaantal voor " + bestandsnaam);
    assertNotNull(resultaat.passages(), "Null passages voor " + bestandsnaam);
    assertNotNull(resultaat.bezwaren(), "Null bezwaren-lijst voor " + bestandsnaam);
    assertNotNull(resultaat.documentSamenvatting(),
        "Geen samenvatting voor " + bestandsnaam);

    var passageIds = resultaat.passages().stream().map(Passage::id).toList();
    for (var bezwaar : resultaat.bezwaren()) {
      assertTrue(passageIds.contains(bezwaar.passageId()),
          "Bezwaar verwijst naar onbestaande passage " + bezwaar.passageId()
              + " in " + bestandsnaam);
    }
  }

  private static List<Path> vindCompleteProjecten(Path testdataRoot) throws IOException {
    try (var stream = Files.list(testdataRoot)) {
      return stream
          .filter(Files::isDirectory)
          .filter(dir -> Files.isDirectory(dir.resolve("documenten"))
              && Files.isDirectory(dir.resolve("bezwaren")))
          .toList();
    }
  }

  private static Path resolveTestdataRoot() {
    var pad = Path.of("testdata");
    if (Files.isDirectory(pad)) {
      return pad;
    }
    pad = Path.of("../testdata");
    if (Files.isDirectory(pad)) {
      return pad;
    }
    throw new IllegalStateException(
        "testdata directory niet gevonden (cwd=" + Path.of("").toAbsolutePath() + ")");
  }

  /**
   * IngestiePoort die bronbestanden leest uit de documenten/ map van het project.
   */
  private static class TestdataIngestiePoort implements IngestiePoort {

    private final Path projectPad;

    TestdataIngestiePoort(Path projectPad) {
      this.projectPad = projectPad;
    }

    @Override
    public Brondocument leesBestand(Path pad) {
      var documentPad = projectPad.resolve("documenten").resolve(pad.getFileName());
      try {
        var tekst = Files.readString(documentPad, StandardCharsets.UTF_8);
        return new Brondocument(
            tekst, pad.getFileName().toString(), documentPad.toString(), Instant.now());
      } catch (IOException e) {
        throw new RuntimeException("Kan testbestand niet lezen: " + documentPad, e);
      }
    }
  }
}
