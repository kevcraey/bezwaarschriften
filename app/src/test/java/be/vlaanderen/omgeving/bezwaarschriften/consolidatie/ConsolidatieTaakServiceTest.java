package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocument;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocumentRepository;
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
class ConsolidatieTaakServiceTest {

  @Mock
  private ConsolidatieTaakRepository repository;

  @Mock
  private BezwaarDocumentRepository documentRepository;

  @Mock
  private ConsolidatieNotificatie notificatie;

  private ConsolidatieTaakService service;

  @BeforeEach
  void setUp() {
    service = new ConsolidatieTaakService(repository, documentRepository, notificatie, 3, 3);
  }

  @Test
  void dienTakenInMetStatusWachtend() {
    var document = maakDocument(10L, "windmolens", "bezwaar-001.txt");
    when(documentRepository.findByProjectNaamAndBestandsnaam("windmolens", "bezwaar-001.txt"))
        .thenReturn(Optional.of(document));
    when(repository.save(any())).thenAnswer(i -> {
      var t = i.getArgument(0, ConsolidatieTaak.class);
      t.setId(1L);
      return t;
    });

    var resultaat = service.indienen("windmolens", List.of("bezwaar-001.txt"));

    assertThat(resultaat).hasSize(1);
    var captor = ArgumentCaptor.forClass(ConsolidatieTaak.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ConsolidatieTaakStatus.WACHTEND);
    assertThat(captor.getValue().getDocumentId()).isEqualTo(10L);
    verify(notificatie).consolidatieTaakGewijzigd(any(ConsolidatieTaakDto.class));
  }

  @Test
  void geefTakenVoorProject() {
    var taak = maakTaak(1L, 10L, ConsolidatieTaakStatus.WACHTEND);
    var document = maakDocument(10L, "windmolens", "bezwaar-001.txt");
    when(repository.findByProjectNaam("windmolens")).thenReturn(List.of(taak));
    when(documentRepository.findAllById(List.of(10L))).thenReturn(List.of(document));

    var resultaat = service.geefTaken("windmolens");

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).bestandsnaam()).isEqualTo("bezwaar-001.txt");
  }

  @Test
  void pakOpVoorVerwerkingZetStatusOpBezig() {
    when(repository.countByStatus(ConsolidatieTaakStatus.BEZIG)).thenReturn(0L);
    var taak = maakTaak(1L, 10L, ConsolidatieTaakStatus.WACHTEND);
    var document = maakDocument(10L, "windmolens", "bezwaar-001.txt");
    when(repository.findByStatusOrderByAangemaaktOpAsc(ConsolidatieTaakStatus.WACHTEND))
        .thenReturn(List.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(documentRepository.findAllById(List.of(10L))).thenReturn(List.of(document));

    var resultaat = service.pakOpVoorVerwerking();

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).getStatus()).isEqualTo(ConsolidatieTaakStatus.BEZIG);
    assertThat(resultaat.get(0).getVerwerkingGestartOp()).isNotNull();
  }

  @Test
  void pakOpRespecteerMaxConcurrent() {
    when(repository.countByStatus(ConsolidatieTaakStatus.BEZIG)).thenReturn(3L);

    var resultaat = service.pakOpVoorVerwerking();

    assertThat(resultaat).isEmpty();
  }

  @Test
  void markeerKlaarZetStatus() {
    var taak = maakTaak(1L, 10L, ConsolidatieTaakStatus.BEZIG);
    var document = maakDocument(10L, "windmolens", "bezwaar-001.txt");
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(documentRepository.findById(10L)).thenReturn(Optional.of(document));

    service.markeerKlaar(1L);

    assertThat(taak.getStatus()).isEqualTo(ConsolidatieTaakStatus.KLAAR);
    assertThat(taak.getAfgerondOp()).isNotNull();
    verify(notificatie).consolidatieTaakGewijzigd(any(ConsolidatieTaakDto.class));
  }

  @Test
  void markeerFoutMetRetryZetTerugNaarWachtend() {
    var taak = maakTaak(1L, 10L, ConsolidatieTaakStatus.BEZIG);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    var document = maakDocument(10L, "windmolens", "bezwaar-001.txt");
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(documentRepository.findById(10L)).thenReturn(Optional.of(document));

    service.markeerFout(1L, "Timeout");

    assertThat(taak.getStatus()).isEqualTo(ConsolidatieTaakStatus.WACHTEND);
    assertThat(taak.getAantalPogingen()).isEqualTo(1);
  }

  @Test
  void markeerFoutDefinitiefBijMaxPogingen() {
    var taak = maakTaak(1L, 10L, ConsolidatieTaakStatus.BEZIG);
    taak.setAantalPogingen(2);
    taak.setMaxPogingen(3);
    var document = maakDocument(10L, "windmolens", "bezwaar-001.txt");
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(documentRepository.findById(10L)).thenReturn(Optional.of(document));

    service.markeerFout(1L, "Definitieve fout");

    assertThat(taak.getStatus()).isEqualTo(ConsolidatieTaakStatus.FOUT);
    assertThat(taak.getAantalPogingen()).isEqualTo(3);
    assertThat(taak.getFoutmelding()).isEqualTo("Definitieve fout");
  }

  @Test
  void verwijderTaakVerwijdertUitRepository() {
    var taak = maakTaak(1L, 10L, ConsolidatieTaakStatus.WACHTEND);
    var document = maakDocument(10L, "windmolens", "bezwaar-001.txt");
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(documentRepository.findById(10L)).thenReturn(Optional.of(document));

    service.verwijderTaak("windmolens", 1L);

    verify(repository).delete(taak);
  }

  @Test
  void verwijderTaakGooitExceptieBijOnbekendeTaak() {
    when(repository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.verwijderTaak("windmolens", 999L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void verwijderTaakGooitExceptieBijVerkeerdeProject() {
    var taak = maakTaak(1L, 10L, ConsolidatieTaakStatus.BEZIG);
    var document = maakDocument(10L, "snelweg", "bezwaar-001.txt");
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(documentRepository.findById(10L)).thenReturn(Optional.of(document));

    assertThatThrownBy(() -> service.verwijderTaak("windmolens", 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ConsolidatieTaak maakTaak(Long id, Long documentId, ConsolidatieTaakStatus status) {
    var taak = new ConsolidatieTaak();
    taak.setId(id);
    taak.setDocumentId(documentId);
    taak.setStatus(status);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    taak.setAangemaaktOp(Instant.now());
    return taak;
  }

  private BezwaarDocument maakDocument(Long id, String projectNaam, String bestandsnaam) {
    var document = new BezwaarDocument();
    document.setId(id);
    document.setProjectNaam(projectNaam);
    document.setBestandsnaam(bestandsnaam);
    return document;
  }
}
