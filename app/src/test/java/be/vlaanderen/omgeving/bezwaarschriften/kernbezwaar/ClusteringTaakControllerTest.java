package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarRepository;
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

  @Mock
  private GeextraheerdBezwaarRepository bezwaarRepository;

  private ClusteringTaakController controller;

  @BeforeEach
  void setUp() {
    controller = new ClusteringTaakController(
        taakService, clusteringWorker, bezwaarRepository);
  }

  @Test
  void geefOverzicht_toontAlleCategorienMetStatus() {
    when(bezwaarRepository.findDistinctCategorienByProjectNaam("windmolens"))
        .thenReturn(List.of("Geluid", "Mobiliteit", "Natuur"));

    var taakGeluid = maakDto(1L, "Geluid", "klaar", 10, 3);
    var taakMobiliteit = maakDto(2L, "Mobiliteit", "bezig", 5, null);
    when(taakService.geefTaken("windmolens"))
        .thenReturn(List.of(taakGeluid, taakMobiliteit));
    when(bezwaarRepository.countByProjectNaamAndCategorie("windmolens", "Natuur"))
        .thenReturn(7);

    var response = controller.geefOverzicht("windmolens");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var items = response.getBody().categorieen();
    assertThat(items).hasSize(3);

    var geluid = items.stream().filter(i -> i.categorie().equals("Geluid")).findFirst().get();
    assertThat(geluid.status()).isEqualTo("klaar");
    assertThat(geluid.taakId()).isEqualTo(1L);
    assertThat(geluid.aantalKernbezwaren()).isEqualTo(3);

    var mobiliteit = items.stream().filter(i -> i.categorie().equals("Mobiliteit")).findFirst().get();
    assertThat(mobiliteit.status()).isEqualTo("bezig");

    var natuur = items.stream().filter(i -> i.categorie().equals("Natuur")).findFirst().get();
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
  void startAlleCategorieen_startEnkelNietActieve() {
    when(bezwaarRepository.findDistinctCategorienByProjectNaam("windmolens"))
        .thenReturn(List.of("Geluid", "Mobiliteit", "Natuur"));

    var taakGeluid = maakDto(1L, "Geluid", "klaar", 10, 3);
    when(taakService.geefTaken("windmolens")).thenReturn(List.of(taakGeluid));

    var dtoMobiliteit = maakDto(2L, "Mobiliteit", "wachtend", 5, null);
    var dtoNatuur = maakDto(3L, "Natuur", "wachtend", 7, null);
    when(taakService.indienen("windmolens", "Mobiliteit")).thenReturn(dtoMobiliteit);
    when(taakService.indienen("windmolens", "Natuur")).thenReturn(dtoNatuur);

    var response = controller.startAlleCategorieen("windmolens");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).hasSize(2);
    verify(taakService, never()).indienen("windmolens", "Geluid");
    verify(taakService).indienen("windmolens", "Mobiliteit");
    verify(taakService).indienen("windmolens", "Natuur");
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

  // --- Hulpmethoden ---

  private ClusteringTaakDto maakDto(Long id, String categorie, String status,
      int aantalBezwaren, Integer aantalKernbezwaren) {
    return new ClusteringTaakDto(
        id, "windmolens", categorie, status, aantalBezwaren,
        aantalKernbezwaren, Instant.now(), null, null, null);
  }
}
