package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;

import be.vlaanderen.omgeving.bezwaarschriften.BaseBezwaarschriftenIntegrationTest;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaak;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaakRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaakStatus;
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
 * Integratietest voor de passage_groep en passage_groep_lid tabellen.
 * Valideert dat de JPA-entiteiten correct samenwerken met de database
 * via Testcontainers.
 */
class PassageGroepPersistentieTest extends BaseBezwaarschriftenIntegrationTest {

  @Autowired
  private PassageGroepRepository passageGroepRepository;

  @Autowired
  private PassageGroepLidRepository passageGroepLidRepository;

  @Autowired
  private ClusteringTaakRepository clusteringTaakRepository;

  @Autowired
  private ExtractieTaakRepository extractieTaakRepository;

  @Autowired
  private IndividueelBezwaarRepository bezwaarRepository;

  @Autowired
  private KernbezwaarRepository kernbezwaarRepository;

  @Autowired
  private KernbezwaarReferentieRepository referentieRepository;

  @BeforeEach
  void setUp() {
    referentieRepository.deleteAll();
    passageGroepLidRepository.deleteAll();
    passageGroepRepository.deleteAll();
    kernbezwaarRepository.deleteAll();
    clusteringTaakRepository.deleteAll();
    bezwaarRepository.deleteAll();
    extractieTaakRepository.deleteAll();
  }

  @Test
  @DisplayName("Slaat PassageGroep op en vindt terug via clusteringTaakId")
  void slaatPassageGroepOpEnVindtTerugViaClusteringTaakId() {
    var clusteringTaak = maakClusteringTaak("testproject");

    var groep = new PassageGroepEntiteit();
    groep.setClusteringTaakId(clusteringTaak.getId());
    groep.setPassage("Het geluid is onaanvaardbaar hoog en overschrijdt de normen.");
    groep.setSamenvatting("Geluidshinder overschrijdt normen");
    groep.setCategorie("IDENTIEK");
    groep.setScorePercentage(95);
    passageGroepRepository.save(groep);

    var gevonden = passageGroepRepository.findByClusteringTaakId(clusteringTaak.getId());
    assertThat(gevonden).hasSize(1);
    assertThat(gevonden.get(0).getPassage())
        .isEqualTo("Het geluid is onaanvaardbaar hoog en overschrijdt de normen.");
    assertThat(gevonden.get(0).getSamenvatting()).isEqualTo("Geluidshinder overschrijdt normen");
    assertThat(gevonden.get(0).getCategorie()).isEqualTo("IDENTIEK");
    assertThat(gevonden.get(0).getScorePercentage()).isEqualTo(95);
  }

  @Test
  @DisplayName("Slaat PassageGroepLid op en vindt terug via passageGroepId")
  void slaatPassageGroepLidOpEnVindtTerugViaPassageGroepId() {
    var clusteringTaak = maakClusteringTaak("testproject");
    var extractieTaak = maakExtractieTaak("testproject", "doc-a.pdf");
    var groep = maakPassageGroep(clusteringTaak.getId(), "Geluidshinder passage",
        "Geluidshinder", "VERGELIJKBAAR");

    final var bezwaar = maakBezwaar(extractieTaak.getId(), "Bezwaar over geluid");
    var lid = new PassageGroepLidEntiteit();
    lid.setPassageGroepId(groep.getId());
    lid.setBezwaarId(bezwaar.getId());
    lid.setBestandsnaam("doc-a.pdf");
    passageGroepLidRepository.save(lid);

    var leden = passageGroepLidRepository.findByPassageGroepId(groep.getId());
    assertThat(leden).hasSize(1);
    assertThat(leden.get(0).getBezwaarId()).isEqualTo(bezwaar.getId());
    assertThat(leden.get(0).getBestandsnaam()).isEqualTo("doc-a.pdf");
  }

  @Test
  @DisplayName("findByPassageGroepIdIn retourneert leden van meerdere groepen")
  void findByPassageGroepIdInRetourneertLedenVanMeerdereGroepen() {
    var clusteringTaak = maakClusteringTaak("testproject");
    var extractieTaak = maakExtractieTaak("testproject", "doc-a.pdf");
    var bezwaar1 = maakBezwaar(extractieTaak.getId(), "Bezwaar 1");
    var bezwaar2 = maakBezwaar(extractieTaak.getId(), "Bezwaar 2");

    var groep1 = maakPassageGroep(clusteringTaak.getId(), "Passage 1", "Samenvatting 1",
        "IDENTIEK");
    var groep2 = maakPassageGroep(clusteringTaak.getId(), "Passage 2", "Samenvatting 2",
        "VERGELIJKBAAR");

    maakPassageGroepLid(groep1.getId(), bezwaar1.getId(), "doc-a.pdf");
    maakPassageGroepLid(groep2.getId(), bezwaar2.getId(), "doc-a.pdf");

    var leden = passageGroepLidRepository.findByPassageGroepIdIn(
        List.of(groep1.getId(), groep2.getId()));
    assertThat(leden).hasSize(2);
  }

