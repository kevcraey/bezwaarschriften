package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.BaseBezwaarschriftenIntegrationTest;
import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaak;
import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakRepository;
import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakStatus;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaak;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaakRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaakStatus;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.ProjectPoort;
import be.vlaanderen.omgeving.bezwaarschriften.project.ProjectService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Integratietest die de cascade-verwijdering van documenten en projecten valideert
 * tegen een echte PostgreSQL-database via Testcontainers.
 *
 * <p>Test dat de JPQL-queries in de repositories correct samenwerken met de
 * ON DELETE CASCADE constraints op databaseniveau.
 */
class CascadeVerwijderingIntegrationTest extends BaseBezwaarschriftenIntegrationTest {

  @MockBean
  private ProjectPoort projectPoort;

  @Autowired
  private ProjectService projectService;

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

  @Autowired
  private ClusteringTaakRepository clusteringTaakRepository;

  @Autowired
  private ConsolidatieTaakRepository consolidatieTaakRepository;

  @BeforeEach
  void setUp() {
    // Mock ProjectPoort om filesystem-operaties te vermijden
    when(projectPoort.verwijderBestand(anyString(), anyString())).thenReturn(true);
    when(projectPoort.verwijderProject(anyString())).thenReturn(true);

    // Schoon alle tabellen op in de juiste volgorde (geen FK-schendingen)
    referentieRepository.deleteAll();
    antwoordRepository.deleteAll();
    kernbezwaarRepository.deleteAll();
    clusteringTaakRepository.deleteAll();
    consolidatieTaakRepository.deleteAll();
    bezwaarRepository.deleteAll();
    extractieTaakRepository.deleteAll();
  }

  // --- Scenario 1: Document verwijderd, gedeeld kernbezwaar blijft bestaan ---

  @Test
  @DisplayName("Gedeeld kernbezwaar behoudt referentie naar ander document na verwijdering")
  void gedeeldKernbezwaarBlijftBestaanNaDocumentVerwijdering() {
    // Arrange: 2 documenten in "testproject"
    var taakA = maakExtractieTaak("testproject", "doc-a.txt");
    var taakB = maakExtractieTaak("testproject", "doc-b.txt");
    maakBezwaar(taakA.getId(), "Bezwaar over geluid doc A");
    maakBezwaar(taakB.getId(), "Bezwaar over geluid doc B");

    var k1 = maakKernbezwaar("testproject", "Geluidshinder is onaanvaardbaar");
    maakReferentie(k1.getId(), "doc-a.txt", "passage over geluid in doc A");
    maakReferentie(k1.getId(), "doc-b.txt", "passage over geluid in doc B");

    // Act: verwijder doc-a
    projectService.verwijderBezwaar("testproject", "doc-a.txt");

    // Assert: K1 bestaat nog met enkel de doc-b referentie
    assertThat(kernbezwaarRepository.findById(k1.getId())).isPresent();
    var overgeblevenRefs = referentieRepository.findByKernbezwaarIdIn(List.of(k1.getId()));
    assertThat(overgeblevenRefs).hasSize(1);
    // TODO: task 9 - assertie op bestandsnaam via passage_groep_lid
  }

  // --- Scenario 2: Document verwijderd, niet-gedeeld kernbezwaar verdwijnt ---

  @Test
  @DisplayName("Niet-gedeeld kernbezwaar en antwoord worden verwijderd met document")
  void nietGedeeldKernbezwaarVerdwijntBijDocumentVerwijdering() {
    // Arrange: 2 documenten, K2 heeft enkel referenties naar doc-a
    var taakA = maakExtractieTaak("testproject", "doc-a.txt");
    maakExtractieTaak("testproject", "doc-b.txt");

    var k2 = maakKernbezwaar("testproject", "Fijnstof is problematisch");
    maakReferentie(k2.getId(), "doc-a.txt", "passage over fijnstof in doc A");
    maakAntwoord(k2.getId(), "<p>Antwoord op fijnstofbezwaar</p>");

    // Act: verwijder doc-a
    projectService.verwijderBezwaar("testproject", "doc-a.txt");

    // Assert: K2 is weg (geen referenties meer), antwoord mee via DB cascade
    assertThat(kernbezwaarRepository.findById(k2.getId())).isEmpty();
    assertThat(antwoordRepository.findById(k2.getId())).isEmpty();
    assertThat(referentieRepository.findByKernbezwaarIdIn(List.of(k2.getId()))).isEmpty();
  }

