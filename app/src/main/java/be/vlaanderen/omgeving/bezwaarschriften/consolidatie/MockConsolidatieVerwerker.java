package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mock-implementatie van {@link ConsolidatieVerwerker} die een configureerbare
 * vertraging simuleert.
 *
 * <p>Bedoeld voor ontwikkeling en testen, zodat de volledige worker-pipeline
 * kan worden getest zonder echte AI-verwerking.
 */
@Component
public class MockConsolidatieVerwerker implements ConsolidatieVerwerker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final int minDelaySeconden;
  private final int maxDelaySeconden;

  public MockConsolidatieVerwerker(
      @Value("${bezwaarschriften.consolidatie.mock.min-delay-seconden:2}") int minDelaySeconden,
      @Value("${bezwaarschriften.consolidatie.mock.max-delay-seconden:5}") int maxDelaySeconden) {
    this.minDelaySeconden = minDelaySeconden;
    this.maxDelaySeconden = maxDelaySeconden;
  }

  @Override
  public void verwerk(String projectNaam, String bestandsnaam, int poging) {
    simuleerDelay();
    LOGGER.info("[MOCK] Consolidatie afgerond voor '{}' in project '{}' (poging {})",
        bestandsnaam, projectNaam, poging);
  }

  private void simuleerDelay() {
    if (maxDelaySeconden <= 0) {
      return;
    }
    try {
      long delayMillis = ThreadLocalRandom.current()
          .nextLong(minDelaySeconden * 1000L, maxDelaySeconden * 1000L + 1);
      Thread.sleep(delayMillis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
