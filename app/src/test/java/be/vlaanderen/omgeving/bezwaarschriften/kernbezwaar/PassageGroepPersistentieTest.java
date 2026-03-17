package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;

import be.vlaanderen.omgeving.bezwaarschriften.BaseBezwaarschriftenIntegrationTest;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocument;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocumentRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.IndividueelBezwaar;
import be.vlaanderen.omgeving.bezwaarschriften.project.IndividueelBezwaarRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integratietest voor de bezwaar_groep en bezwaar_groep_lid tabellen.
 * Valideert dat de JPA-entiteiten correct samenwerken met de database
 * via Testcontainers.
 */
class PassageGroepPersistentieTest extends BaseBezwaarschriftenIntegrationTest {

  @Autowired
  private BezwaarGroepRepository bezwaarGroepRepository;

  @Autowired
  private BezwaarGroepLidRepository bezwaarGroepLidRepository;

  @Autowired
  private ClusteringTaakRepository clusteringTaakRepository;

  @Autowired
  private BezwaarDocumentRepository documentRepository;

  @Autowired
  private IndividueelBezwaarRepository bezwaarRepository;

  @Autowired
  private KernbezwaarRepository kernbezwaarRepository;

  @Autowired
  private KernbezwaarReferentieRepository referentieRepository;

  @BeforeEach
  void setUp() {
    referentieRepository.deleteAll();
    bezwaarGroepLidRepository.deleteAll();
    bezwaarGroepRepository.deleteAll();
    kernbezwaarRepository.deleteAll();
    clusteringTaakRepository.deleteAll();
    bezwaarRepository.deleteAll();
    documentRepository.deleteAll();
  }

  @Test
  @DisplayName("Slaat BezwaarGroep op en vindt terug via clusteringTaakId")
  void slaatBezwaarGroepOpEnVindtTerugViaClusteringTaakId() {
    var clusteringTaak = maakClusteringTaak("testproject");

    var groep = new BezwaarGroep();
    groep.setClusteringTaakId(clusteringTaak.getId());
    groep.setPassage("Het geluid is onaanvaardbaar hoog en overschrijdt de normen.");
    groep.setSamenvatting("Geluidshinder overschrijdt normen");
    groep.setCategorie("IDENTIEK");
    groep.setScorePercentage(95);
    bezwaarGroepRepository.save(groep);

    var gevonden = bezwaarGroepRepository.findByClusteringTaakId(clusteringTaak.getId());
    assertThat(gevonden).hasSize(1);
    assertThat(gevonden.get(0).getPassage())
        .isEqualTo("Het geluid is onaanvaardbaar hoog en overschrijdt de normen.");
    assertThat(gevonden.get(0).getSamenvatting()).isEqualTo("Geluidshinder overschrijdt normen");
    assertThat(gevonden.get(0).getCategorie()).isEqualTo("IDENTIEK");
    assertThat(gevonden.get(0).getScorePercentage()).isEqualTo(95);
  }

  @Test
  @DisplayName("Slaat BezwaarGroepLid op en vindt terug via bezwaarGroepId")
  void slaatBezwaarGroepLidOpEnVindtTerugViaBezwaarGroepId() {
    var clusteringTaak = maakClusteringTaak("testproject");
    var document = maakDocument("testproject", "doc-a.pdf");
    var groep = maakBezwaarGroep(clusteringTaak.getId(), "Geluidshinder passage",
        "Geluidshinder", "VERGELIJKBAAR");

    final var bezwaar = maakBezwaar(document.getId(), "Bezwaar over geluid");
    var lid = new BezwaarGroepLid();
    lid.setBezwaarGroepId(groep.getId());
    lid.setBezwaarId(bezwaar.getId());
    bezwaarGroepLidRepository.save(lid);

    var leden = bezwaarGroepLidRepository.findByBezwaarGroepId(groep.getId());
    assertThat(leden).hasSize(1);
    assertThat(leden.get(0).getBezwaarId()).isEqualTo(bezwaar.getId());
  }

  @Test
  @DisplayName("findByBezwaarGroepIdIn retourneert leden van meerdere groepen")
  void findByBezwaarGroepIdInRetourneertLedenVanMeerdereGroepen() {
    var clusteringTaak = maakClusteringTaak("testproject");
    var document = maakDocument("testproject", "doc-a.pdf");
    var bezwaar1 = maakBezwaar(document.getId(), "Bezwaar 1");
    var bezwaar2 = maakBezwaar(document.getId(), "Bezwaar 2");

    var groep1 = maakBezwaarGroep(clusteringTaak.getId(), "Passage 1", "Samenvatting 1",
        "IDENTIEK");
    var groep2 = maakBezwaarGroep(clusteringTaak.getId(), "Passage 2", "Samenvatting 2",
        "VERGELIJKBAAR");

    maakBezwaarGroepLid(groep1.getId(), bezwaar1.getId());
    maakBezwaarGroepLid(groep2.getId(), bezwaar2.getId());

    var leden = bezwaarGroepLidRepository.findByBezwaarGroepIdIn(
        List.of(groep1.getId(), groep2.getId()));
    assertThat(leden).hasSize(2);
  }

