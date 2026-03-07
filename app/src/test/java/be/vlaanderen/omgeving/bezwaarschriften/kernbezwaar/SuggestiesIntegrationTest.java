package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;

import be.vlaanderen.omgeving.bezwaarschriften.BaseBezwaarschriftenIntegrationTest;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaak;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaakRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaakStatus;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarRepository;
import com.pgvector.PGvector;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integratietest voor de centroid-berekening via native pgvector AVG()-query.
 * Draait tegen een echte PostgreSQL + pgvector via Testcontainers.
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
  @DisplayName("berekenCentroidsOpPassage geeft correcte gemiddelde embedding per kernbezwaar")
  void berekenCentroidsOpPassageGeeftCorrecteGemiddeldeEmbedding() throws SQLException {
    var taak = maakExtractieTaak("testproject", "doc.pdf");

    // 2 bezwaren met bekende embeddings
    var embedding1 = maakEmbedding(1.0f, 0.0f, 0.0f);
    var embedding2 = maakEmbedding(0.0f, 1.0f, 0.0f);
    var bezwaar1 = maakBezwaarMetEmbedding(taak.getId(), "Bezwaar 1", embedding1);
    var bezwaar2 = maakBezwaarMetEmbedding(taak.getId(), "Bezwaar 2", embedding2);

    // 1 kernbezwaar met beide bezwaren als referentie
    var kern = maakKernbezwaar("testproject", "Geluidshinder");
    maakReferentieMetBezwaar(kern.getId(), bezwaar1.getId(), "doc.pdf", "passage 1");
    maakReferentieMetBezwaar(kern.getId(), bezwaar2.getId(), "doc.pdf", "passage 2");

    // Act
    var resultaat = referentieRepository.berekenCentroidsOpPassage(List.of(kern.getId()));

    // Assert: centroid is het gemiddelde van [1,0,0,...] en [0,1,0,...] = [0.5,0.5,0,...]
    assertThat(resultaat).hasSize(1);
    var row = resultaat.get(0);
    assertThat(((Number) row[0]).longValue()).isEqualTo(kern.getId());

    var centroid = new PGvector((String) row[1]).toArray();
    assertThat(centroid[0]).isCloseTo(0.5f, org.assertj.core.data.Offset.offset(0.001f));
    assertThat(centroid[1]).isCloseTo(0.5f, org.assertj.core.data.Offset.offset(0.001f));
    assertThat(centroid[2]).isCloseTo(0.0f, org.assertj.core.data.Offset.offset(0.001f));
  }

  @Test
  @DisplayName("berekenCentroidsOpPassage geeft meerdere kernbezwaren elk hun eigen centroid")
  void berekenCentroidsVoorMeerdereKernbezwaren() throws SQLException {
    var taak = maakExtractieTaak("testproject", "doc.pdf");

    var embA = maakEmbedding(1.0f, 0.0f, 0.0f);
    var embB = maakEmbedding(0.0f, 0.0f, 1.0f);
    var bezwaarA = maakBezwaarMetEmbedding(taak.getId(), "Bezwaar A", embA);
    var bezwaarB = maakBezwaarMetEmbedding(taak.getId(), "Bezwaar B", embB);

    var kern1 = maakKernbezwaar("testproject", "Kern 1");
    maakReferentieMetBezwaar(kern1.getId(), bezwaarA.getId(), "doc.pdf", "passage A");

    var kern2 = maakKernbezwaar("testproject", "Kern 2");
    maakReferentieMetBezwaar(kern2.getId(), bezwaarB.getId(), "doc.pdf", "passage B");

    // Act
    var resultaat = referentieRepository.berekenCentroidsOpPassage(
        List.of(kern1.getId(), kern2.getId()));

    // Assert: 2 rijen, elk met eigen centroid
    assertThat(resultaat).hasSize(2);

    for (var row : resultaat) {
      var kernId = ((Number) row[0]).longValue();
      var centroid = new PGvector((String) row[1]).toArray();
      if (kernId == kern1.getId()) {
        assertThat(centroid[0]).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(0.001f));
      } else {
        assertThat(centroid[2]).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(0.001f));
      }
    }
  }

  @Test
  @DisplayName("berekenCentroidsOpPassage slaat kernbezwaren zonder bezwaar-referentie over")
  void centroidsSlaatKernbezwarenZonderBezwaarOver() {
    var kern = maakKernbezwaar("testproject", "Lege kern");
    // Referentie zonder bezwaar_id (null)
    var ref = new KernbezwaarReferentieEntiteit();
    ref.setKernbezwaarId(kern.getId());
    ref.setBezwaarId(null);
    ref.setBestandsnaam("doc.pdf");
    ref.setPassage("passage zonder bezwaar");
    referentieRepository.save(ref);

    var resultaat = referentieRepository.berekenCentroidsOpPassage(List.of(kern.getId()));

    // JOIN valt weg als bezwaar_id null is -> geen rij
    assertThat(resultaat).isEmpty();
  }

  // --- Helpers ---

  private float[] maakEmbedding(float... eersteWaarden) {
    var embedding = new float[1024];
    System.arraycopy(eersteWaarden, 0, embedding, 0, eersteWaarden.length);
    return embedding;
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

  private GeextraheerdBezwaarEntiteit maakBezwaarMetEmbedding(Long taakId, String samenvatting,
      float[] embedding) {
    var bezwaar = new GeextraheerdBezwaarEntiteit();
    bezwaar.setTaakId(taakId);
    bezwaar.setPassageNr(1);
    bezwaar.setSamenvatting(samenvatting);
    bezwaar.setEmbeddingPassage(embedding);
    bezwaar.setEmbeddingSamenvatting(embedding);
    return bezwaarRepository.save(bezwaar);
  }

  private KernbezwaarEntiteit maakKernbezwaar(String projectNaam, String samenvatting) {
    var kern = new KernbezwaarEntiteit();
    kern.setProjectNaam(projectNaam);
    kern.setSamenvatting(samenvatting);
    return kernbezwaarRepository.save(kern);
  }

  private KernbezwaarReferentieEntiteit maakReferentieMetBezwaar(Long kernbezwaarId, Long bezwaarId,
      String bestandsnaam, String passage) {
    var ref = new KernbezwaarReferentieEntiteit();
    ref.setKernbezwaarId(kernbezwaarId);
    ref.setBezwaarId(bezwaarId);
    ref.setBestandsnaam(bestandsnaam);
    ref.setPassage(passage);
    return referentieRepository.save(ref);
  }
}
