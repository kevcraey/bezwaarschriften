package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ExtendWith(MockitoExtension.class)
class ConsolidatieWorkerTest {

  @Mock
  private ConsolidatieTaakService service;

  @Mock
  private ConsolidatieVerwerker verwerker;

  private ThreadPoolTaskExecutor executor;
  private ConsolidatieWorker worker;

  @BeforeEach
  void setUp() {
    executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setThreadNamePrefix("test-consolidatie-");
    executor.initialize();
    worker = new ConsolidatieWorker(service, verwerker, executor);
  }

  @AfterEach
  void tearDown() {
    executor.shutdown();
  }

  @Test
  void paktTakenOpEnVoertUit() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt");
    when(service.pakOpVoorVerwerking()).thenReturn(List.of(taak));

    worker.verwerkTaken();

    verify(service, timeout(2000)).markeerKlaar(1L);
  }

  @Test
  void markeertFoutBijException() {
    var taak = maakTaak(2L, "snelweg", "bezwaar-042.txt");
    when(service.pakOpVoorVerwerking()).thenReturn(List.of(taak));
    doThrow(new RuntimeException("Consolidatie mislukt"))
        .when(verwerker).verwerk("snelweg", "bezwaar-042.txt", 0);

    worker.verwerkTaken();

    verify(service, timeout(2000)).markeerFout(2L, "Consolidatie mislukt");
  }

  @Test
  void doetNietsAlsGeenTakenBeschikbaar() {
    when(service.pakOpVoorVerwerking()).thenReturn(List.of());

    worker.verwerkTaken();

    verify(verwerker, never()).verwerk(anyString(), anyString(), anyInt());
  }

  @Test
  void annuleerTaakCanceltLopendeFuture() throws Exception {
    var taak = maakTaak(5L, "windmolens", "stuck.txt");
    when(service.pakOpVoorVerwerking()).thenReturn(List.of(taak));
    doAnswer(invocation -> {
      Thread.sleep(10_000);
      return null;
    }).when(verwerker).verwerk("windmolens", "stuck.txt", 0);

    worker.verwerkTaken();
    Thread.sleep(200);

    boolean geannuleerd = worker.annuleerTaak(5L);

    assertThat(geannuleerd).isTrue();
  }

  private ConsolidatieTaak maakTaak(Long id, String projectNaam, String bestandsnaam) {
    var taak = new ConsolidatieTaak();
    taak.setId(id);
    taak.setProjectNaam(projectNaam);
    taak.setBestandsnaam(bestandsnaam);
    taak.setStatus(ConsolidatieTaakStatus.BEZIG);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    taak.setAangemaaktOp(Instant.now());
    return taak;
  }
}
