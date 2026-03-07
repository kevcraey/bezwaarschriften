package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClusteringTaakServiceTest {

  @Mock
  private ClusteringTaakRepository taakRepository;

  @Mock
  private KernbezwaarRepository kernbezwaarRepository;

  @Mock
  private KernbezwaarAntwoordRepository antwoordRepository;

  @Mock
  private GeextraheerdBezwaarRepository bezwaarRepository;

  @Mock
  private ClusteringNotificatie notificatie;

  private ClusteringTaakService service;

  @BeforeEach
  void setUp() {
    service = new ClusteringTaakService(
        taakRepository, kernbezwaarRepository,
        antwoordRepository, bezwaarRepository, notificatie, 2);
  }

  @Test
  void indienen_maaktTaakAanMetStatusWachtend() {
    when(taakRepository.findByProjectNaam("windmolens"))
        .thenReturn(Optional.empty());
    when(bezwaarRepository.countByProjectNaam("windmolens"))
        .thenReturn(5);
    when(taakRepository.save(any())).thenAnswer(inv -> {
      var taak = (ClusteringTaak) inv.getArgument(0);
      taak.setId(1L);
      return taak;
    });

    var dto = service.indienen("windmolens", true);

    assertThat(dto.status()).isEqualTo("wachtend");
    assertThat(dto.projectNaam()).isEqualTo("windmolens");
    assertThat(dto.aantalBezwaren()).isEqualTo(5);
    assertThat(dto.aantalKernbezwaren()).isNull();
    assertThat(dto.deduplicatieVoorClustering()).isTrue();

    var captor = ArgumentCaptor.forClass(ClusteringTaak.class);
    verify(taakRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ClusteringTaakStatus.WACHTEND);
    assertThat(captor.getValue().isDeduplicatieVoorClustering()).isTrue();
    assertThat(captor.getValue().getAangemaaktOp()).isNotNull();

    verify(notificatie).clusteringTaakGewijzigd(any(ClusteringTaakDto.class));
  }

  @Test
  void indienen_verwijdertBestaandeTaak() {
    var bestaandeTaak = maakTaak(10L, "windmolens", ClusteringTaakStatus.KLAAR);
    when(taakRepository.findByProjectNaam("windmolens"))
        .thenReturn(Optional.of(bestaandeTaak));
    when(bezwaarRepository.countByProjectNaam("windmolens"))
        .thenReturn(3);
    when(taakRepository.save(any())).thenAnswer(inv -> {
      var taak = (ClusteringTaak) inv.getArgument(0);
      taak.setId(11L);
      return taak;
    });

    var dto = service.indienen("windmolens", true);

    var inOrder = org.mockito.Mockito.inOrder(taakRepository);
    inOrder.verify(taakRepository).delete(bestaandeTaak);
    inOrder.verify(taakRepository).flush();
    assertThat(dto.status()).isEqualTo("wachtend");
    assertThat(dto.id()).isEqualTo(11L);
  }

  @Test
  void annuleer_verwijdertWachtendeTaak() {
    var taak = maakTaak(1L, "windmolens", ClusteringTaakStatus.WACHTEND);
    when(taakRepository.findById(1L)).thenReturn(Optional.of(taak));

    var resultaat = service.annuleer(1L);

    assertThat(resultaat).isTrue();
    verify(taakRepository).delete(taak);
    verify(taakRepository, never()).save(any());
    verify(notificatie, never()).clusteringTaakGewijzigd(any(ClusteringTaakDto.class));
  }

  @Test
  void annuleer_verwijdertBezigeTaak() {
    var taak = maakTaak(1L, "windmolens", ClusteringTaakStatus.BEZIG);
    when(taakRepository.findById(1L)).thenReturn(Optional.of(taak));

    var resultaat = service.annuleer(1L);

    assertThat(resultaat).isTrue();
    verify(taakRepository).delete(taak);
    verify(taakRepository, never()).save(any());
  }

  @Test
  void annuleer_retourneertFalseVoorKlareTaak() {
    var taak = maakTaak(1L, "windmolens", ClusteringTaakStatus.KLAAR);
    when(taakRepository.findById(1L)).thenReturn(Optional.of(taak));

    var resultaat = service.annuleer(1L);

    assertThat(resultaat).isFalse();
    verify(taakRepository, never()).delete(any(ClusteringTaak.class));
    verify(taakRepository, never()).save(any());
  }

  @Test
  void annuleer_retourneertFalseVoorFouteTaak() {
    var taak = maakTaak(1L, "windmolens", ClusteringTaakStatus.FOUT);
    when(taakRepository.findById(1L)).thenReturn(Optional.of(taak));

    var resultaat = service.annuleer(1L);

    assertThat(resultaat).isFalse();
    verify(taakRepository, never()).delete(any(ClusteringTaak.class));
  }

  @Test
  void annuleer_retourneertFalseVoorOnbekendeTaak() {
    when(taakRepository.findById(99L)).thenReturn(Optional.empty());

    var resultaat = service.annuleer(99L);

    assertThat(resultaat).isFalse();
    verify(taakRepository, never()).delete(any(ClusteringTaak.class));
  }

  @Test
  void verwijderClustering_vraagBevestigingBijAntwoorden() {
    var kern1 = new KernbezwaarEntiteit();
    kern1.setId(200L);
    kern1.setProjectNaam("windmolens");
    var kern2 = new KernbezwaarEntiteit();
    kern2.setId(201L);
    kern2.setProjectNaam("windmolens");
    when(kernbezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(kern1, kern2));
    when(antwoordRepository.countByKernbezwaarIdIn(List.of(200L, 201L))).thenReturn(2L);

    var resultaat = service.verwijderClustering("windmolens", false);

    assertThat(resultaat.verwijderd()).isFalse();
    assertThat(resultaat.bevestigingNodig()).isTrue();
    assertThat(resultaat.aantalAntwoorden()).isEqualTo(2);
    verify(kernbezwaarRepository, never()).deleteAll(any());
  }

  @Test
  void verwijderClustering_verwijdertBijBevestiging() {
    var kern = new KernbezwaarEntiteit();
    kern.setId(200L);
    kern.setProjectNaam("windmolens");
    when(kernbezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(kern));

    var resultaat = service.verwijderClustering("windmolens", true);

    assertThat(resultaat.verwijderd()).isTrue();
    assertThat(resultaat.bevestigingNodig()).isFalse();
    verify(kernbezwaarRepository).deleteAll(List.of(kern));
    verify(taakRepository).deleteByProjectNaam("windmolens");
  }

  @Test
  void verwijderClustering_verwijdertDirectZonderAntwoorden() {
    when(kernbezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of());

    var resultaat = service.verwijderClustering("windmolens", false);

    assertThat(resultaat.verwijderd()).isTrue();
    verify(taakRepository).deleteByProjectNaam("windmolens");
  }

  @Test
  void markeerKlaar_zetStatusOpKlaar() {
    var taak = maakTaak(1L, "windmolens", ClusteringTaakStatus.BEZIG);
    when(taakRepository.findById(1L)).thenReturn(Optional.of(taak));
    when(kernbezwaarRepository.countByProjectNaam("windmolens")).thenReturn(3);
    when(bezwaarRepository.countByProjectNaam("windmolens"))
        .thenReturn(10);

    service.markeerKlaar(1L);

    assertThat(taak.getStatus()).isEqualTo(ClusteringTaakStatus.KLAAR);
    assertThat(taak.getVerwerkingVoltooidOp()).isNotNull();
    verify(taakRepository).save(taak);
    verify(notificatie).clusteringTaakGewijzigd(any(ClusteringTaakDto.class));
  }

  @Test
  void markeerFout_zetStatusOpFout() {
    var taak = maakTaak(1L, "windmolens", ClusteringTaakStatus.BEZIG);
    when(taakRepository.findById(1L)).thenReturn(Optional.of(taak));
    when(bezwaarRepository.countByProjectNaam("windmolens"))
        .thenReturn(5);

    service.markeerFout(1L, "Embedding-service onbeschikbaar");

    assertThat(taak.getStatus()).isEqualTo(ClusteringTaakStatus.FOUT);
    assertThat(taak.getFoutmelding()).isEqualTo("Embedding-service onbeschikbaar");
    assertThat(taak.getVerwerkingVoltooidOp()).isNotNull();
    verify(taakRepository).save(taak);
    verify(notificatie).clusteringTaakGewijzigd(any(ClusteringTaakDto.class));
  }

  @Test
  void isGeannuleerd_returnsTrueWhenNietGevonden() {
    when(taakRepository.existsById(99L)).thenReturn(false);

    assertThat(service.isGeannuleerd(99L)).isTrue();
  }

  @Test
  void isGeannuleerd_returnsFalseWhenBestaat() {
    when(taakRepository.existsById(1L)).thenReturn(true);

    assertThat(service.isGeannuleerd(1L)).isFalse();
  }

  @Test
  void geefTaak_retourneertOptionalMetCounts() {
    var taak = maakTaak(1L, "windmolens", ClusteringTaakStatus.KLAAR);
    when(taakRepository.findByProjectNaam("windmolens"))
        .thenReturn(Optional.of(taak));
    when(bezwaarRepository.countByProjectNaam("windmolens"))
        .thenReturn(10);
    when(kernbezwaarRepository.countByProjectNaam("windmolens")).thenReturn(3);

    var dto = service.geefTaak("windmolens");

    assertThat(dto).isPresent();
    assertThat(dto.get().aantalBezwaren()).isEqualTo(10);
    assertThat(dto.get().aantalKernbezwaren()).isEqualTo(3);
  }

  @Test
  void geefTaak_retourneertLeegAlsGeenTaak() {
    when(taakRepository.findByProjectNaam("windmolens"))
        .thenReturn(Optional.empty());

    var dto = service.geefTaak("windmolens");

    assertThat(dto).isEmpty();
  }

  @Test
  void pakOpVoorVerwerking_paktWachtendeTakenOp() {
    var taak1 = maakTaak(1L, "windmolens", ClusteringTaakStatus.WACHTEND);
    var taak2 = maakTaak(2L, "snelweg", ClusteringTaakStatus.WACHTEND);
    when(taakRepository.countByStatus(ClusteringTaakStatus.BEZIG)).thenReturn(0);
    when(taakRepository.findByStatusOrderByAangemaaktOpAsc(ClusteringTaakStatus.WACHTEND))
        .thenReturn(List.of(taak1, taak2));
    when(taakRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(bezwaarRepository.countByProjectNaam(any())).thenReturn(5);

    var resultaat = service.pakOpVoorVerwerking();

    assertThat(resultaat).hasSize(2);
    assertThat(resultaat.get(0).getStatus()).isEqualTo(ClusteringTaakStatus.BEZIG);
    assertThat(resultaat.get(0).getVerwerkingGestartOp()).isNotNull();
    assertThat(resultaat.get(1).getStatus()).isEqualTo(ClusteringTaakStatus.BEZIG);
  }

  @Test
  void pakOpVoorVerwerking_respecteertMaxConcurrent() {
    when(taakRepository.countByStatus(ClusteringTaakStatus.BEZIG)).thenReturn(2);

    var resultaat = service.pakOpVoorVerwerking();

    assertThat(resultaat).isEmpty();
    verify(taakRepository, never()).findByStatusOrderByAangemaaktOpAsc(any());
  }

  // --- Hulpmethoden ---

  private ClusteringTaak maakTaak(Long id, String projectNaam,
      ClusteringTaakStatus status) {
    var taak = new ClusteringTaak();
    taak.setId(id);
    taak.setProjectNaam(projectNaam);
    taak.setStatus(status);
    taak.setAangemaaktOp(Instant.now());
    return taak;
  }
}
