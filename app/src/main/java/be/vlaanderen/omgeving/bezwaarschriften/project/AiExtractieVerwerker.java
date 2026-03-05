package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extractie-verwerker die een LLM (via {@link ChatModelPoort}) gebruikt om individuele
 * bezwaren te identificeren in bezwaarschriften.
 */
public class AiExtractieVerwerker implements ExtractieVerwerker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String SYSTEM_PROMPT = """
      Je bent een ervaren ambtenaar bij het Departement Omgeving van de Vlaamse overheid.
      Je analyseert bezwaarschriften die zijn ingediend tijdens een openbaar onderzoek.

      Je taak is om uit het bezwaarschrift alle individuele bezwaren te identificeren.

      ## Wat is een individueel bezwaar?

      Een individueel bezwaar is een concreet punt van bezwaar dat zelfstandig beantwoord
      kan worden door de vergunningverlenende overheid. Voorbeelden:
      - "De geluidsoverlast door evenementen zal onze nachtrust verstoren" \
      = een bezwaar (geluidshinder)
      - "Het verkeer zal toenemen EN er zijn onvoldoende parkeerplaatsen" \
      = TWEE bezwaren (verkeerslast + parkeertekort), ook al staan ze in dezelfde zin

      Splits passages die meerdere bezwaren bevatten altijd op in afzonderlijke items.
      Een passage kan dus leiden tot meerdere bezwaren.

      ## Per bezwaar lever je:

      1. **passage**: De letterlijke tekst uit het bezwaarschrift waaruit dit bezwaar blijkt.
      2. **samenvatting**: Een zin die het bezwaar kernachtig beschrijft in je eigen woorden.
      3. **categorie**: Een van: milieu, mobiliteit, ruimtelijke_ordening, procedure,
         gezondheid, economisch, sociaal, overig.

      Antwoord UITSLUITEND in het volgende JSON-formaat (geen extra tekst):
      {
        "passages": [{ "id": 1, "tekst": "..." }],
        "bezwaren": [{ "passageId": 1, "samenvatting": "...", "categorie": "..." }],
        "metadata": { "aantalWoorden": 0, "documentSamenvatting": "..." }
      }
      """;

  private static final String USER_PROMPT_TEMPLATE = """
      Context: Openbaar onderzoek voor het project "Herontwikkeling Gaverbeek Stadion"
      in Waregem - bouw van een multifunctioneel stadion met parking, commerciele ruimtes
      en publieke groenzones langs de Gaverbeek.

      Analyseer het volgende bezwaarschrift en extraheer alle individuele bezwaren:

      ---
      %s
      ---
      """;

  private final ChatModelPoort chatModel;
  private final IngestiePoort ingestiePoort;
  private final Path inputFolder;
  private final ObjectMapper objectMapper;

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
  }

  @Override
  public ExtractieResultaat verwerk(String projectNaam, String bestandsnaam, int poging) {
    var pad = inputFolder.resolve(projectNaam).resolve("bezwaren").resolve(bestandsnaam);
    var brondocument = ingestiePoort.leesBestand(pad);
    var userPrompt = String.format(USER_PROMPT_TEMPLATE, brondocument.tekst());

    LOGGER.info("Start LLM-extractie voor '{}' (project '{}', poging {})",
        bestandsnaam, projectNaam, poging);

    var response = chatModel.chat(SYSTEM_PROMPT, userPrompt);
    return parseResponse(response);
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