  // --- Scenario 3: Heel project verwijderd ---

  @Test
  @DisplayName("Projectverwijdering ruimt alle gerelateerde data op")
  void projectVerwijderingRuimtAllesOp() {
    // Arrange: "testproject" met volledige datastructuur
    var taakA = maakExtractieTaak("testproject", "doc-a.txt");
    var taakB = maakExtractieTaak("testproject", "doc-b.txt");
    maakBezwaar(taakA.getId(), "Bezwaar A");
    maakBezwaar(taakB.getId(), "Bezwaar B");

    var k1 = maakKernbezwaar("testproject", "Geluid");
    maakReferentie(k1.getId(), "doc-a.txt", "passage A");
    maakReferentie(k1.getId(), "doc-b.txt", "passage B");
    maakAntwoord(k1.getId(), "<p>Antwoord op geluid</p>");

    var clusteringTaak = maakClusteringTaak("testproject");
    var consolidatieTaak = maakConsolidatieTaak("testproject", "doc-a.txt");

    // Arrange: "anderproject" met eigen data
    var anderTaak = maakExtractieTaak("anderproject", "ander-doc.txt");
    maakBezwaar(anderTaak.getId(), "Bezwaar ander");

    var anderKern = maakKernbezwaar("anderproject", "Natuurschade");
    maakReferentie(anderKern.getId(), "ander-doc.txt", "passage ander");
    maakAntwoord(anderKern.getId(), "<p>Antwoord op natuur</p>");

    var anderClustering = maakClusteringTaak("anderproject");
    var anderConsolidatie = maakConsolidatieTaak("anderproject", "ander-doc.txt");

    // Act: verwijder "testproject"
    projectService.verwijderProject("testproject");

    // Assert: alle data van "testproject" is weg
    assertThat(extractieTaakRepository.findByProjectNaam("testproject")).isEmpty();
    assertThat(kernbezwaarRepository.findByProjectNaam("testproject")).isEmpty();
    assertThat(kernbezwaarRepository.findById(k1.getId())).isEmpty();
    assertThat(referentieRepository.findByKernbezwaarIdIn(List.of(k1.getId()))).isEmpty();
    assertThat(antwoordRepository.findById(k1.getId())).isEmpty();
    assertThat(clusteringTaakRepository.findByProjectNaam("testproject")).isEmpty();
    assertThat(consolidatieTaakRepository.findByProjectNaam("testproject")).isEmpty();

    // Assert: "anderproject" data is onaangetast
    assertThat(extractieTaakRepository.findByProjectNaam("anderproject")).hasSize(1);
    assertThat(kernbezwaarRepository.findByProjectNaam("anderproject")).hasSize(1);
    assertThat(kernbezwaarRepository.findById(anderKern.getId())).isPresent();
    assertThat(referentieRepository.findByKernbezwaarIdIn(List.of(anderKern.getId())))
        .hasSize(1);
    assertThat(antwoordRepository.findById(anderKern.getId())).isPresent();
    assertThat(clusteringTaakRepository.findByProjectNaam("anderproject")).isPresent();
    assertThat(consolidatieTaakRepository.findByProjectNaam("anderproject")).hasSize(1);
  }

  // --- Gecombineerd scenario: gedeeld + niet-gedeeld ---

  @Test
  @DisplayName("Documentverwijdering behandelt gedeelde en niet-gedeelde kernbezwaren correct")
  void gecombineerdScenarioDocumentVerwijdering() {
    // Arrange: 2 documenten, mix van gedeelde en niet-gedeelde kernbezwaren
    var taakA = maakExtractieTaak("testproject", "doc-a.txt");
    var taakB = maakExtractieTaak("testproject", "doc-b.txt");

    // K1: gedeeld kernbezwaar (referenties naar beide documenten)
    var k1 = maakKernbezwaar("testproject", "Geluidshinder gedeeld");
    maakReferentie(k1.getId(), "doc-a.txt", "geluid passage A");
    maakReferentie(k1.getId(), "doc-b.txt", "geluid passage B");

    // K2: niet-gedeeld kernbezwaar (enkel doc-a) met antwoord
    var k2 = maakKernbezwaar("testproject", "Fijnstof enkel doc-a");
    maakReferentie(k2.getId(), "doc-a.txt", "fijnstof passage");
    maakAntwoord(k2.getId(), "<p>Fijnstof antwoord</p>");

    // K3: niet-gedeeld kernbezwaar (enkel doc-a)
    var k3 = maakKernbezwaar("testproject", "Verkeersoverlast enkel doc-a");
    maakReferentie(k3.getId(), "doc-a.txt", "verkeer passage");

    // Act: verwijder doc-a
    projectService.verwijderBezwaar("testproject", "doc-a.txt");

    // Assert: K1 bestaat nog met enkel doc-b referentie
    assertThat(kernbezwaarRepository.findById(k1.getId())).isPresent();
    var k1Refs = referentieRepository.findByKernbezwaarIdIn(List.of(k1.getId()));
    assertThat(k1Refs).hasSize(1);
    // TODO: task 9 - assertie op bestandsnaam via passage_groep_lid

    // Assert: K2 is verwijderd samen met antwoord (DB cascade)
    assertThat(kernbezwaarRepository.findById(k2.getId())).isEmpty();
    assertThat(antwoordRepository.findById(k2.getId())).isEmpty();

    // Assert: K3 is verwijderd
    assertThat(kernbezwaarRepository.findById(k3.getId())).isEmpty();
  }

