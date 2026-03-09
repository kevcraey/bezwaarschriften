package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Worker die periodiek wachtende consolidatie-taken oppakt en asynchroon verwerkt.
 *
 * <p>Pollt elke seconde via {@link #verwerkTaken()} en delegeert de verwerking
 * naar een {@link ConsolidatieVerwerker}. Lopende taken kunnen geannuleerd worden
 * via {@link #annuleerTaak(Long)}.
 */
@Component
public class ConsolidatieWorker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ConsolidatieTaakService service;
  private final ConsolidatieVerwerker verwerker;
  private final ThreadPoolTaskExecutor executor;
  private final ConcurrentHashMap<Long, Future<?>> lopendeTaken = new ConcurrentHashMap<>();

  public ConsolidatieWorker(ConsolidatieTaakService service, ConsolidatieVerwerker verwerker,
      @Qualifier("consolidatieExecutor") ThreadPoolTaskExecutor executor) {
    this.service = service;
    this.verwerker = verwerker;
    this.executor = executor;
  }

  @Scheduled(fixedDelay = 1000)
  public void verwerkTaken() {
    var taken = service.pakOpVoorVerwerking();
    for (var taak : taken) {
      var future = executor.submit(() -> verwerkTaak(taak));
      lopendeTaken.put(taak.getId(), future);
    }
  }

  /**
   * Annuleert een lopende consolidatie-taak.
   *
   * @param taakId het ID van de te annuleren taak
   * @return {@code true} als de taak succesvol geannuleerd is
   */
  public boolean annuleerTaak(Long taakId) {
    var future = lopendeTaken.remove(taakId);
    if (future != null) {
      LOGGER.info("Consolidatie-taak {} geannuleerd", taakId);
      return future.cancel(true);
    }
    return false;
  }

  private void verwerkTaak(ConsolidatieTaak taak) {
    try {
      verwerker.verwerk(taak.getProjectNaam(), taak.getBestandsnaam(), taak.getAantalPogingen());
      try {
        service.markeerKlaar(taak.getId());
      } catch (IllegalArgumentException e) {
        LOGGER.info("Consolidatie-taak {} niet meer aanwezig na voltooiing", taak.getId());
      }
    } catch (Throwable e) {
      LOGGER.error("Fout bij consolidatie-taak {}: {}", taak.getId(), e.getMessage(), e);
      try {
        service.markeerFout(taak.getId(), e.getMessage());
      } catch (IllegalArgumentException ex) {
        LOGGER.info("Consolidatie-taak {} niet meer aanwezig na fout", taak.getId());
      }
    } finally {
      lopendeTaken.remove(taak.getId());
    }
  }
}
