package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;

import be.vlaanderen.omgeving.bezwaarschriften.BaseBezwaarschriftenIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integratietest voor {@link BezwaarDocumentRepository}.
 * Valideert dat de Spring Data JPA queries correct werken met PostgreSQL
 * via Testcontainers.
 */
class BezwaarDocumentRepositoryTest extends BaseBezwaarschriftenIntegrationTest {

  @Autowired
  private BezwaarDocumentRepository repository;

  private BezwaarDocument docA;
  private BezwaarDocument docB;
  private BezwaarDocument docC;

  @BeforeEach
  void setUp() {
    repository.deleteAll();

    docA = maakDocument("project-alpha", "bezwaar-1.pdf",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.GEEN);
    docB = maakDocument("project-alpha", "bezwaar-2.pdf",
        TekstExtractieStatus.BEZIG, BezwaarExtractieStatus.KLAAR);
    docC = maakDocument("project-beta", "bezwaar-3.pdf",
        TekstExtractieStatus.KLAAR, BezwaarExtractieStatus.BEZIG);
  }

  @AfterEach
  void tearDown() {
    repository.deleteAll();
  }

  @Test
  @DisplayName("findByTekstExtractieStatus geeft alleen documenten met die status")
  void findByTekstExtractieStatusGeeftAlleenDocumentenMetDieStatus() {
    var resultaat = repository.findByTekstExtractieStatus(TekstExtractieStatus.KLAAR);

    assertThat(resultaat).hasSize(2);
    assertThat(resultaat).extracting(BezwaarDocument::getBestandsnaam)
        .containsExactlyInAnyOrder("bezwaar-1.pdf", "bezwaar-3.pdf");
  }

  @Test
  @DisplayName("findByTekstExtractieStatus geeft lege lijst bij geen match")
  void findByTekstExtractieStatusGeeftLegeLijstBijGeenMatch() {
    var resultaat = repository.findByTekstExtractieStatus(TekstExtractieStatus.FOUT);

    assertThat(resultaat).isEmpty();
  }

