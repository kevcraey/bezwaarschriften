package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

/**
 * Extractie-verwerker die een LLM (via {@link ChatModelPoort}) gebruikt om individuele
 * bezwaren te identificeren in bezwaarschriften.
 */
public class AiExtractieVerwerker implements ExtractieVerwerker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ChatModelPoort chatModel;
  private final IngestiePoort ingestiePoort;
  private final Path inputFolder;
  private final ObjectMapper objectMapper;
  private final String systemPrompt;
  private final String userPromptTemplate;

  /**
   * Maakt een nieuwe AiExtractieVerwerker aan.
   *
   * @param chatModel port naar het taalmodel
   * @param ingestiePoort port voor bestandsingestie
   * @param inputFolderString root input folder
   */
  public AiExtractieVerwerker(ChatModelPoort chatModel, IngestiePoort ingestiePoort,
      String inputFolderString) {
    this.chatModel = chatModel;
    this.ingestiePoort = ingestiePoort;
    this.inputFolder = Path.of(inputFolderString);
    this.objectMapper = new ObjectMapper();
    this.systemPrompt = laadPrompt("prompts/extractie-system.md");
    this.userPromptTemplate = laadPrompt("prompts/extractie-user.md");
  }

  @Override
  public ExtractieResultaat verwerk(String projectNaam, String bestandsnaam, int poging) {
    var tekstBestandsnaam = bestandsnaam.contains(".")
        ? bestandsnaam.substring(0, bestandsnaam.lastIndexOf('.')) + ".txt"
        : bestandsnaam + ".txt";
    var pad = inputFolder.resolve(projectNaam).resolve("bezwaren-text").resolve(tekstBestandsnaam);
    var brondocument = ingestiePoort.leesBestand(pad);
    var userPrompt = String.format(userPromptTemplate, brondocument.tekst());

    LOGGER.info("Start LLM-extractie voor '{}' (project '{}', poging {})",
        bestandsnaam, projectNaam, poging);

    var response = chatModel.chat(systemPrompt, userPrompt);
    return parseResponse(response);
  }

  private static String laadPrompt(String classpathPad) {
    try {
      var resource = new ClassPathResource(classpathPad);
      return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Kan prompt niet laden: " + classpathPad, e);
    }
  }

  private ExtractieResultaat parseResponse(String json) {
    try {
      var root = objectMapper.readTree(json);
      var passages = parsePassages(root.get("passages"));
      var bezwaren = parseBezwaren(root.get("bezwaren"));
      var metadata = root.get("metadata");
      int aantalWoorden = metadata.get("aantalWoorden").asInt();
      String samenvatting = metadata.get("documentSamenvatting").asText();
      return new ExtractieResultaat(aantalWoorden, bezwaren.size(), passages, bezwaren,
          samenvatting);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Ongeldig JSON-antwoord van LLM: " + e.getMessage(), e);
    }
  }

  private List<Passage> parsePassages(JsonNode node) {
    var lijst = new ArrayList<Passage>();
    if (node != null && node.isArray()) {
      for (var item : node) {
        lijst.add(new Passage(item.get("id").asInt(), item.get("tekst").asText()));
      }
    }
    return List.copyOf(lijst);
  }

  private List<GeextraheerdBezwaar> parseBezwaren(JsonNode node) {
    var lijst = new ArrayList<GeextraheerdBezwaar>();
    if (node != null && node.isArray()) {
      for (var item : node) {
        lijst.add(new GeextraheerdBezwaar(
            item.has("passageId") ? item.get("passageId").asInt() : 0,
            item.has("samenvatting") ? item.get("samenvatting").asText() : "",
            item.has("categorie") ? item.get("categorie").asText() : "overig"));
      }
    }
    return List.copyOf(lijst);
  }
}
