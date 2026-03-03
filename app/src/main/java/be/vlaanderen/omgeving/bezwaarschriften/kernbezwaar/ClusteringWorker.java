package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

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
 * Worker die periodiek wachtende clustering-taken oppakt en asynchroon verwerkt.
 *
 * <p>Pollt elke seconde via {@link ClusteringTaakService#pakOpVoorVerwerking()}
 * en dient opgepakte taken in bij de thread pool voor parallelle verwerking.
 */
@Component
public class ClusteringWorker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ClusteringTaakService taakService;
  private final KernbezwaarService kernbezwaarService;
  private final ThreadPoolTaskExecutor executor;
  private final ConcurrentHashMap<Long, Future<?>> actieveTaken = new ConcurrentHashMap<>();

  /**
   * Maakt een nieuwe ClusteringWorker aan.
   *
   * @param taakService service voor het beheren van clustering-taken
   * @param kernbezwaarService service die de daadwerkelijke clustering uitvoert
   * @param executor thread pool voor asynchrone uitvoering
   */
  public ClusteringWorker(ClusteringTaakService taakService,
      KernbezwaarService kernbezwaarService,
      @Qualifier("clusteringExecutor") ThreadPoolTaskExecutor executor) {
    this.taakService = taakService;
    this.kernbezwaarService = kernbezwaarService;
    this.executor = executor;
  }

  /**
   * Pollt periodiek voor wachtende taken en dient ze in bij de thread pool.
   */
  @Scheduled(fixedDelay = 1000)
  public void verwerkTaken() {
    var taken = taakService.pakOpVoorVerwerking();
    for (var taak : taken) {
      var future = executor.submit(() -> verwerkTaak(taak));
      actieveTaken.put(taak.getId(), future);
    }
  }

  /**
   * Annuleert een lopende taak door de bijbehorende Future te cancellen.
   *
   * @param taakId id van de te annuleren taak
   * @return true als de taak gevonden en geannuleerd is, false als er geen lopende taak was
   */
  public boolean annuleerTaak(Long taakId) {
    var future = actieveTaken.remove(taakId);
    if (future != null) {
      LOGGER.info("Clustering-taak {} geannuleerd", taakId);
      return future.cancel(true);
    }
    return false;
  }

  private void verwerkTaak(ClusteringTaak taak) {
    try {
      kernbezwaarService.clusterEenCategorie(
          taak.getProjectNaam(), taak.getCategorie(), taak.getId());
      try {
        taakService.markeerKlaar(taak.getId());
      } catch (IllegalArgumentException e) {
        LOGGER.info("Clustering-taak {} niet meer aanwezig na voltooiing (geannuleerd?)",
            taak.getId());
      }
    } catch (ClusteringGeannuleerdException e) {
      LOGGER.info("Clustering-taak {} is geannuleerd: {}", taak.getId(), e.getMessage());
    } catch (Exception e) {
      LOGGER.error("Fout bij clustering-taak {}: {}", taak.getId(), e.getMessage(), e);
      try {
        taakService.markeerFout(taak.getId(), e.getMessage());
      } catch (IllegalArgumentException ex) {
        LOGGER.info("Clustering-taak {} niet meer aanwezig na fout (geannuleerd?)",
            taak.getId());
      }
    } finally {
      actieveTaken.remove(taak.getId());
    }
  }
}
