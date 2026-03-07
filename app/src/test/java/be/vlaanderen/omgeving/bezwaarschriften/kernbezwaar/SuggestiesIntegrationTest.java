package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;

import be.vlaanderen.omgeving.bezwaarschriften.BaseBezwaarschriftenIntegrationTest;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaak;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaakRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaakStatus;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integratietest voor suggesties-functionaliteit.
 * TODO: task 8 - herschrijf centroid-testen met passage_groep_lid model.
 * De originele berekenCentroidsOpPassage/Samenvatting queries zijn verwijderd
 * bij de passage-deduplicatie migratie.
 */
class SuggestiesIntegrationTest extends BaseBezwaarschriftenIntegrationTest {

  @Autowired
  private ExtractieTaakRepository extractieTaakRepository;

  @Autowired
  private GeextraheerdBezwaarRepository bezwaarRepository;

  @Autowired
  private KernbezwaarRepository kernbezwaarRepository;

  @Autowired
  private KernbezwaarReferentieRepository referentieRepository;

  @Autowired
  private KernbezwaarAntwoordRepository antwoordRepository;

  @BeforeEach
  void setUp() {
    referentieRepository.deleteAll();
    antwoordRepository.deleteAll();
    kernbezwaarRepository.deleteAll();
    bezwaarRepository.deleteAll();
    extractieTaakRepository.deleteAll();
  }

  @Test
  @DisplayName("findByKernbezwaarIdIn retourneert referenties voor meerdere kernbezwaren")
  void findByKernbezwaarIdInRetourneertReferenties() {
    var kern1 = maakKernbezwaar("testproject", "Kern 1");
    final var kern2 = maakKernbezwaar("testproject", "Kern 2");

    var ref1 = new KernbezwaarReferentieEntiteit();
    ref1.setKernbezwaarId(kern1.getId());
    ref1.setPassageGroepId(0L); // placeholder
    referentieRepository.save(ref1);

    var ref2 = new KernbezwaarReferentieEntiteit();
    ref2.setKernbezwaarId(kern2.getId());
    ref2.setPassageGroepId(0L); // placeholder
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
}
