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
    var entiteit = new KernbezwaarAntwoordEntiteit();
    entiteit.setKernbezwaarId(1L);
    entiteit.setInhoud("<p>Het weerwoord</p>");
    entiteit.setBijgewerktOp(Instant.now());

    repository.save(entiteit);
    entityManager.flush();
    entityManager.clear();

    var opgehaald = repository.findById(1L);
    assertThat(opgehaald).isPresent();
    assertThat(opgehaald.get().getInhoud()).isEqualTo("<p>Het weerwoord</p>");
  }

  @Test
  void vindAntwoordenVoorMeerdereKernbezwaarIds() {
    var e1 = new KernbezwaarAntwoordEntiteit();
    e1.setKernbezwaarId(10L);
    e1.setInhoud("<p>Antwoord 1</p>");
    e1.setBijgewerktOp(Instant.now());

    var e2 = new KernbezwaarAntwoordEntiteit();
    e2.setKernbezwaarId(20L);
    e2.setInhoud("<p>Antwoord 2</p>");
    e2.setBijgewerktOp(Instant.now());

    repository.saveAll(List.of(e1, e2));
    entityManager.flush();
    entityManager.clear();

    var resultaat = repository.findByKernbezwaarIdIn(List.of(10L, 20L, 30L));
    assertThat(resultaat).hasSize(2);
  }

  @Test
  void upsertBijBestaandAntwoord() {
    var entiteit = new KernbezwaarAntwoordEntiteit();
    entiteit.setKernbezwaarId(1L);
    entiteit.setInhoud("<p>Oud</p>");
    entiteit.setBijgewerktOp(Instant.now());
    repository.save(entiteit);
    entityManager.flush();
    entityManager.clear();

    // Overschrijven met nieuw antwoord
    var bijgewerkt = new KernbezwaarAntwoordEntiteit();
    bijgewerkt.setKernbezwaarId(1L);
    bijgewerkt.setInhoud("<p>Nieuw</p>");
    bijgewerkt.setBijgewerktOp(Instant.now());
    repository.save(bijgewerkt);
    entityManager.flush();
    entityManager.clear();

    var opgehaald = repository.findById(1L);
    assertThat(opgehaald).isPresent();
    assertThat(opgehaald.get().getInhoud()).isEqualTo("<p>Nieuw</p>");
  }
}
