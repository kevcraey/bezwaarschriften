package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.BaseBezwaarschriftenIntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Integratietest die reproduceert dat een bestand na verwijdering opnieuw
 * ge-upload kan worden zonder unique constraint violation op bezwaar_document.
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
  private BezwaarDocumentRepository bezwaarDocumentRepository;

  @BeforeEach
  void setUp() {
    when(projectPoort.verwijderBestand(anyString(), anyString())).thenReturn(true);

    bezwaarDocumentRepository.deleteAll();
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

    // Verifieer dat het document in de database staat
    var document = bezwaarDocumentRepository
        .findByProjectNaamAndBestandsnaam("testproject", "v2-247.txt");
    assertThat(document).isPresent();

    // Stap 2: Verwijder het bestand
    projectService.verwijderBezwaar("testproject", "v2-247.txt");

    // Verifieer dat het document uit de database is
    var naVerwijdering = bezwaarDocumentRepository
        .findByProjectNaamAndBestandsnaam("testproject", "v2-247.txt");
    assertThat(naVerwijdering)
        .as("Na verwijdering mag er geen bezwaar_document record meer bestaan")
        .isEmpty();

    // Stap 3: Upload hetzelfde bestand opnieuw
    when(projectPoort.geefBestandsnamen("testproject")).thenReturn(List.of());
    var herUpload = new LinkedHashMap<String, byte[]>();
    herUpload.put("v2-247.txt", "nieuwe inhoud".getBytes());

    var herResultaat = projectService.uploadBezwaren("testproject", herUpload);

    assertThat(herResultaat.geupload()).containsExactly("v2-247.txt");
    assertThat(herResultaat.fouten()).isEmpty();

    // Verifieer dat er een nieuw document is
    var naHerupload = bezwaarDocumentRepository
        .findByProjectNaamAndBestandsnaam("testproject", "v2-247.txt");
    assertThat(naHerupload).isPresent();
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

    // Verifieer dat documenten weg zijn
    assertThat(bezwaarDocumentRepository.findByProjectNaam("testproject")).isEmpty();

    // Stap 3: Herupload
    when(projectPoort.geefBestandsnamen("testproject")).thenReturn(List.of());
    var herUpload = new LinkedHashMap<String, byte[]>();
    herUpload.put("v2-001.txt", "nieuwe inhoud".getBytes());

    var herResultaat = projectService.uploadBezwaren("testproject", herUpload);

    assertThat(herResultaat.geupload()).containsExactly("v2-001.txt");
    assertThat(herResultaat.fouten()).isEmpty();
  }
}
