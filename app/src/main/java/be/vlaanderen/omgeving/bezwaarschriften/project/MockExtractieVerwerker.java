package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Mock-implementatie van {@link ExtractieVerwerker} die fixture-data gebruikt.
 *
 * <p>Zoekt bij verwerking naar een bijbehorend JSON-fixture bestand in de testdata-directory.
 * Gooit een {@link IllegalStateException} als er geen fixture gevonden wordt.
 */
@Component
@Profile("dev")
@ConditionalOnMissingBean(ExtractieVerwerker.class)
public class MockExtractieVerwerker implements ExtractieVerwerker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final IngestiePoort ingestiePoort;
  private final Path inputFolder;
  private final Path testdataBaseDir;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Maakt een nieuwe MockExtractieVerwerker aan.
   *
   * @param ingestiePoort Port voor bestandsingestie
   * @param inputFolderString Root input folder als string-pad
   * @param testdataPad Pad naar testdata project-directory met fixture JSON bestanden
   */
  public MockExtractieVerwerker(
      IngestiePoort ingestiePoort,
      @Value("${bezwaarschriften.input.folder}") String inputFolderString,
      @Value("${bezwaarschriften.testdata.pad:}") String testdataPad) {
    this.ingestiePoort = ingestiePoort;
    this.inputFolder = Path.of(inputFolderString);
    this.testdataBaseDir = resolveFixtureDir(testdataPad);
    if (this.testdataBaseDir != null) {
      LOGGER.info("Fixture-basismap geconfigureerd: {}", this.testdataBaseDir.toAbsolutePath());
    }
  }

  private static Path resolveFixtureDir(String testdataPad) {
    if (testdataPad.isEmpty()) {
      return null;
    }
    var pad = Path.of(testdataPad);
    if (Files.isDirectory(pad)) {
      return pad;
    }
    // Probeer parent directory (als app start vanuit app/ submodule)
    var parentPad = Path.of("..").resolve(testdataPad);
    if (Files.isDirectory(parentPad)) {
      return parentPad;
    }
    LOGGER.warn("Fixture-basismap niet gevonden op '{}' of '{}'",
        pad.toAbsolutePath(), parentPad.toAbsolutePath());
    return null;
  }

  @Override
  public ExtractieResultaat verwerk(String projectNaam, String bestandsnaam, int poging) {
    var tekstBestandsnaam = bestandsnaam.contains(".")
        ? bestandsnaam.substring(0, bestandsnaam.lastIndexOf('.')) + ".txt"
        : bestandsnaam + ".txt";
    var pad = inputFolder.resolve(projectNaam).resolve("bezwaren-text").resolve(tekstBestandsnaam);
    var brondocument = ingestiePoort.leesBestand(pad);
    var aantalWoorden = telWoorden(brondocument.tekst());

    var fixtureResultaat = zoekFixture(projectNaam, bestandsnaam, aantalWoorden);
    if (fixtureResultaat == null) {
      throw new IllegalStateException(
          ("Geen fixture gevonden voor bestand '%s' in project '%s'. "
              + "Voeg een JSON-fixture toe in testdata/%s/bezwaren/")
              .formatted(bestandsnaam, projectNaam, projectNaam));
    }
    LOGGER.info("Bestand '{}' verwerkt via fixture (project '{}', {} bezwaren)",
        bestandsnaam, projectNaam, fixtureResultaat.aantalBezwaren());
    return fixtureResultaat;
  }

  private ExtractieResultaat zoekFixture(String projectNaam, String bestandsnaam, int aantalWoorden) {
    if (testdataBaseDir == null) {
      return null;
    }
    var naamZonderExtensie = bestandsnaam.contains(".")
        ? bestandsnaam.substring(0, bestandsnaam.lastIndexOf('.'))
        : bestandsnaam;
    var fixturePad = zoekFixturePad(projectNaam, naamZonderExtensie);
    if (fixturePad == null) {
      LOGGER.debug("Geen fixture gevonden voor '{}' in project '{}'", bestandsnaam, projectNaam);
      return null;
    }
    try {
      var json = Files.readString(fixturePad);
      return parseFixture(json, aantalWoorden);
    } catch (IOException e) {
      LOGGER.warn("Kon fixture niet lezen: {}", fixturePad, e);
      return null;
    }
  }

  private Path zoekFixturePad(String projectNaam, String naamZonderExtensie) {
    var jsonNaam = naamZonderExtensie + ".json";
    // Probeer eerst exact projectpad
    var kandidaat = testdataBaseDir.resolve(projectNaam).resolve("bezwaren").resolve(jsonNaam);
    if (Files.exists(kandidaat)) {
      return kandidaat;
    }
    // Doorzoek alle projectmappen als fallback (projectnaam in app kan afwijken van testdata)
    try (var stream = Files.list(testdataBaseDir)) {
      return stream
          .filter(Files::isDirectory)
          .map(dir -> dir.resolve("bezwaren").resolve(jsonNaam))
          .filter(Files::exists)
          .findFirst()
          .orElse(null);
    } catch (IOException e) {
      LOGGER.warn("Kon testdata basismap niet doorzoeken: {}", testdataBaseDir, e);
      return null;
    }
  }

  private ExtractieResultaat parseFixture(String json, int aantalWoorden) {
    try {
      var root = objectMapper.readTree(json);
      var passages = parsePassages(root.get("passages"));
      var bezwaren = parseBezwaren(root.get("bezwaren"));
      var metadata = root.get("metadata");
      String samenvatting = metadata.has("documentSamenvatting")
          ? metadata.get("documentSamenvatting").asText() : null;
      return new ExtractieResultaat(aantalWoorden, bezwaren.size(), passages, bezwaren,
          samenvatting);
    } catch (Exception e) {
      LOGGER.warn("Kon fixture JSON niet parsen: {}", e.getMessage());
      return null;
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
            item.get("passageId").asInt(),
            item.get("samenvatting").asText(),
            "Overige"));
      }
    }
    return List.copyOf(lijst);
  }

  private int telWoorden(String tekst) {
    if (tekst == null || tekst.isBlank()) {
      return 0;
    }
    return tekst.strip().split("\\s+").length;
  }

}
