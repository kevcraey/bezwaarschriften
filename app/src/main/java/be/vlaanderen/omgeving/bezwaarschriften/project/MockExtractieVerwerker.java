package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mock-implementatie van {@link ExtractieVerwerker} die een AI-extractie simuleert.
 *
 * <p>Gebruikt configureerbare delay om realistische verwerkingstijden na te bootsen.
 * Bestanden met "2" in de naam slagen enkel bij elke 3e aanroep (aanroep 3, 6, 9, ...).
 */
@Component
public class MockExtractieVerwerker implements ExtractieVerwerker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final IngestiePoort ingestiePoort;
  private final Path inputFolder;
  private final int minDelaySeconden;
  private final int maxDelaySeconden;
  private final ConcurrentHashMap<String, AtomicInteger> aanroepTeller =
      new ConcurrentHashMap<>();

  /**
   * Maakt een nieuwe MockExtractieVerwerker aan.
   *
   * @param ingestiePoort Port voor bestandsingestie
   * @param inputFolderString Root input folder als string-pad
   * @param minDelaySeconden Minimale vertraging in seconden
   * @param maxDelaySeconden Maximale vertraging in seconden
   */
  public MockExtractieVerwerker(
      IngestiePoort ingestiePoort,
      @Value("${bezwaarschriften.input.folder}") String inputFolderString,
      @Value("${bezwaarschriften.extractie.mock.min-delay-seconden:5}") int minDelaySeconden,
      @Value("${bezwaarschriften.extractie.mock.max-delay-seconden:30}") int maxDelaySeconden) {
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

    int aantalBezwaren = bepaalAantalBezwaren(bestandsnaam);

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
}
