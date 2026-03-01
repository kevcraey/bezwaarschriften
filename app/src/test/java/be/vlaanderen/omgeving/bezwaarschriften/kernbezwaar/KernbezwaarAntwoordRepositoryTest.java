package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class KernbezwaarAntwoordRepositoryTest {

  @Autowired
  private KernbezwaarAntwoordRepository repository;

  @Autowired
  private TestEntityManager entityManager;

  @Test
  void slaatAntwoordOpEnHaaltHetOp() {
    var kernId = maakKernbezwaar("Thema 1", "Samenvatting 1");

    var entiteit = new KernbezwaarAntwoordEntiteit();
    entiteit.setKernbezwaarId(kernId);
    entiteit.setInhoud("<p>Het weerwoord</p>");
    entiteit.setBijgewerktOp(Instant.now());

    repository.save(entiteit);
    entityManager.flush();
    entityManager.clear();

    var opgehaald = repository.findById(kernId);
    assertThat(opgehaald).isPresent();
    assertThat(opgehaald.get().getInhoud()).isEqualTo("<p>Het weerwoord</p>");
  }

  @Test
  void vindAntwoordenVoorMeerdereKernbezwaarIds() {
    var kern1 = maakKernbezwaar("Thema A", "Samenvatting A");

    var e1 = new KernbezwaarAntwoordEntiteit();
    e1.setKernbezwaarId(kern1);
    e1.setInhoud("<p>Antwoord 1</p>");
    e1.setBijgewerktOp(Instant.now());
    repository.save(e1);

    var kern2 = maakKernbezwaar("Thema B", "Samenvatting B");

    var e2 = new KernbezwaarAntwoordEntiteit();
    e2.setKernbezwaarId(kern2);
    e2.setInhoud("<p>Antwoord 2</p>");
    e2.setBijgewerktOp(Instant.now());
    repository.save(e2);

    entityManager.flush();
    entityManager.clear();

    var resultaat = repository.findByKernbezwaarIdIn(List.of(kern1, kern2, 999L));
    assertThat(resultaat).hasSize(2);
  }

  @Test
  void upsertBijBestaandAntwoord() {
    var kernId = maakKernbezwaar("Thema X", "Samenvatting X");

    var entiteit = new KernbezwaarAntwoordEntiteit();
    entiteit.setKernbezwaarId(kernId);
    entiteit.setInhoud("<p>Oud</p>");
    entiteit.setBijgewerktOp(Instant.now());
    repository.save(entiteit);
    entityManager.flush();
    entityManager.clear();

    // Overschrijven met nieuw antwoord
    var bijgewerkt = new KernbezwaarAntwoordEntiteit();
    bijgewerkt.setKernbezwaarId(kernId);
    bijgewerkt.setInhoud("<p>Nieuw</p>");
    bijgewerkt.setBijgewerktOp(Instant.now());
    repository.save(bijgewerkt);
    entityManager.flush();
    entityManager.clear();

    var opgehaald = repository.findById(kernId);
    assertThat(opgehaald).isPresent();
    assertThat(opgehaald.get().getInhoud()).isEqualTo("<p>Nieuw</p>");
  }

  private Long maakKernbezwaar(String themaNaam, String samenvatting) {
    var thema = new ThemaEntiteit();
    thema.setProjectNaam("test-project");
    thema.setNaam(themaNaam);
    entityManager.persist(thema);

    var kern = new KernbezwaarEntiteit();
    kern.setThemaId(thema.getId());
    kern.setSamenvatting(samenvatting);
    entityManager.persist(kern);
    entityManager.flush();

    return kern.getId();
  }
}
