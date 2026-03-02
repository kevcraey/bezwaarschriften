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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Mock-implementatie van {@link ExtractieVerwerker} die fixture-data gebruikt als die
 * beschikbaar is, en anders terugvalt op gesimuleerde resultaten.
 *
 * <p>Zoekt bij verwerking naar een bijbehorend JSON-fixture bestand in de testdata-directory.
 * Als dat bestaat, wordt het geparsed en als realistisch resultaat teruggegeven.
 * Als er geen fixture is, worden random aantallen gegenereerd.
 */
@Component
@Profile("dev")
public class MockExtractieVerwerker implements ExtractieVerwerker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final IngestiePoort ingestiePoort;
  private final Path inputFolder;
  private final Path testdataFixtureDir;
  private final int minDelaySeconden;
  private final int maxDelaySeconden;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ConcurrentHashMap<String, AtomicInteger> aanroepTeller =
      new ConcurrentHashMap<>();

  /**
   * Maakt een nieuwe MockExtractieVerwerker aan.
   *
   * @param ingestiePoort Port voor bestandsingestie
   * @param inputFolderString Root input folder als string-pad
   * @param testdataPad Pad naar testdata project-directory met fixture JSON bestanden
   * @param minDelaySeconden Minimale vertraging in seconden
   * @param maxDelaySeconden Maximale vertraging in seconden
   */
  public MockExtractieVerwerker(
      IngestiePoort ingestiePoort,
      @Value("${bezwaarschriften.input.folder}") String inputFolderString,
      @Value("${bezwaarschriften.testdata.pad:}") String testdataPad,
      @Value("${bezwaarschriften.extractie.mock.min-delay-seconden:5}") int minDelaySeconden,
      @Value("${bezwaarschriften.extractie.mock.max-delay-seconden:30}") int maxDelaySeconden) {
    this.ingestiePoort = ingestiePoort;
    this.inputFolder = Path.of(inputFolderString);
    this.testdataFixtureDir = testdataPad.isEmpty()
        ? null : Path.of(testdataPad).resolve("bezwaren");
    this.minDelaySeconden = minDelaySeconden;
    this.maxDelaySeconden = maxDelaySeconden;
  }

  @Override
  public ExtractieResultaat verwerk(String projectNaam, String bestandsnaam, int poging) {
    var pad = inputFolder.resolve(projectNaam).resolve("bezwaren").resolve(bestandsnaam);
    var brondocument = ingestiePoort.leesBestand(pad);
    var aantalWoorden = telWoorden(brondocument.tekst());

    simuleerDelay();

    var fixtureResultaat = zoekFixture(bestandsnaam, aantalWoorden);
    if (fixtureResultaat != null) {
      LOGGER.info("Bestand '{}' verwerkt via fixture (project '{}', {} bezwaren)",
          bestandsnaam, projectNaam, fixtureResultaat.aantalBezwaren());
      return fixtureResultaat;
    }

    int aantalBezwaren = bepaalAantalBezwaren(bestandsnaam);
    LOGGER.info("Bestand '{}' verwerkt zonder fixture (project '{}', {} woorden, {} bezwaren)",
        bestandsnaam, projectNaam, aantalWoorden, aantalBezwaren);
    return new ExtractieResultaat(aantalWoorden, aantalBezwaren);
  }

  private ExtractieResultaat zoekFixture(String bestandsnaam, int aantalWoorden) {
    if (testdataFixtureDir == null) {
      return null;
    }
    var naamZonderExtensie = bestandsnaam.contains(".")
        ? bestandsnaam.substring(0, bestandsnaam.lastIndexOf('.'))
        : bestandsnaam;
    var fixturePad = testdataFixtureDir.resolve(naamZonderExtensie + ".json");
    if (!Files.exists(fixturePad)) {
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
            item.get("categorie").asText()));
      }
    }
    return List.copyOf(lijst);
  }

  private int bepaalAantalBezwaren(String bestandsnaam) {
    if (bestandsnaam.contains("2")) {
      int aanroep = aanroepTeller
          .computeIfAbsent(bestandsnaam, k -> new AtomicInteger(0))
          .incrementAndGet();
      if (aanroep % 3 != 0) {
        throw new RuntimeException(
            "Gesimuleerde fout (aanroep " + aanroep + ") voor bestand '" + bestandsnaam + "'");
      }
      return 4;
    }
    return ThreadLocalRandom.current().nextInt(2, 6);
  }

  private int telWoorden(String tekst) {
    if (tekst == null || tekst.isBlank()) {
      return 0;
    }
    return tekst.strip().split("\\s+").length;
  }

  private void simuleerDelay() {
    if (maxDelaySeconden <= 0) {
      return;
    }
    try {
      long delayMillis = ThreadLocalRandom.current()
          .nextLong(minDelaySeconden * 1000L, maxDelaySeconden * 1000L + 1);
      LOGGER.debug("Simuleer verwerkingsdelay van {} ms", delayMillis);
      Thread.sleep(delayMillis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.warn("Verwerkingsdelay onderbroken");
    }
  }
}
