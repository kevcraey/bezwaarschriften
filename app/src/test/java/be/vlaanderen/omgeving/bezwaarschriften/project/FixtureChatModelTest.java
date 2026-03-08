package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FixtureChatModelTest {

  @TempDir
  Path tempDir;

  private FixtureChatModel chatModel;

  @BeforeEach
  void setUp() throws IOException {
    var projectDir = tempDir.resolve("testproject");
    var documentenDir = projectDir.resolve("documenten");
    var bezwarenDir = projectDir.resolve("bezwaren-orig");
    Files.createDirectories(documentenDir);
    Files.createDirectories(bezwarenDir);

    Files.writeString(documentenDir.resolve("Bezwaar_01.txt"),
        "Het verkeer zal onhoudbaar toenemen in de Gaverstraat.");
    Files.writeString(bezwarenDir.resolve("Bezwaar_01.json"),
        "{\"passages\":[{\"id\":1,\"tekst\":\"verkeer\"}],\"bezwaren\":[],"
            + "\"metadata\":{\"aantalWoorden\":8,\"documentSamenvatting\":\"test\"}}");

    chatModel = new FixtureChatModel(tempDir.toString());
  }

  @Test
  void retourneertFixtureVoorBekendDocument() {
    var prompt = """
        ---
        Het verkeer zal onhoudbaar toenemen in de Gaverstraat.
        ---
        """;
    var response = chatModel.chat("system prompt", prompt);

    assertTrue(response.contains("\"passages\""));
    assertTrue(response.contains("\"aantalWoorden\":8"));
  }

  @Test
  void gooitExceptieVoorOnbekendDocument() {
    var prompt = """
        ---
        Dit document bestaat niet in de fixtures.
        ---
        """;
    assertThrows(IllegalArgumentException.class,
        () -> chatModel.chat("system prompt", prompt));
  }
}
