package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
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
class AiExtractieVerwerkerTest {

  private static final String FIXTURE_JSON = """
      {
        "passages": [
          { "id": 1, "tekst": "Het verkeer zal onhoudbaar toenemen." }
        ],
        "bezwaren": [
          { "passageId": 1, "samenvatting": "Verkeerstoename", "categorie": "mobiliteit" }
        ],
        "metadata": {
          "aantalWoorden": 7,
          "documentSamenvatting": "Bezwaar over verkeer"
        }
      }
      """;

  @Mock
  private ChatModelPoort chatModel;

  @Mock
  private IngestiePoort ingestiePoort;

  private AiExtractieVerwerker verwerker;

  @BeforeEach
  void setUp() {
    verwerker = new AiExtractieVerwerker(chatModel, ingestiePoort, "input");
  }

  @Test
  void verwerktDocumentEnParstLlmResponse() {
    var document = new Brondocument(
        "Het verkeer zal onhoudbaar toenemen.", "bezwaar.txt", "pad", Instant.now());
    when(ingestiePoort.leesBestand(any(Path.class))).thenReturn(document);
    when(chatModel.chat(anyString(), anyString())).thenReturn(FIXTURE_JSON);

    var resultaat = verwerker.verwerk("project", "bezwaar.txt", 0);

    assertEquals(7, resultaat.aantalWoorden());
    assertEquals(1, resultaat.aantalBezwaren());
    assertEquals(1, resultaat.passages().size());
    assertEquals("Het verkeer zal onhoudbaar toenemen.", resultaat.passages().get(0).tekst());
    assertEquals(1, resultaat.bezwaren().size());
    assertEquals("mobiliteit", resultaat.bezwaren().get(0).categorie());
    assertEquals("Bezwaar over verkeer", resultaat.documentSamenvatting());
  }

  @Test
  void stuurtDocumentTekstInUserPrompt() {
    var document = new Brondocument("Mijn bezwaar tekst.", "b.txt", "p", Instant.now());
    when(ingestiePoort.leesBestand(any(Path.class))).thenReturn(document);
    when(chatModel.chat(anyString(), anyString())).thenReturn(FIXTURE_JSON);

    verwerker.verwerk("project", "b.txt", 0);

    verify(chatModel).chat(anyString(), contains("Mijn bezwaar tekst."));
  }

  @Test
  void laadtSystemPromptVanClasspath() {
    var document = new Brondocument("Tekst.", "b.txt", "p", Instant.now());
    when(ingestiePoort.leesBestand(any(Path.class))).thenReturn(document);
    when(chatModel.chat(anyString(), anyString())).thenReturn(FIXTURE_JSON);

    verwerker.verwerk("project", "b.txt", 0);

    verify(chatModel).chat(contains("ervaren ambtenaar"), anyString());
  }

  @Test
  void gooitExceptieBijOngeldigeJson() {
    var document = new Brondocument("tekst", "b.txt", "p", Instant.now());
    when(ingestiePoort.leesBestand(any(Path.class))).thenReturn(document);
    when(chatModel.chat(anyString(), anyString())).thenReturn("geen json");

    assertThrows(RuntimeException.class, () -> verwerker.verwerk("project", "b.txt", 0));
  }
}
