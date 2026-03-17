package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;

import be.vlaanderen.omgeving.bezwaarschriften.BaseBezwaarschriftenIntegrationTest;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocumentRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.IndividueelBezwaar;
import be.vlaanderen.omgeving.bezwaarschriften.project.IndividueelBezwaarRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integratietest voor suggesties-functionaliteit.
 * Centroid-berekening verloopt nu via passage_groep_lid model.
 */
class SuggestiesIntegrationTest extends BaseBezwaarschriftenIntegrationTest {

  @Autowired
  private BezwaarDocumentRepository documentRepository;

  @Autowired
  private IndividueelBezwaarRepository bezwaarRepository;

  @Autowired
  private KernbezwaarRepository kernbezwaarRepository;

  @Autowired
  private KernbezwaarReferentieRepository referentieRepository;

  @Autowired
  private KernbezwaarAntwoordRepository antwoordRepository;

  @Autowired
  private ClusteringTaakRepository clusteringTaakRepository;

  @Autowired
  private BezwaarGroepRepository bezwaarGroepRepository;

  @BeforeEach
  void setUp() {
    referentieRepository.deleteAll();
    antwoordRepository.deleteAll();
    kernbezwaarRepository.deleteAll();
    bezwaarGroepRepository.deleteAll();
    clusteringTaakRepository.deleteAll();
    bezwaarRepository.deleteAll();
    documentRepository.deleteAll();
  }

  @Test
  @DisplayName("findByKernbezwaarIdIn retourneert referenties voor meerdere kernbezwaren")
  void findByKernbezwaarIdInRetourneertReferenties() {
    final var clusteringTaak = maakClusteringTaak("testproject");
    var kern1 = maakKernbezwaar("testproject", "Kern 1");
    final var kern2 = maakKernbezwaar("testproject", "Kern 2");

    final var groep1 = maakPassageGroep(clusteringTaak.getId(), "Passage 1");
    var ref1 = new KernbezwaarReferentieEntiteit();
    ref1.setKernbezwaarId(kern1.getId());
    ref1.setPassageGroepId(groep1.getId());
    referentieRepository.save(ref1);

    final var groep2 = maakPassageGroep(clusteringTaak.getId(), "Passage 2");
    var ref2 = new KernbezwaarReferentieEntiteit();
    ref2.setKernbezwaarId(kern2.getId());
    ref2.setPassageGroepId(groep2.getId());
    referentieRepository.save(ref2);

    var refs = referentieRepository.findByKernbezwaarIdIn(
        List.of(kern1.getId(), kern2.getId()));
    assertThat(refs).hasSize(2);
  }

  // --- Helpers ---

  private KernbezwaarEntiteit maakKernbezwaar(String projectNaam, String samenvatting) {
    var kern = new KernbezwaarEntiteit();
    kern.setProjectNaam(projectNaam);
    kern.setSamenvatting(samenvatting);
    return kernbezwaarRepository.save(kern);
  }

  private ClusteringTaak maakClusteringTaak(String projectNaam) {
    var taak = new ClusteringTaak();
    taak.setProjectNaam(projectNaam);
    taak.setStatus(ClusteringTaakStatus.KLAAR);
    taak.setAangemaaktOp(Instant.now());
    return clusteringTaakRepository.save(taak);
  }

  private BezwaarGroep maakPassageGroep(Long clusteringTaakId, String passage) {
    var groep = new BezwaarGroep();
    groep.setClusteringTaakId(clusteringTaakId);
    groep.setPassage(passage);
    groep.setSamenvatting(passage);
    groep.setCategorie("Test");
    return bezwaarGroepRepository.save(groep);
  }
}
