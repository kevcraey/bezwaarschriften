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
 * Worker die periodiek documenten met bezwaar-extractie status BEZIG oppakt
 * en asynchroon verwerkt.
 *
 * <p>Pollt elke seconde via {@link ExtractieTaakService#pakOpVoorVerwerking()}
 * en dient opgepakte documenten in bij de thread pool voor parallelle verwerking.
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
   * @param service service voor het beheren van bezwaar-extractie
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
   * Pollt periodiek voor documenten met status BEZIG en dient ze in bij de thread pool.
   */
  @Scheduled(fixedDelay = 1000)
  public void verwerkTaken() {
    var documenten = service.pakOpVoorVerwerking();
    for (var doc : documenten) {
      if (lopendeTaken.containsKey(doc.getId())) {
        continue;
      }
      var future = executor.submit(() -> verwerkDocument(doc));
      lopendeTaken.put(doc.getId(), future);
    }
  }

  /**
   * Annuleert een lopende taak door de bijbehorende Future te cancellen.
   *
   * @param documentId id van het te annuleren document
   * @return true als de taak gevonden en geannuleerd is, false als er geen lopende taak was
   */
  public boolean annuleerTaak(Long documentId) {
    var future = lopendeTaken.remove(documentId);
    if (future != null) {
      LOGGER.info("Document {} geannuleerd", documentId);
      return future.cancel(true);
    }
    return false;
  }

  private void verwerkDocument(BezwaarDocument doc) {
    try {
      service.markeerVerwerkingGestart(doc.getId());
      var resultaat = verwerker.verwerk(
          doc.getProjectNaam(),
          doc.getBestandsnaam(),
          0);
      try {
        service.markeerKlaar(doc.getId(), resultaat);
      } catch (IllegalArgumentException e) {
        LOGGER.info("Document {} niet meer aanwezig na voltooiing (geannuleerd?)", doc.getId());
      }
    } catch (Throwable e) {
      LOGGER.error("Fout bij verwerking van document {}: {}", doc.getId(), e.getMessage(), e);
      try {
        service.markeerFout(doc.getId(), e.getMessage());
      } catch (IllegalArgumentException ex) {
        LOGGER.info("Document {} niet meer aanwezig na fout (geannuleerd?)", doc.getId());
      }
    } finally {
      lopendeTaken.remove(doc.getId());
    }
  }
}