  @Test
  @DisplayName("Cascade delete: verwijdering van clustering_taak verwijdert passage_groepen")
  void cascadeDeleteVerwijdertPassageGroepenBijClusteringTaakVerwijdering() {
    var clusteringTaak = maakClusteringTaak("testproject");
    var extractieTaak = maakExtractieTaak("testproject", "doc-a.pdf");
    var bezwaar = maakBezwaar(extractieTaak.getId(), "Bezwaar");

    var groep = maakPassageGroep(clusteringTaak.getId(), "Passage", "Samenvatting", "IDENTIEK");
    maakPassageGroepLid(groep.getId(), bezwaar.getId(), "doc-a.pdf");

    clusteringTaakRepository.deleteById(clusteringTaak.getId());

    assertThat(passageGroepRepository.findByClusteringTaakId(clusteringTaak.getId())).isEmpty();
    assertThat(passageGroepLidRepository.findByPassageGroepId(groep.getId())).isEmpty();
  }

  @Test
  @DisplayName("deleteByClusteringTaakId verwijdert alle groepen voor een taak")
  @Transactional
  void deleteByClusteringTaakIdVerwijdertAlleGroepenVoorEenTaak() {
    var taak1 = maakClusteringTaak("project-a");
    var taak2 = maakClusteringTaak("project-b");

    maakPassageGroep(taak1.getId(), "Groep A1", "Samenvatting A1", "IDENTIEK");
    maakPassageGroep(taak1.getId(), "Groep A2", "Samenvatting A2", "VERGELIJKBAAR");
    maakPassageGroep(taak2.getId(), "Groep B1", "Samenvatting B1", "IDENTIEK");

    passageGroepRepository.deleteByClusteringTaakId(taak1.getId());

    assertThat(passageGroepRepository.findByClusteringTaakId(taak1.getId())).isEmpty();
    assertThat(passageGroepRepository.findByClusteringTaakId(taak2.getId())).hasSize(1);
  }

  @Test
  @DisplayName("KernbezwaarReferentie met passageGroepId wordt correct opgeslagen")
  void kernbezwaarReferentieMetPassageGroepId() {
    final var clusteringTaak = maakClusteringTaak("testproject");
    var kern = new KernbezwaarEntiteit();
    kern.setProjectNaam("testproject");
    kern.setSamenvatting("Geluidshinder");
    kern = kernbezwaarRepository.save(kern);

    final var groep = maakPassageGroep(clusteringTaak.getId(), "Passage", "Samenvatting",
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

  private ExtractieTaak maakExtractieTaak(String projectNaam, String bestandsnaam) {
    var taak = new ExtractieTaak();
    taak.setProjectNaam(projectNaam);
    taak.setBestandsnaam(bestandsnaam);
    taak.setStatus(ExtractieTaakStatus.KLAAR);
    taak.setAantalPogingen(1);
    taak.setMaxPogingen(3);
    taak.setAangemaaktOp(Instant.now());
    return extractieTaakRepository.save(taak);
  }

  private IndividueelBezwaar maakBezwaar(Long taakId, String samenvatting) {
    var bezwaar = new IndividueelBezwaar();
    bezwaar.setTaakId(taakId);
    bezwaar.setProjectNaam("testproject");
    bezwaar.setBestandsnaam("test.txt");
    bezwaar.setPassageNr(1);
    bezwaar.setSamenvatting(samenvatting);
    return bezwaarRepository.save(bezwaar);
  }

  private PassageGroepEntiteit maakPassageGroep(Long clusteringTaakId, String passage,
      String samenvatting, String categorie) {
    var groep = new PassageGroepEntiteit();
    groep.setClusteringTaakId(clusteringTaakId);
    groep.setPassage(passage);
    groep.setSamenvatting(samenvatting);
    groep.setCategorie(categorie);
    return passageGroepRepository.save(groep);
  }

  private PassageGroepLidEntiteit maakPassageGroepLid(Long passageGroepId, Long bezwaarId,
      String bestandsnaam) {
    var lid = new PassageGroepLidEntiteit();
    lid.setPassageGroepId(passageGroepId);
    lid.setBezwaarId(bezwaarId);
    lid.setBestandsnaam(bestandsnaam);
    return passageGroepLidRepository.save(lid);
  }
}
