package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mock-implementatie van {@link ExtractieVerwerker} die een AI-extractie simuleert.
 *
 * <p>Gebruikt configureerbare delay om realistische verwerkingstijden na te bootsen.
 * Het tweede .txt-bestand (index 1) faalt altijd bij de eerste poging.
 */
@Component
public class MockExtractieVerwerker implements ExtractieVerwerker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ProjectPoort projectPoort;
  private final IngestiePoort ingestiePoort;
  private final Path inputFolder;
  private final int minDelaySeconden;
  private final int maxDelaySeconden;

  /**
   * Maakt een nieuwe MockExtractieVerwerker aan.
   *
   * @param projectPoort Port voor projecttoegang
   * @param ingestiePoort Port voor bestandsingestie
   * @param inputFolderString Root input folder als string-pad
   * @param minDelaySeconden Minimale vertraging in seconden
   * @param maxDelaySeconden Maximale vertraging in seconden
   */
  public MockExtractieVerwerker(
      ProjectPoort projectPoort,
      IngestiePoort ingestiePoort,
      @Value("${bezwaarschriften.input.folder}") String inputFolderString,
      @Value("${bezwaarschriften.extractie.mock.min-delay-seconden:5}") int minDelaySeconden,
      @Value("${bezwaarschriften.extractie.mock.max-delay-seconden:30}") int maxDelaySeconden) {
    this.projectPoort = projectPoort;
    this.ingestiePoort = ingestiePoort;
    this.inputFolder = Path.of(inputFolderString);
    this.minDelaySeconden = minDelaySeconden;
    this.maxDelaySeconden = maxDelaySeconden;
  }

  @Override
  public ExtractieResultaat verwerk(String projectNaam, String bestandsnaam, int poging) {
    var pad = inputFolder.resolve(projectNaam).resolve("bezwaren").resolve(bestandsnaam);
    var brondocument = ingestiePoort.leesBestand(pad);
    var aantalWoorden = telWoorden(brondocument.tekst());

    simuleerDelay();

    var txtBestanden = filterTxtBestanden(projectPoort.geefBestandsnamen(projectNaam));
    int index = txtBestanden.indexOf(bestandsnaam);

    int aantalBezwaren = bepaalAantalBezwaren(index, poging, bestandsnaam);

    LOGGER.info("Bestand '{}' verwerkt voor project '{}' (poging {}, {} woorden, {} bezwaren)",
        bestandsnaam, projectNaam, poging, aantalWoorden, aantalBezwaren);

    return new ExtractieResultaat(aantalWoorden, aantalBezwaren);
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

  private List<String> filterTxtBestanden(List<String> bestandsnamen) {
    return bestandsnamen.stream()
        .filter(naam -> naam.toLowerCase().endsWith(".txt"))
        .toList();
  }

  private int bepaalAantalBezwaren(int index, int poging, String bestandsnaam) {
    return switch (index) {
      case 0 -> 3;
      case 1 -> {
        if (poging == 0) {
          throw new RuntimeException(
              "Gesimuleerde fout bij eerste poging voor bestand '" + bestandsnaam + "'");
        }
        yield 4;
      }
      case 2 -> 5;
      default -> 2;
    };
  }
}
