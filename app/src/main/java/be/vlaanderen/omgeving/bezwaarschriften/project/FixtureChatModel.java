package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fixture-gebaseerde implementatie van {@link ChatModelPoort} die vooraf gegenereerde
 * LLM-antwoorden teruggeeft op basis van documenttekst-matching.
 *
 * <p>Laadt bij initialisatie alle .txt/.json paren uit de testdata-directory.
 * Matcht inkomende prompts door de documenttekst (tussen --- markers) te vergelijken
 * met de geladen .txt bestanden.
 */
public class FixtureChatModel implements ChatModelPoort {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Map<String, String> fixtures;

  /**
   * Laadt fixtures uit de opgegeven testdata-directory.
   *
   * @param testdataPad pad naar de testdata project-directory
   */
  public FixtureChatModel(String testdataPad) {
    this.fixtures = laadFixtures(Path.of(testdataPad));
    LOGGER.info("FixtureChatModel geladen met {} fixtures", fixtures.size());
  }

  @Override
  public String chat(String systemPrompt, String userPrompt) {
    var documentTekst = extractDocumentTekst(userPrompt).strip();
    var fixtureJson = fixtures.get(documentTekst);
    if (fixtureJson == null) {
      throw new IllegalArgumentException(
          "Geen fixture gevonden voor document (eerste 80 chars): '"
              + documentTekst.substring(0, Math.min(80, documentTekst.length())) + "...'");
    }
    return fixtureJson;
  }

  private Map<String, String> laadFixtures(Path basePad) {
    var result = new HashMap<String, String>();
    if (!Files.isDirectory(basePad)) {
      LOGGER.warn("Testdata basismap niet gevonden: {}", basePad);
      return result;
    }
    try (var projectStream = Files.list(basePad)) {
      projectStream.filter(Files::isDirectory).forEach(projectDir -> {
        var documentenDir = projectDir.resolve("documenten");
        if (!Files.isDirectory(documentenDir)) {
          return;
        }
        try (var stream = Files.list(documentenDir)) {
          stream.filter(p -> p.toString().endsWith(".txt")).forEach(txtPath -> {
            var naam = txtPath.getFileName().toString();
            var naamZonderExtensie = naam.substring(0, naam.lastIndexOf('.'));
            var jsonPath = projectDir.resolve("bezwaren").resolve(naamZonderExtensie + ".json");
            if (Files.exists(jsonPath)) {
              try {
                var txtContent = Files.readString(txtPath, StandardCharsets.UTF_8).strip();
                var jsonContent = Files.readString(jsonPath, StandardCharsets.UTF_8).strip();
                result.put(txtContent, jsonContent);
              } catch (IOException e) {
                LOGGER.error("Fout bij laden fixture: {}", txtPath, e);
              }
            }
          });
        } catch (IOException e) {
          throw new RuntimeException("Kan documenten directory niet lezen: " + documentenDir, e);
        }
      });
    } catch (IOException e) {
      throw new RuntimeException("Kan testdata basismap niet lezen: " + basePad, e);
    }
    return result;
  }

  private String extractDocumentTekst(String userPrompt) {
    int start = userPrompt.indexOf("---");
    int end = userPrompt.lastIndexOf("---");
    if (start == -1 || end == -1 || start == end) {
      throw new IllegalArgumentException("User prompt bevat geen --- markers");
    }
    return userPrompt.substring(start + 3, end);
  }
}