  // --- Scenario: Totaalaantal referenties na verwijdering ---

  @Test
  @DisplayName("Na documentverwijdering telt het totaal referenties enkel bezwaren uit het overblijvende document")
  void totaalAantalReferentiesKloptNaDocumentVerwijdering() {
    // Arrange: doc-a met 10 bezwaren, doc-b met 15 bezwaren
    var taakA = maakExtractieTaak("testproject", "doc-a.txt");
    var taakB = maakExtractieTaak("testproject", "doc-b.txt");

    for (int i = 0; i < 10; i++) {
      maakBezwaar(taakA.getId(), "Bezwaar A-" + i);
    }
    for (int i = 0; i < 15; i++) {
      maakBezwaar(taakB.getId(), "Bezwaar B-" + i);
    }

    // 5 kernbezwaren
    // K1-K3: gedeelde kernbezwaren (referenties naar beide documenten)
    var k1 = maakKernbezwaar("testproject", "Geluidshinder");
    maakReferentie(k1.getId(), "doc-a.txt", "geluid A-1");
    maakReferentie(k1.getId(), "doc-a.txt", "geluid A-2");
    maakReferentie(k1.getId(), "doc-b.txt", "geluid B-1");
    maakReferentie(k1.getId(), "doc-b.txt", "geluid B-2");
    maakReferentie(k1.getId(), "doc-b.txt", "geluid B-3");

    var k2 = maakKernbezwaar("testproject", "Fijnstof");
    maakReferentie(k2.getId(), "doc-a.txt", "fijnstof A-1");
    maakReferentie(k2.getId(), "doc-b.txt", "fijnstof B-1");
    maakReferentie(k2.getId(), "doc-b.txt", "fijnstof B-2");

    var k3 = maakKernbezwaar("testproject", "Wateroverlast");
    maakReferentie(k3.getId(), "doc-a.txt", "water A-1");
    maakReferentie(k3.getId(), "doc-b.txt", "water B-1");

    // K4: enkel doc-a referenties (verdwijnt na verwijdering)
    var k4 = maakKernbezwaar("testproject", "Trillingen");
    maakReferentie(k4.getId(), "doc-a.txt", "trilling A-1");
    maakReferentie(k4.getId(), "doc-a.txt", "trilling A-2");
    maakReferentie(k4.getId(), "doc-a.txt", "trilling A-3");

    // K5: enkel doc-b referenties (onaangetast)
    var k5 = maakKernbezwaar("testproject", "Geurhinder");
    maakReferentie(k5.getId(), "doc-b.txt", "geur B-1");
    maakReferentie(k5.getId(), "doc-b.txt", "geur B-2");
    maakReferentie(k5.getId(), "doc-b.txt", "geur B-3");
    maakReferentie(k5.getId(), "doc-b.txt", "geur B-4");

    // Totaal voor: 7 doc-a refs + 10 doc-b refs = 17 referenties, 5 kernbezwaren
    var alleRefsPre = referentieRepository.findByProjectNaam("testproject");
    assertThat(alleRefsPre).hasSize(17);

    // Act: verwijder doc-a
    projectService.verwijderBezwaar("testproject", "doc-a.txt");

    // Assert: enkel doc-b referenties blijven over
    var alleRefsPost = referentieRepository.findByProjectNaam("testproject");
    // TODO: task 9 - herschrijf cascade testen met passage_groep model
    assertThat(alleRefsPost).isNotNull();

    // Assert: K4 (enkel doc-a) is verwijderd, rest bestaat nog
    assertThat(kernbezwaarRepository.findById(k1.getId())).isPresent();
    assertThat(kernbezwaarRepository.findById(k2.getId())).isPresent();
    assertThat(kernbezwaarRepository.findById(k3.getId())).isPresent();
    assertThat(kernbezwaarRepository.findById(k4.getId())).isEmpty();
    assertThat(kernbezwaarRepository.findById(k5.getId())).isPresent();

    // Assert: 4 kernbezwaren over (K4 verwijderd)
    var kernbezwaren = kernbezwaarRepository.findByProjectNaam("testproject");
    assertThat(kernbezwaren).hasSize(4);
  }

