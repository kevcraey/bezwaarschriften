package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class ExtractieTaakServiceTest {

  @Mock
  private ExtractieTaakRepository repository;

  @Mock
  private ExtractieNotificatie notificatie;

  private ExtractieTaakService service;

  @BeforeEach
  void setUp() {
    service = new ExtractieTaakService(repository, notificatie, 3);
  }

  @Test
  void dienTakenInMetStatusWachtend() {
    when(repository.save(any())).thenAnswer(i -> {
      var t = i.getArgument(0, ExtractieTaak.class);
      t.setId(1L);
      return t;
    });

    var resultaat = service.indienen("windmolens", List.of("bezwaar-001.txt", "bezwaar-002.txt"));

    assertThat(resultaat).hasSize(2);
    var captor = ArgumentCaptor.forClass(ExtractieTaak.class);
    verify(repository, times(2)).save(captor.capture());
    captor.getAllValues().forEach(taak -> {
      assertThat(taak.getStatus()).isEqualTo(ExtractieTaakStatus.WACHTEND);
      assertThat(taak.getProjectNaam()).isEqualTo("windmolens");
      assertThat(taak.getAantalPogingen()).isZero();
    });
    verify(notificatie, times(2)).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void geefTakenVoorProject() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.WACHTEND);
    when(repository.findByProjectNaam("windmolens")).thenReturn(List.of(taak));

    var resultaat = service.geefTaken("windmolens");

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).projectNaam()).isEqualTo("windmolens");
    assertThat(resultaat.get(0).bestandsnaam()).isEqualTo("bezwaar-001.txt");
    verify(repository).findByProjectNaam("windmolens");
  }

  @Test
  void pakOpVoorVerwerkingZetStatusOpBezig() {
    when(repository.countByStatus(ExtractieTaakStatus.BEZIG)).thenReturn(0L);
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.WACHTEND);
    when(repository.findByStatusOrderByAangemaaktOpAsc(ExtractieTaakStatus.WACHTEND))
        .thenReturn(List.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    var resultaat = service.pakOpVoorVerwerking();

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).getStatus()).isEqualTo(ExtractieTaakStatus.BEZIG);
    assertThat(resultaat.get(0).getVerwerkingGestartOp()).isNotNull();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void pakOpRespecteerMaxConcurrent() {
    when(repository.countByStatus(ExtractieTaakStatus.BEZIG)).thenReturn(3L);

    var resultaat = service.pakOpVoorVerwerking();

    assertThat(resultaat).isEmpty();
  }

  @Test
  void markeerKlaarZetResultaat() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.markeerKlaar(1L, 500, 7);

    assertThat(taak.getStatus()).isEqualTo(ExtractieTaakStatus.KLAAR);
    assertThat(taak.getAantalWoorden()).isEqualTo(500);
    assertThat(taak.getAantalBezwaren()).isEqualTo(7);
    assertThat(taak.getAfgerondOp()).isNotNull();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void markeerFoutMetRetryZetTerugNaarWachtend() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    taak.setVerwerkingGestartOp(Instant.now());
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.markeerFout(1L, "Timeout bij AI-call");

    assertThat(taak.getStatus()).isEqualTo(ExtractieTaakStatus.WACHTEND);
    assertThat(taak.getAantalPogingen()).isEqualTo(1);
    assertThat(taak.getVerwerkingGestartOp()).isNull();
    assertThat(taak.getFoutmelding()).isNull();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  @Test
  void markeerFoutDefinitiefBijMaxPogingen() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
    taak.setAantalPogingen(2);
    taak.setMaxPogingen(3);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.markeerFout(1L, "Definitieve fout");

    assertThat(taak.getStatus()).isEqualTo(ExtractieTaakStatus.FOUT);
    assertThat(taak.getAantalPogingen()).isEqualTo(3);
    assertThat(taak.getFoutmelding()).isEqualTo("Definitieve fout");
    assertThat(taak.getAfgerondOp()).isNotNull();
    verify(notificatie).taakGewijzigd(any(ExtractieTaakDto.class));
  }

  private ExtractieTaak maakTaak(Long id, String projectNaam, String bestandsnaam,
      ExtractieTaakStatus status) {
    var taak = new ExtractieTaak();
    taak.setId(id);
    taak.setProjectNaam(projectNaam);
    taak.setBestandsnaam(bestandsnaam);
    taak.setStatus(status);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    taak.setAangemaaktOp(Instant.now());
    return taak;
  }
}
