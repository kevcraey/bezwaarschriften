package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ClusteringTaakControllerTest {

  @Mock
  private ClusteringTaakService taakService;

  @Mock
  private ClusteringWorker clusteringWorker;

  private ClusteringTaakController controller;

  @BeforeEach
  void setUp() {
    controller = new ClusteringTaakController(taakService, clusteringWorker);
  }

  @Test
  void geefTaak_delegeertNaarService() {
    var dto = maakDto(1L, "klaar", 10, 3);
    when(taakService.geefTaak("windmolens")).thenReturn(Optional.of(dto));

    var response = controller.geefTaak("windmolens");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(taakService).geefTaak("windmolens");
  }

  @Test
  void geefTaak_retourneert204AlsGeenTaak() {
    when(taakService.geefTaak("windmolens")).thenReturn(Optional.empty());

    var response = controller.geefTaak("windmolens");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void startClustering_retourneert202() {
    var dto = maakDto(1L, "wachtend", 10, null);
    when(taakService.indienen("windmolens")).thenReturn(dto);

    var response = controller.startClustering("windmolens");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    verify(taakService).indienen("windmolens");
  }

  @Test
  void verwijderClustering_retourneert409BijAntwoorden() {
    when(taakService.geefTaak("windmolens")).thenReturn(Optional.empty());
    when(taakService.verwijderClustering("windmolens", false))
        .thenReturn(VerwijderResultaat.bevestigingVereist(2));

    var response = controller.verwijderClustering("windmolens", false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void verwijderClustering_verwijdertBijBevestiging() {
    when(taakService.geefTaak("windmolens")).thenReturn(Optional.empty());
    when(taakService.verwijderClustering("windmolens", true))
        .thenReturn(VerwijderResultaat.succesvolVerwijderd());

    var response = controller.verwijderClustering("windmolens", true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(taakService).verwijderClustering("windmolens", true);
  }

  @Test
  void verwijderClustering_retourneert200ZonderAntwoorden() {
    when(taakService.geefTaak("windmolens")).thenReturn(Optional.empty());
    when(taakService.verwijderClustering("windmolens", false))
        .thenReturn(VerwijderResultaat.succesvolVerwijderd());

    var response = controller.verwijderClustering("windmolens", false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(taakService).verwijderClustering("windmolens", false);
  }

  @Test
  void verwijderClustering_annuleertWachtendeTaak() {
    var dto = maakDto(1L, "wachtend", 10, null);
    when(taakService.geefTaak("windmolens")).thenReturn(Optional.of(dto));

    var response = controller.verwijderClustering("windmolens", false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(taakService).annuleer(1L);
  }

  @Test
  void verwijderClustering_annuleertBezigeTaakEnCanceltWorker() {
    var dto = maakDto(1L, "bezig", 10, null);
    when(taakService.geefTaak("windmolens")).thenReturn(Optional.of(dto));

    var response = controller.verwijderClustering("windmolens", false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(taakService).annuleer(1L);
    verify(clusteringWorker).annuleerTaak(1L);
  }

  // --- Hulpmethoden ---

  private ClusteringTaakDto maakDto(Long id, String status,
      int aantalBezwaren, Integer aantalKernbezwaren) {
    return new ClusteringTaakDto(
        id, "windmolens", status, aantalBezwaren,
        aantalKernbezwaren, Instant.now(), null, null, null);
  }
}
