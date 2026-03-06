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
  private TestEntityManager entityManager;

  @Test
  void slaatKernbezwaarMetReferentieOp() {
    var kern = new KernbezwaarEntiteit();
    kern.setProjectNaam("windmolens");
    kern.setSamenvatting("Geluid overschrijdt normen");
    kern = kernbezwaarRepository.save(kern);

    var ref = new KernbezwaarReferentieEntiteit();
    ref.setKernbezwaarId(kern.getId());
    ref.setBezwaarId(42L);
    ref.setBestandsnaam("bezwaar-janssen.pdf");
    ref.setPassage("Het geluid is onacceptabel hoog.");
    referentieRepository.save(ref);

    entityManager.flush();
    entityManager.clear();

    var kernen = kernbezwaarRepository.findByProjectNaam("windmolens");
    assertThat(kernen).hasSize(1);
    assertThat(kernen.get(0).getSamenvatting()).isEqualTo("Geluid overschrijdt normen");

    var refs = referentieRepository.findByKernbezwaarIdIn(
        kernen.stream().map(KernbezwaarEntiteit::getId).toList());
    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getBestandsnaam()).isEqualTo("bezwaar-janssen.pdf");
    assertThat(refs.get(0).getBezwaarId()).isEqualTo(42L);
  }

  @Test
  void cascadeDeleteVerwijdertAlleGerelateerdeData() {
    // Maak kernbezwaar -> referentie + antwoord
    var kern = new KernbezwaarEntiteit();
    kern.setProjectNaam("windmolens");
    kern.setSamenvatting("Verkeershinder");
    kern = kernbezwaarRepository.save(kern);

    var ref = new KernbezwaarReferentieEntiteit();
    ref.setKernbezwaarId(kern.getId());
    ref.setBezwaarId(1L);
    ref.setBestandsnaam("b1.pdf");
    ref.setPassage("passage");
    referentieRepository.save(ref);

    var antwoord = new KernbezwaarAntwoordEntiteit();
    antwoord.setKernbezwaarId(kern.getId());
    antwoord.setInhoud("<p>Weerwoord</p>");
    antwoord.setBijgewerktOp(Instant.now());
    antwoordRepository.save(antwoord);

    entityManager.flush();
    entityManager.clear();

    // Verwijder kernbezwaren -> alles moet weg zijn (DB cascade)
    kernbezwaarRepository.deleteByProjectNaam("windmolens");
    entityManager.flush();
    entityManager.clear();

    assertThat(kernbezwaarRepository.findByProjectNaam("windmolens")).isEmpty();
    assertThat(referentieRepository.findByKernbezwaarIdIn(java.util.List.of(kern.getId())))
        .isEmpty();
    assertThat(antwoordRepository.findById(kern.getId())).isEmpty();
  }

  @Test
  void referentieMetNullBezwaarId() {
    var kern = new KernbezwaarEntiteit();
    kern.setProjectNaam("windmolens");
    kern.setSamenvatting("Stankoverlast");
    kern = kernbezwaarRepository.save(kern);

    var ref = new KernbezwaarReferentieEntiteit();
    ref.setKernbezwaarId(kern.getId());
    ref.setBezwaarId(null);
    ref.setBestandsnaam("anoniem.pdf");
    ref.setPassage("Het stinkt enorm.");
    referentieRepository.save(ref);

    entityManager.flush();
    entityManager.clear();

    var refs = referentieRepository.findByKernbezwaarIdIn(
        java.util.List.of(kern.getId()));
    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getBezwaarId()).isNull();
  }
}