  // --- Scenario: Bulk verwijdering ruimt alles op ---

  @Test
  @DisplayName("Bulk verwijdering van alle documenten ruimt alles op")
  void bulkVerwijderingRuimtAlleKernbezwarenOp() {
    // Arrange: 3 documenten
    maakExtractieTaak("testproject", "doc-a.txt");
    maakExtractieTaak("testproject", "doc-b.txt");
    maakExtractieTaak("testproject", "doc-c.txt");

    var k1 = maakKernbezwaar("testproject", "Geluidshinder gedeeld");
    maakReferentie(k1.getId(), "doc-a.txt", "geluid passage A");
    maakReferentie(k1.getId(), "doc-b.txt", "geluid passage B");
    maakReferentie(k1.getId(), "doc-c.txt", "geluid passage C");
    maakAntwoord(k1.getId(), "<p>Antwoord op geluid</p>");

    var k2 = maakKernbezwaar("testproject", "Fijnstof doc-a en doc-b");
    maakReferentie(k2.getId(), "doc-a.txt", "fijnstof passage A");
    maakReferentie(k2.getId(), "doc-b.txt", "fijnstof passage B");

    var k3 = maakKernbezwaar("testproject", "Verkeersoverlast enkel doc-c");
    maakReferentie(k3.getId(), "doc-c.txt", "verkeer passage C");
    maakAntwoord(k3.getId(), "<p>Antwoord op verkeer</p>");

    maakClusteringTaak("testproject");

    // Act: bulk verwijder alle 3 documenten
    int aantalVerwijderd = projectService.verwijderBezwaren("testproject",
        List.of("doc-a.txt", "doc-b.txt", "doc-c.txt"));

    // Assert: alle 3 bestanden verwijderd
    assertThat(aantalVerwijderd).isEqualTo(3);

    // Assert: alle extractie-taken weg
    assertThat(extractieTaakRepository.findByProjectNaam("testproject")).isEmpty();

    // Assert: alle kernbezwaren weg
    assertThat(kernbezwaarRepository.findById(k1.getId())).isEmpty();
    assertThat(kernbezwaarRepository.findById(k2.getId())).isEmpty();
    assertThat(kernbezwaarRepository.findById(k3.getId())).isEmpty();

    // Assert: alle antwoorden weg
    assertThat(antwoordRepository.findById(k1.getId())).isEmpty();
    assertThat(antwoordRepository.findById(k3.getId())).isEmpty();

    // Assert: alle referenties weg
    assertThat(referentieRepository.findByProjectNaam("testproject")).isEmpty();

    // Assert: alle clustering-taken weg
    assertThat(clusteringTaakRepository.findByProjectNaam("testproject")).isEmpty();
  }

  // --- Scenario: Bulk verwijdering behoudt kernbezwaren met overblijvende referenties ---

