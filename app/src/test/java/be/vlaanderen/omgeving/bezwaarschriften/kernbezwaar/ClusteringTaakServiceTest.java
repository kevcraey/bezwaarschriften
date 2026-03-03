package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
  private ThemaRepository themaRepository;

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
        taakRepository, themaRepository, kernbezwaarRepository,
        antwoordRepository, bezwaarRepository, notificatie, 2);
  }

  @Test
  void indienen_maaktTaakAanMetStatusWachtend() {
    when(taakRepository.findByProjectNaamAndCategorie("windmolens", "Geluid"))
        .thenReturn(Optional.empty());
    when(bezwaarRepository.countByProjectNaamAndCategorie("windmolens", "Geluid"))
        .thenReturn(5);
    when(taakRepository.save(any())).thenAnswer(inv -> {
      var taak = (ClusteringTaak) inv.getArgument(0);
      taak.setId(1L);
      return taak;
    });

    var dto = service.indienen("windmolens", "Geluid");

    assertThat(dto.status()).isEqualTo("wachtend");
    assertThat(dto.projectNaam()).isEqualTo("windmolens");
    assertThat(dto.categorie()).isEqualTo("Geluid");
    assertThat(dto.aantalBezwaren()).isEqualTo(5);
    assertThat(dto.aantalKernbezwaren()).isNull();

    var captor = ArgumentCaptor.forClass(ClusteringTaak.class);
    verify(taakRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ClusteringTaakStatus.WACHTEND);
    assertThat(captor.getValue().getAangemaaktOp()).isNotNull();

    verify(notificatie).clusteringTaakGewijzigd(any(ClusteringTaakDto.class));
  }

  @Test
  void indienen_verwijdertBestaandeTaakEnThema() {
    var bestaandeTaak = maakTaak(10L, "windmolens", "Geluid", ClusteringTaakStatus.KLAAR);
    when(taakRepository.findByProjectNaamAndCategorie("windmolens", "Geluid"))
        .thenReturn(Optional.of(bestaandeTaak));
    when(bezwaarRepository.countByProjectNaamAndCategorie("windmolens", "Geluid"))
        .thenReturn(3);
    when(taakRepository.save(any())).thenAnswer(inv -> {
      var taak = (ClusteringTaak) inv.getArgument(0);
      taak.setId(11L);
      return taak;
    });

    var dto = service.indienen("windmolens", "Geluid");

    verify(taakRepository).delete(bestaandeTaak);
    verify(themaRepository).deleteByProjectNaamAndNaam("windmolens", "Geluid");
    assertThat(dto.status()).isEqualTo("wachtend");
    assertThat(dto.id()).isEqualTo(11L);
  }

  @Test
  void annuleer_zetStatusOpGeannuleerd() {
    var taak = maakTaak(1L, "windmolens", "Geluid", ClusteringTaakStatus.WACHTEND);
    when(taakRepository.findById(1L)).thenReturn(Optional.of(taak));
    when(bezwaarRepository.countByProjectNaamAndCategorie("windmolens", "Geluid"))
        .thenReturn(5);

    var resultaat = service.annuleer(1L);

    assertThat(resultaat).isTrue();
    assertThat(taak.getStatus()).isEqualTo(ClusteringTaakStatus.GEANNULEERD);
    verify(taakRepository).save(taak);
    verify(notificatie).clusteringTaakGewijzigd(any(ClusteringTaakDto.class));
  }

  @Test
  void annuleer_retourneertFalseVoorKlareTaak() {
    var taak = maakTaak(1L, "windmolens", "Geluid", ClusteringTaakStatus.KLAAR);
    when(taakRepository.findById(1L)).thenReturn(Optional.of(taak));

    var resultaat = service.annuleer(1L);

    assertThat(resultaat).isFalse();
    verify(taakRepository, never()).save(any());
  }

  @Test
  void verwijderClustering_vraagBevestigingBijAntwoorden() {
    var thema = new ThemaEntiteit();
    thema.setId(100L);
    thema.setProjectNaam("windmolens");
    thema.setNaam("Geluid");
    when(themaRepository.findByProjectNaamAndNaam("windmolens", "Geluid"))
        .thenReturn(Optional.of(thema));

    var kern1 = new KernbezwaarEntiteit();
    kern1.setId(200L);
    kern1.setThemaId(100L);
    var kern2 = new KernbezwaarEntiteit();
    kern2.setId(201L);
    kern2.setThemaId(100L);
    when(kernbezwaarRepository.findByThemaId(100L)).thenReturn(List.of(kern1, kern2));
    when(antwoordRepository.countByKernbezwaarIdIn(List.of(200L, 201L))).thenReturn(2L);

    var resultaat = service.verwijderClustering("windmolens", "Geluid", false);

    assertThat(resultaat.verwijderd()).isFalse();
    assertThat(resultaat.bevestigingNodig()).isTrue();
    assertThat(resultaat.aantalAntwoorden()).isEqualTo(2);
    verify(themaRepository, never()).deleteByProjectNaamAndNaam(any(), any());
  }

  @Test
  void verwijderClustering_verwijdertBijBevestiging() {
    var thema = new ThemaEntiteit();
    thema.setId(100L);
    thema.setProjectNaam("windmolens");
    thema.setNaam("Geluid");
    when(themaRepository.findByProjectNaamAndNaam("windmolens", "Geluid"))
        .thenReturn(Optional.of(thema));

    var kern = new KernbezwaarEntiteit();
    kern.setId(200L);
    kern.setThemaId(100L);
    when(kernbezwaarRepository.findByThemaId(100L)).thenReturn(List.of(kern));

    var resultaat = service.verwijderClustering("windmolens", "Geluid", true);

    assertThat(resultaat.verwijderd()).isTrue();
    assertThat(resultaat.bevestigingNodig()).isFalse();
    verify(themaRepository).deleteByProjectNaamAndNaam("windmolens", "Geluid");
    verify(taakRepository).findByProjectNaamAndCategorie("windmolens", "Geluid");
  }

  @Test
  void verwijderClustering_verwijdertDirectZonderAntwoorden() {
    var thema = new ThemaEntiteit();
    thema.setId(100L);
    thema.setProjectNaam("windmolens");
    thema.setNaam("Geluid");
    when(themaRepository.findByProjectNaamAndNaam("windmolens", "Geluid"))
        .thenReturn(Optional.of(thema));

    when(kernbezwaarRepository.findByThemaId(100L)).thenReturn(List.of());

    var resultaat = service.verwijderClustering("windmolens", "Geluid", false);

    assertThat(resultaat.verwijderd()).isTrue();
    verify(themaRepository).deleteByProjectNaamAndNaam("windmolens", "Geluid");
  }

  @Test
  void verwijderClustering_retourneertVerwijderdAlsGeenThema() {
    when(themaRepository.findByProjectNaamAndNaam("windmolens", "Geluid"))
        .thenReturn(Optional.empty());

    var resultaat = service.verwijderClustering("windmolens", "Geluid", false);

    assertThat(resultaat.verwijderd()).isTrue();
  }

  @Test
  void markeerKlaar_zetStatusOpKlaar() {
    var taak = maakTaak(1L, "windmolens", "Geluid", ClusteringTaakStatus.BEZIG);
    when(taakRepository.findById(1L)).thenReturn(Optional.of(taak));

    var thema = new ThemaEntiteit();
    thema.setId(100L);
    thema.setNaam("Geluid");
    when(themaRepository.findByProjectNaamAndNaam("windmolens", "Geluid"))
        .thenReturn(Optional.of(thema));
    when(kernbezwaarRepository.countByThemaId(100L)).thenReturn(3);
    when(bezwaarRepository.countByProjectNaamAndCategorie("windmolens", "Geluid"))
        .thenReturn(10);

    service.markeerKlaar(1L);

    assertThat(taak.getStatus()).isEqualTo(ClusteringTaakStatus.KLAAR);
    assertThat(taak.getVerwerkingVoltooidOp()).isNotNull();
    verify(taakRepository).save(taak);
    verify(notificatie).clusteringTaakGewijzigd(any(ClusteringTaakDto.class));
  }

  @Test
  void markeerFout_zetStatusOpFout() {
    var taak = maakTaak(1L, "windmolens", "Geluid", ClusteringTaakStatus.BEZIG);
    when(taakRepository.findById(1L)).thenReturn(Optional.of(taak));
    when(bezwaarRepository.countByProjectNaamAndCategorie("windmolens", "Geluid"))
        .thenReturn(5);

    service.markeerFout(1L, "Embedding-service onbeschikbaar");

    assertThat(taak.getStatus()).isEqualTo(ClusteringTaakStatus.FOUT);
    assertThat(taak.getFoutmelding()).isEqualTo("Embedding-service onbeschikbaar");
    assertThat(taak.getVerwerkingVoltooidOp()).isNotNull();
    verify(taakRepository).save(taak);
    verify(notificatie).clusteringTaakGewijzigd(any(ClusteringTaakDto.class));
  }

  @Test
  void isGeannuleerd_returnsTrueWhenGeannuleerd() {
    var taak = maakTaak(1L, "windmolens", "Geluid", ClusteringTaakStatus.GEANNULEERD);
    when(taakRepository.findById(1L)).thenReturn(Optional.of(taak));

    assertThat(service.isGeannuleerd(1L)).isTrue();
  }

  @Test
  void isGeannuleerd_returnsFalseWhenBezig() {
    var taak = maakTaak(1L, "windmolens", "Geluid", ClusteringTaakStatus.BEZIG);
    when(taakRepository.findById(1L)).thenReturn(Optional.of(taak));

    assertThat(service.isGeannuleerd(1L)).isFalse();
  }

  @Test
  void isGeannuleerd_returnsFalseWhenNotFound() {
    when(taakRepository.findById(99L)).thenReturn(Optional.empty());

    assertThat(service.isGeannuleerd(99L)).isFalse();
  }

  @Test
  void geefTaken_retourneertLijstMetCounts() {
    var taak1 = maakTaak(1L, "windmolens", "Geluid", ClusteringTaakStatus.KLAAR);
    var taak2 = maakTaak(2L, "windmolens", "Mobiliteit", ClusteringTaakStatus.WACHTEND);
    when(taakRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(taak1, taak2));

    when(bezwaarRepository.countByProjectNaamAndCategorie("windmolens", "Geluid"))
        .thenReturn(10);
    when(bezwaarRepository.countByProjectNaamAndCategorie("windmolens", "Mobiliteit"))
        .thenReturn(5);

    var thema = new ThemaEntiteit();
    thema.setId(100L);
    when(themaRepository.findByProjectNaamAndNaam("windmolens", "Geluid"))
        .thenReturn(Optional.of(thema));
    when(kernbezwaarRepository.countByThemaId(100L)).thenReturn(3);

    var taken = service.geefTaken("windmolens");

    assertThat(taken).hasSize(2);
    assertThat(taken.get(0).aantalBezwaren()).isEqualTo(10);
    assertThat(taken.get(0).aantalKernbezwaren()).isEqualTo(3);
    assertThat(taken.get(1).aantalBezwaren()).isEqualTo(5);
    assertThat(taken.get(1).aantalKernbezwaren()).isNull();
  }

  @Test
  void pakOpVoorVerwerking_paktWachtendeTakenOp() {
    var taak1 = maakTaak(1L, "windmolens", "Geluid", ClusteringTaakStatus.WACHTEND);
    var taak2 = maakTaak(2L, "windmolens", "Mobiliteit", ClusteringTaakStatus.WACHTEND);
    when(taakRepository.countByStatus(ClusteringTaakStatus.BEZIG)).thenReturn(0);
    when(taakRepository.findByStatusOrderByAangemaaktOpAsc(ClusteringTaakStatus.WACHTEND))
        .thenReturn(List.of(taak1, taak2));
    when(taakRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(bezwaarRepository.countByProjectNaamAndCategorie(any(), any())).thenReturn(5);

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

  private ClusteringTaak maakTaak(Long id, String projectNaam, String categorie,
      ClusteringTaakStatus status) {
    var taak = new ClusteringTaak();
    taak.setId(id);
    taak.setProjectNaam(projectNaam);
    taak.setCategorie(categorie);
    taak.setStatus(status);
    taak.setAangemaaktOp(Instant.now());
    return taak;
  }
}