  @Test
  @DisplayName("findByBezwaarExtractieStatus geeft alleen documenten met die status")
  void findByBezwaarExtractieStatusGeeftAlleenDocumentenMetDieStatus() {
    var resultaat = repository.findByBezwaarExtractieStatus(BezwaarExtractieStatus.KLAAR);

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).getBestandsnaam()).isEqualTo("bezwaar-2.pdf");
  }

  @Test
  @DisplayName("findByBezwaarExtractieStatus geeft lege lijst bij geen match")
  void findByBezwaarExtractieStatusGeeftLegeLijstBijGeenMatch() {
    var resultaat = repository.findByBezwaarExtractieStatus(BezwaarExtractieStatus.FOUT);

    assertThat(resultaat).isEmpty();
  }

  @Test
  @DisplayName("findByProjectNaamAndBestandsnaam vindt één document")
  void findByProjectNaamAndBestandsnaamVindtEenDocument() {
    var resultaat = repository.findByProjectNaamAndBestandsnaam("project-alpha", "bezwaar-1.pdf");

    assertThat(resultaat).isPresent();
    assertThat(resultaat.get().getId()).isEqualTo(docA.getId());
    assertThat(resultaat.get().getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.KLAAR);
  }

  @Test
  @DisplayName("findByProjectNaamAndBestandsnaam geeft empty bij onbekend document")
  void findByProjectNaamAndBestandsnaamGeeftEmptyBijOnbekendDocument() {
    var resultaat = repository.findByProjectNaamAndBestandsnaam("project-alpha", "onbekend.pdf");

    assertThat(resultaat).isEmpty();
  }

  @Test
  @DisplayName("findByProjectNaamAndBestandsnaam matcht niet op verkeerd project")
  void findByProjectNaamAndBestandsnaamMatchtNietOpVerkeerProject() {
    var resultaat = repository.findByProjectNaamAndBestandsnaam("project-beta", "bezwaar-1.pdf");

    assertThat(resultaat).isEmpty();
  }

  @Test
  @DisplayName("deleteByProjectNaamAndBestandsnaam verwijdert correct document")
  @Transactional
  void deleteByProjectNaamAndBestandsnaamVerwijdertCorrectDocument() {
    repository.deleteByProjectNaamAndBestandsnaam("project-alpha", "bezwaar-1.pdf");

    assertThat(repository.findByProjectNaamAndBestandsnaam("project-alpha", "bezwaar-1.pdf"))
        .isEmpty();
    assertThat(repository.findByProjectNaamAndBestandsnaam("project-alpha", "bezwaar-2.pdf"))
        .isPresent();
    assertThat(repository.findAll()).hasSize(2);
  }

  @Test
  @DisplayName("findByProjectNaam geeft alle documenten voor een project")
  void findByProjectNaamGeeftAlleDocumentenVoorEenProject() {
    var resultaat = repository.findByProjectNaam("project-alpha");

    assertThat(resultaat).hasSize(2);
    assertThat(resultaat).extracting(BezwaarDocument::getBestandsnaam)
        .containsExactlyInAnyOrder("bezwaar-1.pdf", "bezwaar-2.pdf");
  }

  @Test
  @DisplayName("findByProjectNaam geeft lege lijst voor onbekend project")
  void findByProjectNaamGeeftLegeLijstVoorOnbekendProject() {
    var resultaat = repository.findByProjectNaam("onbekend-project");

    assertThat(resultaat).isEmpty();
  }

  @Test
  @DisplayName("deleteByProjectNaamAndBestandsnaamIn verwijdert meerdere documenten in batch")
  @Transactional
  void deleteByProjectNaamAndBestandsnaamInVerwijdertMeerdereDocumentenInBatch() {
    repository.deleteByProjectNaamAndBestandsnaamIn("project-alpha",
        List.of("bezwaar-1.pdf", "bezwaar-2.pdf"));

    assertThat(repository.findByProjectNaam("project-alpha")).isEmpty();
    assertThat(repository.findByProjectNaam("project-beta")).hasSize(1);
  }

  @Test
  @DisplayName("countByProjectNaam telt correct per project")
  void countByProjectNaamTeltCorrectPerProject() {
    assertThat(repository.countByProjectNaam("project-alpha")).isEqualTo(2);
    assertThat(repository.countByProjectNaam("project-beta")).isEqualTo(1);
    assertThat(repository.countByProjectNaam("onbekend")).isZero();
  }

  @Test
  @DisplayName("countByTekstExtractieStatus telt correct per status")
  void countByTekstExtractieStatusTeltCorrectPerStatus() {
    assertThat(repository.countByTekstExtractieStatus(TekstExtractieStatus.KLAAR)).isEqualTo(2);
    assertThat(repository.countByTekstExtractieStatus(TekstExtractieStatus.BEZIG)).isEqualTo(1);
    assertThat(repository.countByTekstExtractieStatus(TekstExtractieStatus.FOUT)).isZero();
  }

  @Test
  @DisplayName("countByBezwaarExtractieStatus telt correct per status")
  void countByBezwaarExtractieStatusTeltCorrectPerStatus() {
    assertThat(repository.countByBezwaarExtractieStatus(BezwaarExtractieStatus.GEEN)).isEqualTo(1);
    assertThat(repository.countByBezwaarExtractieStatus(BezwaarExtractieStatus.KLAAR)).isEqualTo(1);
    assertThat(repository.countByBezwaarExtractieStatus(BezwaarExtractieStatus.BEZIG)).isEqualTo(1);
    assertThat(repository.countByBezwaarExtractieStatus(BezwaarExtractieStatus.FOUT)).isZero();
  }

  @Test
  @DisplayName("deleteByProjectNaam verwijdert alle documenten voor een project")
  @Transactional
  void deleteByProjectNaamVerwijdertAlleDocumentenVoorEenProject() {
    repository.deleteByProjectNaam("project-alpha");

    assertThat(repository.findByProjectNaam("project-alpha")).isEmpty();
    assertThat(repository.findByProjectNaam("project-beta")).hasSize(1);
  }

  // --- Helper methoden ---

  private BezwaarDocument maakDocument(String projectNaam, String bestandsnaam,
      TekstExtractieStatus tekstStatus, BezwaarExtractieStatus bezwaarStatus) {
    var doc = new BezwaarDocument();
    doc.setProjectNaam(projectNaam);
    doc.setBestandsnaam(bestandsnaam);
    doc.setTekstExtractieStatus(tekstStatus);
    doc.setBezwaarExtractieStatus(bezwaarStatus);
    return repository.save(doc);
  }
}
