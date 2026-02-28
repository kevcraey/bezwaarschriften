package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  /**
   * Maakt een nieuwe ExtractieWorker aan.
   *
   * @param service service voor het beheren van extractie-taken
   * @param verwerker verwerker die de daadwerkelijke extractie uitvoert
   * @param executor thread pool voor asynchrone uitvoering
   */
  public ExtractieWorker(ExtractieTaakService service, ExtractieVerwerker verwerker,
      ThreadPoolTaskExecutor executor) {
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
      executor.submit(() -> verwerkTaak(taak));
    }
  }

  private void verwerkTaak(ExtractieTaak taak) {
    try {
      var resultaat = verwerker.verwerk(
          taak.getProjectNaam(),
          taak.getBestandsnaam(),
          taak.getAantalPogingen());
      service.markeerKlaar(taak.getId(), resultaat.aantalWoorden(), resultaat.aantalBezwaren());
    } catch (Exception e) {
      LOGGER.error("Fout bij verwerking van taak {}: {}", taak.getId(), e.getMessage(), e);
      service.markeerFout(taak.getId(), e.getMessage());
    }
  }
}
