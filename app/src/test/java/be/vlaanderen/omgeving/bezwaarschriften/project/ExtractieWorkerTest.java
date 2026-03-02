package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
class ExtractieWorkerTest {

  @Mock
  private ExtractieTaakService service;

  @Mock
  private ExtractieVerwerker verwerker;

  private ThreadPoolTaskExecutor executor;
  private ExtractieWorker worker;

  @BeforeEach
  void setUp() {
    executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setThreadNamePrefix("test-extractie-");
    executor.initialize();
    worker = new ExtractieWorker(service, verwerker, executor);
  }

  @AfterEach
  void tearDown() {
    executor.shutdown();
  }

  @Test
  void paktTakenOpEnVoertUit() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", 0);
    when(service.pakOpVoorVerwerking()).thenReturn(List.of(taak));
    when(verwerker.verwerk("windmolens", "bezwaar-001.txt", 0))
        .thenReturn(new ExtractieResultaat(500, 7));

    worker.verwerkTaken();

    verify(service, timeout(2000)).markeerKlaar(eq(1L), any(ExtractieResultaat.class));
  }

  @Test
  void markeertFoutBijException() {
    var taak = maakTaak(2L, "snelweg", "bezwaar-042.txt", 1);
    when(service.pakOpVoorVerwerking()).thenReturn(List.of(taak));
    when(verwerker.verwerk("snelweg", "bezwaar-042.txt", 1))
        .thenThrow(new RuntimeException("AI-service onbereikbaar"));

    worker.verwerkTaken();

    verify(service, timeout(2000)).markeerFout(2L, "AI-service onbereikbaar");
  }

  @Test
  void doetNietsAlsGeenTakenBeschikbaar() {
    when(service.pakOpVoorVerwerking()).thenReturn(List.of());

    worker.verwerkTaken();

    verify(verwerker, never()).verwerk(anyString(), anyString(), anyInt());
    verify(service, never()).markeerKlaar(anyLong(), any(ExtractieResultaat.class));
    verify(service, never()).markeerFout(anyLong(), anyString());
  }

  @Test
  void annuleerTaakCanceltLopendeFuture() throws Exception {
    var taak = maakTaak(5L, "windmolens", "stuck.txt", 0);
    when(service.pakOpVoorVerwerking()).thenReturn(List.of(taak));
    when(verwerker.verwerk("windmolens", "stuck.txt", 0))
        .thenAnswer(invocation -> {
          Thread.sleep(10_000);
          return new ExtractieResultaat(100, 1);
        });

    worker.verwerkTaken();
    Thread.sleep(200);

    boolean geannuleerd = worker.annuleerTaak(5L);

    assertThat(geannuleerd).isTrue();
  }

  @Test
  void annuleerTaakRetourneertFalseVoorOnbekendeTaak() {
    boolean geannuleerd = worker.annuleerTaak(999L);

    assertThat(geannuleerd).isFalse();
  }

  private ExtractieTaak maakTaak(Long id, String projectNaam, String bestandsnaam, int pogingen) {
    var taak = new ExtractieTaak();
    taak.setId(id);
    taak.setProjectNaam(projectNaam);
    taak.setBestandsnaam(bestandsnaam);
    taak.setStatus(ExtractieTaakStatus.BEZIG);
    taak.setAantalPogingen(pogingen);
    taak.setMaxPogingen(3);
    taak.setAangemaaktOp(Instant.now());
    return taak;
  }
}
