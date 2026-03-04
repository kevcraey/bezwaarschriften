package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
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
  void geefOverzicht_delegeertNaarService() {
    var statussen = List.of(
        new ClusteringTaakService.CategorieStatus(
            "Geluid", "klaar", 1L, 10, 3, null),
        new ClusteringTaakService.CategorieStatus(
            "Mobiliteit", "bezig", 2L, 5, null, null),
        new ClusteringTaakService.CategorieStatus(
            "Natuur", "todo", null, 7, null, null));
    when(taakService.geefCategorieOverzicht("windmolens")).thenReturn(statussen);

    var response = controller.geefOverzicht("windmolens");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var items = response.getBody().categorieen();
    assertThat(items).hasSize(3);

    var geluid = items.stream()
        .filter(i -> i.categorie().equals("Geluid")).findFirst().get();
    assertThat(geluid.status()).isEqualTo("klaar");
    assertThat(geluid.taakId()).isEqualTo(1L);
    assertThat(geluid.aantalKernbezwaren()).isEqualTo(3);

    var natuur = items.stream()
        .filter(i -> i.categorie().equals("Natuur")).findFirst().get();
    assertThat(natuur.status()).isEqualTo("todo");
    assertThat(natuur.taakId()).isNull();
    assertThat(natuur.aantalBezwaren()).isEqualTo(7);
  }

  @Test
  void startCategorie_retourneert202() {
    var dto = maakDto(1L, "Geluid", "wachtend", 10, null);
    when(taakService.indienen("windmolens", "Geluid")).thenReturn(dto);

    var response = controller.startCategorie("windmolens", "Geluid");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody().categorie()).isEqualTo("Geluid");
    verify(taakService).indienen("windmolens", "Geluid");
  }

  @Test
  void startAlleCategorieen_delegeertNaarService() {
    var dtoMobiliteit = maakDto(2L, "Mobiliteit", "wachtend", 5, null);
    var dtoNatuur = maakDto(3L, "Natuur", "wachtend", 7, null);
    when(taakService.indienenAlleNietActieve("windmolens"))
        .thenReturn(List.of(dtoMobiliteit, dtoNatuur));

    var response = controller.startAlleCategorieen("windmolens");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).hasSize(2);
    verify(taakService).indienenAlleNietActieve("windmolens");
  }

  @Test
  void verwijderCategorie_annuleertBezigeTaak() {
    var dto = maakDto(1L, "Geluid", "bezig", 10, null);
    when(taakService.geefTaken("windmolens")).thenReturn(List.of(dto));
    when(taakService.annuleer(1L)).thenReturn(true);

    var response = controller.verwijderCategorie("windmolens", "Geluid", false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(taakService).annuleer(1L);
    verify(clusteringWorker).annuleerTaak(1L);
  }

  @Test
  void verwijderCategorie_annuleertWachtendeTaakZonderWorker() {
    var dto = maakDto(1L, "Geluid", "wachtend", 10, null);
    when(taakService.geefTaken("windmolens")).thenReturn(List.of(dto));
    when(taakService.annuleer(1L)).thenReturn(true);

    var response = controller.verwijderCategorie("windmolens", "Geluid", false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(taakService).annuleer(1L);
    verify(clusteringWorker, never()).annuleerTaak(1L);
  }

  @Test
  void verwijderCategorie_retourneert409BijAntwoorden() {
    var dto = maakDto(1L, "Geluid", "klaar", 10, 3);
    when(taakService.geefTaken("windmolens")).thenReturn(List.of(dto));
    when(taakService.verwijderClustering("windmolens", "Geluid", false))
        .thenReturn(VerwijderResultaat.bevestigingVereist(2));

    var response = controller.verwijderCategorie("windmolens", "Geluid", false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void verwijderCategorie_verwijdertBijBevestiging() {
    var dto = maakDto(1L, "Geluid", "klaar", 10, 3);
    when(taakService.geefTaken("windmolens")).thenReturn(List.of(dto));
    when(taakService.verwijderClustering("windmolens", "Geluid", true))
        .thenReturn(VerwijderResultaat.succesvolVerwijderd());

    var response = controller.verwijderCategorie("windmolens", "Geluid", true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(taakService).verwijderClustering("windmolens", "Geluid", true);
  }

  @Test
  void verwijderAlleClusteringen_retourneert200ZonderAntwoorden() {
    when(taakService.verwijderAlleClusteringen("windmolens", false))
        .thenReturn(VerwijderResultaat.succesvolVerwijderd());

    var response = controller.verwijderAlleClusteringen("windmolens", false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(taakService).verwijderAlleClusteringen("windmolens", false);
  }

  @Test
  void verwijderAlleClusteringen_retourneert409BijAntwoorden() {
    when(taakService.verwijderAlleClusteringen("windmolens", false))
        .thenReturn(VerwijderResultaat.bevestigingVereist(5));

    var response = controller.verwijderAlleClusteringen("windmolens", false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void verwijderAlleClusteringen_verwijdertBijBevestiging() {
    when(taakService.verwijderAlleClusteringen("windmolens", true))
        .thenReturn(VerwijderResultaat.succesvolVerwijderd());

    var response = controller.verwijderAlleClusteringen("windmolens", true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(taakService).verwijderAlleClusteringen("windmolens", true);
  }

  // --- Hulpmethoden ---

  private ClusteringTaakDto maakDto(Long id, String categorie, String status,
      int aantalBezwaren, Integer aantalKernbezwaren) {
    return new ClusteringTaakDto(
        id, "windmolens", categorie, status, aantalBezwaren,
        aantalKernbezwaren, Instant.now(), null, null, null);
  }
}
