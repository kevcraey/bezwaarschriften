package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Tests voor {@link ExtractieTaakRepository} custom query methods.
 */
@DataJpaTest
class ExtractieTaakRepositoryTest {

  @Autowired
  private ExtractieTaakRepository repository;

  @Test
  void slaatTaakOpEnVindtTerugOpId() {
    ExtractieTaak taak = maakTaak("project-a", "bestand.txt",
        ExtractieTaakStatus.WACHTEND, Instant.now());

    ExtractieTaak opgeslagen = repository.save(taak);

    assertThat(opgeslagen.getId()).isNotNull();
    Optional<ExtractieTaak> gevonden = repository.findById(opgeslagen.getId());
    assertThat(gevonden).isPresent();
    assertThat(gevonden.get().getProjectNaam()).isEqualTo("project-a");
    assertThat(gevonden.get().getBestandsnaam()).isEqualTo("bestand.txt");
    assertThat(gevonden.get().getStatus()).isEqualTo(ExtractieTaakStatus.WACHTEND);
  }

  @Test
  void vindtTakenOpProjectNaam() {
    repository.save(maakTaak("project-a", "bestand1.txt",
        ExtractieTaakStatus.WACHTEND, Instant.now()));
    repository.save(maakTaak("project-a", "bestand2.txt",
        ExtractieTaakStatus.BEZIG, Instant.now()));
    repository.save(maakTaak("project-b", "bestand3.txt",
        ExtractieTaakStatus.WACHTEND, Instant.now()));

    List<ExtractieTaak> resultaat = repository.findByProjectNaam("project-a");

    assertThat(resultaat).hasSize(2);
    assertThat(resultaat).allSatisfy(t ->
        assertThat(t.getProjectNaam()).isEqualTo("project-a"));
  }

  @Test
  void teltTakenOpStatus() {
    repository.save(maakTaak("p1", "a.txt", ExtractieTaakStatus.WACHTEND, Instant.now()));
    repository.save(maakTaak("p1", "b.txt", ExtractieTaakStatus.WACHTEND, Instant.now()));
    repository.save(maakTaak("p1", "c.txt", ExtractieTaakStatus.BEZIG, Instant.now()));
    repository.save(maakTaak("p1", "d.txt", ExtractieTaakStatus.KLAAR, Instant.now()));

    assertThat(repository.countByStatus(ExtractieTaakStatus.WACHTEND)).isEqualTo(2);
    assertThat(repository.countByStatus(ExtractieTaakStatus.BEZIG)).isEqualTo(1);
    assertThat(repository.countByStatus(ExtractieTaakStatus.KLAAR)).isEqualTo(1);
    assertThat(repository.countByStatus(ExtractieTaakStatus.FOUT)).isZero();
  }

  @Test
  void vindtWachtendeTakenGesorteerdOpAangemaaktOp() {
    Instant nu = Instant.now();
    repository.save(maakTaak("p1", "derde.txt",
        ExtractieTaakStatus.WACHTEND, nu));
    repository.save(maakTaak("p1", "eerste.txt",
        ExtractieTaakStatus.WACHTEND, nu.minus(2, ChronoUnit.HOURS)));
    repository.save(maakTaak("p1", "tweede.txt",
        ExtractieTaakStatus.WACHTEND, nu.minus(1, ChronoUnit.HOURS)));
    repository.save(maakTaak("p1", "bezig.txt",
        ExtractieTaakStatus.BEZIG, nu.minus(3, ChronoUnit.HOURS)));

    List<ExtractieTaak> resultaat =
        repository.findByStatusOrderByAangemaaktOpAsc(ExtractieTaakStatus.WACHTEND);

    assertThat(resultaat).hasSize(3);
    assertThat(resultaat.get(0).getBestandsnaam()).isEqualTo("eerste.txt");
    assertThat(resultaat.get(1).getBestandsnaam()).isEqualTo("tweede.txt");
    assertThat(resultaat.get(2).getBestandsnaam()).isEqualTo("derde.txt");
  }

  @Test
  void vindtLaatsteTaakVoorBestand() {
    Instant nu = Instant.now();
    repository.save(maakTaak("project-a", "rapport.txt",
        ExtractieTaakStatus.FOUT, nu.minus(2, ChronoUnit.HOURS)));
    repository.save(maakTaak("project-a", "rapport.txt",
        ExtractieTaakStatus.KLAAR, nu));
    repository.save(maakTaak("project-a", "rapport.txt",
        ExtractieTaakStatus.WACHTEND, nu.minus(1, ChronoUnit.HOURS)));
    repository.save(maakTaak("project-b", "rapport.txt",
        ExtractieTaakStatus.WACHTEND, nu.plus(1, ChronoUnit.HOURS)));

    Optional<ExtractieTaak> resultaat =
        repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
            "project-a", "rapport.txt");

    assertThat(resultaat).isPresent();
    assertThat(resultaat.get().getStatus()).isEqualTo(ExtractieTaakStatus.KLAAR);
    assertThat(resultaat.get().getAangemaaktOp()).isEqualTo(nu);
  }

  private ExtractieTaak maakTaak(String projectNaam, String bestandsnaam,
      ExtractieTaakStatus status, Instant aangemaaktOp) {
    ExtractieTaak taak = new ExtractieTaak();
    taak.setProjectNaam(projectNaam);
    taak.setBestandsnaam(bestandsnaam);
    taak.setStatus(status);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    taak.setAangemaaktOp(aangemaaktOp);
    return taak;
  }
}