  @Test
  @DisplayName("Cascade delete: verwijdering van clustering_taak verwijdert bezwaar_groepen")
  void cascadeDeleteVerwijdertBezwaarGroepenBijClusteringTaakVerwijdering() {
    var clusteringTaak = maakClusteringTaak("testproject");
    var document = maakDocument("testproject", "doc-a.pdf");
    var bezwaar = maakBezwaar(document.getId(), "Bezwaar");

    var groep = maakBezwaarGroep(clusteringTaak.getId(), "Passage", "Samenvatting", "IDENTIEK");
    maakBezwaarGroepLid(groep.getId(), bezwaar.getId());

    clusteringTaakRepository.deleteById(clusteringTaak.getId());

    assertThat(bezwaarGroepRepository.findByClusteringTaakId(clusteringTaak.getId())).isEmpty();
    assertThat(bezwaarGroepLidRepository.findByBezwaarGroepId(groep.getId())).isEmpty();
  }

  @Test
  @DisplayName("deleteByClusteringTaakId verwijdert alle groepen voor een taak")
  @Transactional
  void deleteByClusteringTaakIdVerwijdertAlleGroepenVoorEenTaak() {
    var taak1 = maakClusteringTaak("project-a");
    var taak2 = maakClusteringTaak("project-b");

    maakBezwaarGroep(taak1.getId(), "Groep A1", "Samenvatting A1", "IDENTIEK");
    maakBezwaarGroep(taak1.getId(), "Groep A2", "Samenvatting A2", "VERGELIJKBAAR");
    maakBezwaarGroep(taak2.getId(), "Groep B1", "Samenvatting B1", "IDENTIEK");

    bezwaarGroepRepository.deleteByClusteringTaakId(taak1.getId());

    assertThat(bezwaarGroepRepository.findByClusteringTaakId(taak1.getId())).isEmpty();
    assertThat(bezwaarGroepRepository.findByClusteringTaakId(taak2.getId())).hasSize(1);
  }

  @Test
  @DisplayName("KernbezwaarReferentie met passageGroepId wordt correct opgeslagen")
  void kernbezwaarReferentieMetPassageGroepId() {
    final var clusteringTaak = maakClusteringTaak("testproject");
    var kern = new KernbezwaarEntiteit();
    kern.setProjectNaam("testproject");
    kern.setSamenvatting("Geluidshinder");
    kern = kernbezwaarRepository.save(kern);

    final var groep = maakBezwaarGroep(clusteringTaak.getId(), "Passage", "Samenvatting",
        "IDENTIEK");
    var ref = new KernbezwaarReferentieEntiteit();
    ref.setKernbezwaarId(kern.getId());
    ref.setPassageGroepId(groep.getId());
    ref = referentieRepository.save(ref);

    var refs = referentieRepository.findByKernbezwaarIdIn(List.of(kern.getId()));
    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getPassageGroepId()).isEqualTo(groep.getId());
    assertThat(refs.get(0).getToewijzingsmethode()).isEqualTo(ToewijzingsMethode.HDBSCAN);
  }

  @Test
  @DisplayName("ClusteringTaak met deduplicatieVoorClustering vlag")
  void clusteringTaakMetDeduplicatieVoorClusteringVlag() {
    var taak = new ClusteringTaak();
    taak.setProjectNaam("testproject");
    taak.setStatus(ClusteringTaakStatus.WACHTEND);
    taak.setAangemaaktOp(Instant.now());
    taak.setDeduplicatieVoorClustering(true);
    taak = clusteringTaakRepository.save(taak);

    var opgehaald = clusteringTaakRepository.findById(taak.getId());
    assertThat(opgehaald).isPresent();
    assertThat(opgehaald.get().isDeduplicatieVoorClustering()).isTrue();
  }

  @Test
  @DisplayName("ClusteringTaak zonder deduplicatie heeft standaard false")
  void clusteringTaakZonderDeduplicatieHeeftStandaardFalse() {
    var taak = maakClusteringTaak("testproject");

    var opgehaald = clusteringTaakRepository.findById(taak.getId());
    assertThat(opgehaald).isPresent();
    assertThat(opgehaald.get().isDeduplicatieVoorClustering()).isFalse();
  }

  // --- Helper methoden ---

  private ClusteringTaak maakClusteringTaak(String projectNaam) {
    var taak = new ClusteringTaak();
    taak.setProjectNaam(projectNaam);
    taak.setStatus(ClusteringTaakStatus.KLAAR);
    taak.setAangemaaktOp(Instant.now());
    return clusteringTaakRepository.save(taak);
  }

  private BezwaarDocument maakDocument(String projectNaam, String bestandsnaam) {
    var doc = new BezwaarDocument();
    doc.setProjectNaam(projectNaam);
    doc.setBestandsnaam(bestandsnaam);
    return documentRepository.save(doc);
  }

  private IndividueelBezwaar maakBezwaar(Long documentId, String samenvatting) {
    var bezwaar = new IndividueelBezwaar();
    bezwaar.setDocumentId(documentId);
    bezwaar.setSamenvatting(samenvatting);
    return bezwaarRepository.save(bezwaar);
  }

  private BezwaarGroep maakBezwaarGroep(Long clusteringTaakId, String passage,
      String samenvatting, String categorie) {
    var groep = new BezwaarGroep();
    groep.setClusteringTaakId(clusteringTaakId);
    groep.setPassage(passage);
    groep.setSamenvatting(samenvatting);
    groep.setCategorie(categorie);
    return bezwaarGroepRepository.save(groep);
  }

  private BezwaarGroepLid maakBezwaarGroepLid(Long bezwaarGroepId, Long bezwaarId) {
    var lid = new BezwaarGroepLid();
    lid.setBezwaarGroepId(bezwaarGroepId);
    lid.setBezwaarId(bezwaarId);
    return bezwaarGroepLidRepository.save(lid);
  }
}
