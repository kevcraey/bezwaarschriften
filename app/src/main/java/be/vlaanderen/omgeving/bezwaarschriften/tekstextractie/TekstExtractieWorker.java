package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

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
 * Worker die periodiek wachtende tekst-extractie taken oppakt en asynchroon verwerkt.
 *
 * <p>Pollt elke seconde via {@link TekstExtractieService#pakOpVoorVerwerking()}
 * en dient opgepakte taken in bij de thread pool voor parallelle verwerking.
 */
@Component
public class TekstExtractieWorker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final TekstExtractieService service;
  private final ThreadPoolTaskExecutor executor;
  private final ConcurrentHashMap<Long, Future<?>> lopendeTaken = new ConcurrentHashMap<>();

  /**
   * Maakt een nieuwe TekstExtractieWorker aan.
   *
   * @param service service voor tekst-extractie taken
   * @param executor thread pool voor asynchrone uitvoering
   */
  public TekstExtractieWorker(TekstExtractieService service,
      @Qualifier("tekstExtractieExecutor") ThreadPoolTaskExecutor executor) {
    this.service = service;
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

  private void verwerkTaak(TekstExtractieTaak taak) {
    try {
      service.verwerkTaak(taak);
    } catch (Exception e) {
      LOGGER.error("Fout bij verwerking van tekst-extractie taak {}: {}",
          taak.getId(), e.getMessage(), e);
      try {
        service.markeerMislukt(taak.getId(), e.getMessage());
      } catch (IllegalArgumentException ex) {
        LOGGER.info("Taak {} niet meer aanwezig na fout", taak.getId());
      }
    } finally {
      lopendeTaken.remove(taak.getId());
    }
  }
}
