package be.vlaanderen.omgeving.bezwaarschriften.project;

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
 * Worker die periodiek wachtende extractie-taken oppakt en asynchroon verwerkt.
 *
 * <p>Pollt elke seconde via {@link ExtractieTaakService#pakOpVoorVerwerking()}
 * en dient opgepakte taken in bij de thread pool voor parallelle verwerking.
 */
@Component
public class ExtractieWorker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ExtractieTaakService service;
  private final ExtractieVerwerker verwerker;
  private final ThreadPoolTaskExecutor executor;
  private final ConcurrentHashMap<Long, Future<?>> lopendeTaken = new ConcurrentHashMap<>();

  /**
   * Maakt een nieuwe ExtractieWorker aan.
   *
   * @param service service voor het beheren van extractie-taken
   * @param verwerker verwerker die de daadwerkelijke extractie uitvoert
   * @param executor thread pool voor asynchrone uitvoering
   */
  public ExtractieWorker(ExtractieTaakService service, ExtractieVerwerker verwerker,
      @Qualifier("extractieExecutor") ThreadPoolTaskExecutor executor) {
    this.service = service;
    this.verwerker = verwerker;
    this.executor = executor;
  }

  /**
   * Pollt periodiek voor wachtende taken en dient ze in bij de thread pool.
   */
  @Scheduled(fixedDelay = 1000)
  public void verwerkTaken() {
    var taken = service.pakOpVoorVerwerking();
    for (var taak : taken) {
      var future = executor.submit(() -> verwerkTaak(taak));
      lopendeTaken.put(taak.getId(), future);
    }
  }

  /**
   * Annuleert een lopende taak door de bijbehorende Future te cancellen.
   *
   * @param taakId id van de te annuleren taak
   * @return true als de taak gevonden en geannuleerd is, false als er geen lopende taak was
   */
  public boolean annuleerTaak(Long taakId) {
    var future = lopendeTaken.remove(taakId);
    if (future != null) {
      LOGGER.info("Taak {} geannuleerd", taakId);
      return future.cancel(true);
    }
    return false;
  }

  private void verwerkTaak(ExtractieTaak taak) {
    try {
      var resultaat = verwerker.verwerk(
          taak.getProjectNaam(),
          taak.getBestandsnaam(),
          taak.getAantalPogingen());
      try {
        service.markeerKlaar(taak.getId(), resultaat);
      } catch (IllegalArgumentException e) {
        LOGGER.info("Taak {} niet meer aanwezig na voltooiing (geannuleerd?)", taak.getId());
      }
    } catch (Exception e) {
      LOGGER.error("Fout bij verwerking van taak {}: {}", taak.getId(), e.getMessage(), e);
      try {
        service.markeerFout(taak.getId(), e.getMessage());
      } catch (IllegalArgumentException ex) {
        LOGGER.info("Taak {} niet meer aanwezig na fout (geannuleerd?)", taak.getId());
      }
    } finally {
      lopendeTaken.remove(taak.getId());
    }
  }
}
