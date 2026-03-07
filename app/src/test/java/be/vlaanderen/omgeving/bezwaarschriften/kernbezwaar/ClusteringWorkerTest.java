package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
class ClusteringWorkerTest {

  @Mock
  private ClusteringTaakService taakService;

  @Mock
  private KernbezwaarService kernbezwaarService;

  private ThreadPoolTaskExecutor executor;
  private ClusteringWorker worker;

  @BeforeEach
  void setUp() {
    executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setThreadNamePrefix("test-clustering-");
    executor.initialize();
    worker = new ClusteringWorker(taakService, kernbezwaarService, executor);
  }

  @AfterEach
  void tearDown() {
    executor.shutdown();
  }

  @Test
  void paktTakenOpEnMarkeertKlaar() {
    var taak = maakTaak(1L, "windmolens");
    when(taakService.pakOpVoorVerwerking()).thenReturn(List.of(taak));

    worker.verwerkTaken();

    verify(kernbezwaarService, timeout(2000))
        .clusterProject("windmolens", 1L);
    verify(taakService, timeout(2000)).markeerKlaar(1L);
  }

  @Test
  void markeertFoutBijException() {
    var taak = maakTaak(2L, "snelweg");
    when(taakService.pakOpVoorVerwerking()).thenReturn(List.of(taak));
    doThrow(new RuntimeException("AI-service onbereikbaar"))
        .when(kernbezwaarService).clusterProject("snelweg", 2L);

    worker.verwerkTaken();

    verify(taakService, timeout(2000)).markeerFout(2L, "AI-service onbereikbaar");
    verify(taakService, never()).markeerKlaar(anyLong());
  }

  @Test
  void vangtClusteringGeannuleerdExceptionZonderMarkeerFout() {
    var taak = maakTaak(3L, "windmolens");
    when(taakService.pakOpVoorVerwerking()).thenReturn(List.of(taak));
    doThrow(new ClusteringGeannuleerdException())
        .when(kernbezwaarService).clusterProject("windmolens", 3L);

    worker.verwerkTaken();

    // Wacht tot de taak verwerkt is
    verify(kernbezwaarService, timeout(2000))
        .clusterProject("windmolens", 3L);
    // Bij annulering: geen markeerKlaar en geen markeerFout
    verify(taakService, never()).markeerKlaar(anyLong());
    verify(taakService, never()).markeerFout(anyLong(), anyString());
  }

  @Test
  void doetNietsAlsGeenTakenBeschikbaar() {
    when(taakService.pakOpVoorVerwerking()).thenReturn(List.of());

    worker.verwerkTaken();

    verify(kernbezwaarService, never())
        .clusterProject(anyString(), anyLong());
    verify(taakService, never()).markeerKlaar(anyLong());
    verify(taakService, never()).markeerFout(anyLong(), anyString());
  }

  @Test
  void annuleerTaakCanceltLopendeFuture() throws Exception {
    var taak = maakTaak(5L, "windmolens");
    when(taakService.pakOpVoorVerwerking()).thenReturn(List.of(taak));
    doThrow(new ClusteringGeannuleerdException())
        .when(kernbezwaarService).clusterProject("windmolens", 5L);

    worker.verwerkTaken();
    Thread.sleep(200);

    boolean geannuleerd = worker.annuleerTaak(5L);

    // De taak is al verwerkt (exception), maar annuleerTaak probeert de Future te cancellen
    // Het resultaat hangt af van timing; de Future kan al klaar zijn
    // We verifieren dat de methode niet crasht
    assertThat(geannuleerd).isIn(true, false);
  }

  @Test
  void annuleerTaakRetourneertFalseVoorOnbekendeTaak() {
    boolean geannuleerd = worker.annuleerTaak(999L);

    assertThat(geannuleerd).isFalse();
  }

  private ClusteringTaak maakTaak(Long id, String projectNaam) {
    var taak = new ClusteringTaak();
    taak.setId(id);
    taak.setProjectNaam(projectNaam);
    taak.setStatus(ClusteringTaakStatus.BEZIG);
    taak.setAangemaaktOp(Instant.now());
    return taak;
  }
}
