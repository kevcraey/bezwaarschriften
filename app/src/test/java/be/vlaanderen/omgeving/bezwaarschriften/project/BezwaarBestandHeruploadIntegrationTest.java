package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.BaseBezwaarschriftenIntegrationTest;
import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.TekstExtractieTaakRepository;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Integratietest die reproduceert dat een bestand na verwijdering opnieuw
 * ge-upload kan worden zonder unique constraint violation op bezwaar_bestand.
 *
 * <p>Draait tegen een echte PostgreSQL via Testcontainers om de database-constraint
 * effectief te testen.
 */
class BezwaarBestandHeruploadIntegrationTest extends BaseBezwaarschriftenIntegrationTest {

  @MockBean
  private ProjectPoort projectPoort;

  @Autowired
  private ProjectService projectService;

  @Autowired
  private BezwaarBestandRepository bezwaarBestandRepository;

  @Autowired
  private ExtractieTaakRepository extractieTaakRepository;

  @Autowired
  private TekstExtractieTaakRepository tekstExtractieTaakRepository;

  @BeforeEach
  void setUp() {
    when(projectPoort.verwijderBestand(anyString(), anyString())).thenReturn(true);

    tekstExtractieTaakRepository.deleteAll();
    extractieTaakRepository.deleteAll();
    bezwaarBestandRepository.deleteAll();
  }

  @Test
  @DisplayName("Upload, verwijder, herupload: geen constraint violation")
  void uploadVerwijderHeruploadZonderConstraintViolation() {
    // Stap 1: Upload een bestand
    when(projectPoort.geefBestandsnamen("testproject")).thenReturn(List.of());
    var bestanden = new LinkedHashMap<String, byte[]>();
    bestanden.put("v2-247.txt", "inhoud".getBytes());

    var resultaat = projectService.uploadBezwaren("testproject", bestanden);
    assertThat(resultaat.geupload()).containsExactly("v2-247.txt");

    // Verifieer dat het record in de database staat
    // Status is TEKST_EXTRACTIE_WACHTEND omdat indienen() de status meteen bijwerkt
    var entiteit = bezwaarBestandRepository
        .findByProjectNaamAndBestandsnaam("testproject", "v2-247.txt");
    assertThat(entiteit).isPresent();
    assertThat(entiteit.get().getStatus())
        .isEqualTo(BezwaarBestandStatus.TEKST_EXTRACTIE_WACHTEND);

    // Stap 2: Verwijder het bestand
    projectService.verwijderBezwaar("testproject", "v2-247.txt");

    // Verifieer dat het record uit de database is
    var naVerwijdering = bezwaarBestandRepository
        .findByProjectNaamAndBestandsnaam("testproject", "v2-247.txt");
    assertThat(naVerwijdering)
        .as("Na verwijdering mag er geen bezwaar_bestand record meer bestaan")
        .isEmpty();

    // Stap 3: Upload hetzelfde bestand opnieuw — dit is waar de bug toeslaat
    when(projectPoort.geefBestandsnamen("testproject")).thenReturn(List.of());
    var herUpload = new LinkedHashMap<String, byte[]>();
    herUpload.put("v2-247.txt", "nieuwe inhoud".getBytes());

    var herResultaat = projectService.uploadBezwaren("testproject", herUpload);

    assertThat(herResultaat.geupload()).containsExactly("v2-247.txt");
    assertThat(herResultaat.fouten()).isEmpty();

    // Verifieer dat er een nieuw record is (indienen() zet status op TEKST_EXTRACTIE_WACHTEND)
    var naHerupload = bezwaarBestandRepository
        .findByProjectNaamAndBestandsnaam("testproject", "v2-247.txt");
    assertThat(naHerupload).isPresent();
    assertThat(naHerupload.get().getStatus())
        .isEqualTo(BezwaarBestandStatus.TEKST_EXTRACTIE_WACHTEND);
  }

  @Test
  @DisplayName("Bulk verwijder, herupload: geen constraint violation")
  void bulkVerwijderHeruploadZonderConstraintViolation() {
    // Stap 1: Upload meerdere bestanden
    when(projectPoort.geefBestandsnamen("testproject")).thenReturn(List.of());
    var bestanden = new LinkedHashMap<String, byte[]>();
    bestanden.put("v2-001.txt", "inhoud 1".getBytes());
    bestanden.put("v2-002.txt", "inhoud 2".getBytes());

    projectService.uploadBezwaren("testproject", bestanden);

    // Stap 2: Bulk verwijder
    projectService.verwijderBezwaren("testproject", List.of("v2-001.txt", "v2-002.txt"));

    // Verifieer dat records weg zijn
    assertThat(bezwaarBestandRepository.findByProjectNaam("testproject")).isEmpty();

    // Stap 3: Herupload
    when(projectPoort.geefBestandsnamen("testproject")).thenReturn(List.of());
    var herUpload = new LinkedHashMap<String, byte[]>();
    herUpload.put("v2-001.txt", "nieuwe inhoud".getBytes());

    var herResultaat = projectService.uploadBezwaren("testproject", herUpload);

    assertThat(herResultaat.geupload()).containsExactly("v2-001.txt");
    assertThat(herResultaat.fouten()).isEmpty();
  }
}
