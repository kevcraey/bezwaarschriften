package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocument;
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
 * en dient opgepakte documenten in bij de thread pool voor parallelle verwerking.
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
   * Pollt periodiek voor documenten met status BEZIG en dient ze in bij de thread pool.
   */
  @Scheduled(fixedDelay = 1000)
  public void verwerkTaken() {
    var documenten = service.pakOpVoorVerwerking();
    for (var document : documenten) {
      if (lopendeTaken.containsKey(document.getId())) {
        continue;
      }
      var future = executor.submit(() -> verwerkDocument(document));
      lopendeTaken.put(document.getId(), future);
    }
  }

  private void verwerkDocument(BezwaarDocument document) {
    try {
      service.verwerkTaak(document);
    } catch (Throwable e) {
      LOGGER.error("Fout bij verwerking van tekst-extractie document {}: {}",
          document.getId(), e.getMessage(), e);
      try {
        service.markeerFout(document.getId(), e.getMessage());
      } catch (IllegalArgumentException ex) {
        LOGGER.info("Document {} niet meer aanwezig na fout", document.getId());
      }
    } finally {
      lopendeTaken.remove(document.getId());
    }
  }

  /**
   * Annuleert een lopende tekst-extractie taak.
   *
   * @param documentId id van het te annuleren document
   */
  public void annuleerTaak(Long documentId) {
    var future = lopendeTaken.remove(documentId);
    if (future != null) {
      future.cancel(true);
      LOGGER.info("Tekst-extractie voor document {} geannuleerd", documentId);
    }
  }
}
