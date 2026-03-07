package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class KernbezwaarPersistentieTest {

  @Autowired
  private KernbezwaarRepository kernbezwaarRepository;

  @Autowired
  private KernbezwaarReferentieRepository referentieRepository;

  @Autowired
  private KernbezwaarAntwoordRepository antwoordRepository;

  @Autowired
  private ClusteringTaakRepository clusteringTaakRepository;

  @Autowired
  private PassageGroepRepository passageGroepRepository;

  @Autowired
  private TestEntityManager entityManager;

  @Test
  void slaatKernbezwaarMetReferentieOp() {
    var clusteringTaak = maakClusteringTaak("windmolens");
    final var groep = maakPassageGroep(clusteringTaak.getId());

    var kern = new KernbezwaarEntiteit();
    kern.setProjectNaam("windmolens");
    kern.setSamenvatting("Geluid overschrijdt normen");
    kern = kernbezwaarRepository.save(kern);

    var ref = new KernbezwaarReferentieEntiteit();
    ref.setKernbezwaarId(kern.getId());
    ref.setPassageGroepId(groep.getId());
    referentieRepository.save(ref);

    entityManager.flush();
    entityManager.clear();

    var kernen = kernbezwaarRepository.findByProjectNaam("windmolens");
    assertThat(kernen).hasSize(1);
    assertThat(kernen.get(0).getSamenvatting()).isEqualTo("Geluid overschrijdt normen");

    var refs = referentieRepository.findByKernbezwaarIdIn(
        kernen.stream().map(KernbezwaarEntiteit::getId).toList());
    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getPassageGroepId()).isEqualTo(groep.getId());
  }

  @Test
  void cascadeDeleteVerwijdertAlleGerelateerdeData() {
    var clusteringTaak = maakClusteringTaak("windmolens");
    final var groep = maakPassageGroep(clusteringTaak.getId());

    var kern = new KernbezwaarEntiteit();
    kern.setProjectNaam("windmolens");
    kern.setSamenvatting("Verkeershinder");
    kern = kernbezwaarRepository.save(kern);

    var ref = new KernbezwaarReferentieEntiteit();
    ref.setKernbezwaarId(kern.getId());
    ref.setPassageGroepId(groep.getId());
    referentieRepository.save(ref);

    var antwoord = new KernbezwaarAntwoordEntiteit();
    antwoord.setKernbezwaarId(kern.getId());
    antwoord.setInhoud("<p>Weerwoord</p>");
    antwoord.setBijgewerktOp(Instant.now());
    antwoordRepository.save(antwoord);

    entityManager.flush();
    entityManager.clear();

    kernbezwaarRepository.deleteByProjectNaam("windmolens");
    entityManager.flush();
    entityManager.clear();

    assertThat(kernbezwaarRepository.findByProjectNaam("windmolens")).isEmpty();
    assertThat(referentieRepository.findByKernbezwaarIdIn(java.util.List.of(kern.getId())))
        .isEmpty();
    assertThat(antwoordRepository.findById(kern.getId())).isEmpty();
  }

  @Test
  void referentieMetPassageGroepId() {
    var clusteringTaak = maakClusteringTaak("windmolens");
    final var groep = maakPassageGroep(clusteringTaak.getId());

    var kern = new KernbezwaarEntiteit();
    kern.setProjectNaam("windmolens");
    kern.setSamenvatting("Stankoverlast");
    kern = kernbezwaarRepository.save(kern);

    var ref = new KernbezwaarReferentieEntiteit();
    ref.setKernbezwaarId(kern.getId());
    ref.setPassageGroepId(groep.getId());
    referentieRepository.save(ref);

    entityManager.flush();
    entityManager.clear();

    var refs = referentieRepository.findByKernbezwaarIdIn(
        java.util.List.of(kern.getId()));
    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getPassageGroepId()).isEqualTo(groep.getId());
  }

  // --- Helper methoden ---

  private ClusteringTaak maakClusteringTaak(String projectNaam) {
    var taak = new ClusteringTaak();
    taak.setProjectNaam(projectNaam);
    taak.setStatus(ClusteringTaakStatus.KLAAR);
    taak.setAangemaaktOp(Instant.now());
    return clusteringTaakRepository.save(taak);
  }

  private PassageGroepEntiteit maakPassageGroep(Long clusteringTaakId) {
    var groep = new PassageGroepEntiteit();
    groep.setClusteringTaakId(clusteringTaakId);
    groep.setPassage("test passage");
    groep.setSamenvatting("test samenvatting");
    groep.setCategorie("Test");
    return passageGroepRepository.save(groep);
  }
}