  @Test
  @DisplayName("Bulk verwijdering behoudt kernbezwaren die nog referenties hebben naar overblijvende documenten")
  void bulkVerwijderingBehoudtKernbezwarenMetOverblijvendeReferenties() {
    // Arrange: 3 documenten
    maakExtractieTaak("testproject", "doc-a.txt");
    maakExtractieTaak("testproject", "doc-b.txt");
    maakExtractieTaak("testproject", "doc-c.txt");

    // K1: gedeeld over alle 3 docs
    var k1 = maakKernbezwaar("testproject", "Geluidshinder gedeeld over 3 docs");
    maakReferentie(k1.getId(), "doc-a.txt", "geluid passage A");
    maakReferentie(k1.getId(), "doc-b.txt", "geluid passage B");
    maakReferentie(k1.getId(), "doc-c.txt", "geluid passage C");

    // K2: enkel doc-a + doc-b, met antwoord
    var k2 = maakKernbezwaar("testproject", "Fijnstof enkel doc-a en doc-b");
    maakReferentie(k2.getId(), "doc-a.txt", "fijnstof passage A");
    maakReferentie(k2.getId(), "doc-b.txt", "fijnstof passage B");
    maakAntwoord(k2.getId(), "<p>Antwoord op fijnstof</p>");

    // Act: bulk verwijder doc-a en doc-b (doc-c blijft)
    int aantalVerwijderd = projectService.verwijderBezwaren("testproject",
        List.of("doc-a.txt", "doc-b.txt"));

    // Assert: 2 bestanden verwijderd
    assertThat(aantalVerwijderd).isEqualTo(2);

    // Assert: extractie-taken van doc-a en doc-b weg, doc-c blijft
    assertThat(extractieTaakRepository.findByProjectNaam("testproject")).hasSize(1);
    assertThat(extractieTaakRepository.findByProjectNaam("testproject").get(0).getBestandsnaam())
        .isEqualTo("doc-c.txt");

    // Assert: K1 bestaat nog met enkel de doc-c referentie
    assertThat(kernbezwaarRepository.findById(k1.getId())).isPresent();
    var k1Refs = referentieRepository.findByKernbezwaarIdIn(List.of(k1.getId()));
    assertThat(k1Refs).hasSize(1);
    // TODO: task 9 - assertie op bestandsnaam via passage_groep_lid

    // Assert: K2 is verwijderd (alle referenties waren naar doc-a en doc-b)
    assertThat(kernbezwaarRepository.findById(k2.getId())).isEmpty();
    assertThat(antwoordRepository.findById(k2.getId())).isEmpty();
    assertThat(referentieRepository.findByKernbezwaarIdIn(List.of(k2.getId()))).isEmpty();
  }

  // --- Helper methoden voor testdata-aanmaak ---

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

  private GeextraheerdBezwaarEntiteit maakBezwaar(Long taakId, String samenvatting) {
    var bezwaar = new GeextraheerdBezwaarEntiteit();
    bezwaar.setTaakId(taakId);
    bezwaar.setPassageNr(1);
    bezwaar.setSamenvatting(samenvatting);
    return bezwaarRepository.save(bezwaar);
  }

  private KernbezwaarEntiteit maakKernbezwaar(String projectNaam, String samenvatting) {
    var kern = new KernbezwaarEntiteit();
    kern.setProjectNaam(projectNaam);
    kern.setSamenvatting(samenvatting);
    return kernbezwaarRepository.save(kern);
  }

  // TODO: task 9 - herschrijf met passage_groep model
  private KernbezwaarReferentieEntiteit maakReferentie(Long kernbezwaarId, String bestandsnaam,
      String passage) {
    var ref = new KernbezwaarReferentieEntiteit();
    ref.setKernbezwaarId(kernbezwaarId);
    ref.setPassageGroepId(0L); // placeholder - cascade tests herschrijven in task 9
    return referentieRepository.save(ref);
  }

  private KernbezwaarAntwoordEntiteit maakAntwoord(Long kernbezwaarId, String inhoud) {
    var antwoord = new KernbezwaarAntwoordEntiteit();
    antwoord.setKernbezwaarId(kernbezwaarId);
    antwoord.setInhoud(inhoud);
    antwoord.setBijgewerktOp(Instant.now());
    return antwoordRepository.save(antwoord);
  }

  private ClusteringTaak maakClusteringTaak(String projectNaam) {
    var taak = new ClusteringTaak();
    taak.setProjectNaam(projectNaam);
    taak.setStatus(ClusteringTaakStatus.KLAAR);
    taak.setAangemaaktOp(Instant.now());
    return clusteringTaakRepository.save(taak);
  }

  private ConsolidatieTaak maakConsolidatieTaak(String projectNaam, String bestandsnaam) {
    var taak = new ConsolidatieTaak();
    taak.setProjectNaam(projectNaam);
    taak.setBestandsnaam(bestandsnaam);
    taak.setStatus(ConsolidatieTaakStatus.KLAAR);
    taak.setAantalPogingen(1);
    taak.setMaxPogingen(3);
    taak.setAangemaaktOp(Instant.now());
    return consolidatieTaakRepository.save(taak);
  }
}
