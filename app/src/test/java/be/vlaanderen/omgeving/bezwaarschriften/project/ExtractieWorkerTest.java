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
  void paktDocumentenOpEnVoertUit() {
    var doc = maakDocument(1L, "windmolens", "bezwaar-001.txt");
    when(service.pakOpVoorVerwerking()).thenReturn(List.of(doc));
    when(verwerker.verwerk("windmolens", "bezwaar-001.txt", 0))
        .thenReturn(new ExtractieResultaat(500, 7));

    worker.verwerkTaken();

    verify(service, timeout(2000)).markeerKlaar(eq(1L), any(ExtractieResultaat.class));
  }

  @Test
  void markeertFoutBijException() {
    var doc = maakDocument(2L, "snelweg", "bezwaar-042.txt");
    when(service.pakOpVoorVerwerking()).thenReturn(List.of(doc));
    when(verwerker.verwerk("snelweg", "bezwaar-042.txt", 0))
        .thenThrow(new RuntimeException("AI-service onbereikbaar"));

    worker.verwerkTaken();

    verify(service, timeout(2000)).markeerFout(2L, "AI-service onbereikbaar");
  }

  @Test
  void doetNietsAlsGeenDocumentenBeschikbaar() {
    when(service.pakOpVoorVerwerking()).thenReturn(List.of());

    worker.verwerkTaken();

    verify(verwerker, never()).verwerk(anyString(), anyString(), anyInt());
    verify(service, never()).markeerKlaar(anyLong(), any(ExtractieResultaat.class));
    verify(service, never()).markeerFout(anyLong(), anyString());
  }

  @Test
  void annuleerTaakCanceltLopendeFuture() throws Exception {
    var doc = maakDocument(5L, "windmolens", "stuck.txt");
    when(service.pakOpVoorVerwerking()).thenReturn(List.of(doc));
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

  private BezwaarDocument maakDocument(Long id, String projectNaam, String bestandsnaam) {
    var doc = new BezwaarDocument();
    doc.setId(id);
    doc.setProjectNaam(projectNaam);
    doc.setBestandsnaam(bestandsnaam);
    doc.setBezwaarExtractieStatus(BezwaarExtractieStatus.BEZIG);
    return doc;
  }
}
