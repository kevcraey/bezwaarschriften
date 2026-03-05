package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort.Cluster;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort.ClusteringResultaat;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.EmbeddingPoort;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractiePassageEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractiePassageRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaak;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaakRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class KernbezwaarServiceTest {

  @Mock
  private EmbeddingPoort embeddingPoort;

  @Mock
  private ClusteringPoort clusteringPoort;

  @Mock
  private GeextraheerdBezwaarRepository bezwaarRepository;

  @Mock
  private ExtractiePassageRepository passageRepository;

  @Mock
  private ExtractieTaakRepository taakRepository;

  @Mock
  private KernbezwaarAntwoordRepository antwoordRepository;

  @Mock
  private ThemaRepository themaRepository;

  @Mock
  private KernbezwaarRepository kernbezwaarRepository;

  @Mock
  private KernbezwaarReferentieRepository referentieRepository;

  @Mock
  private ClusteringTaakService clusteringTaakService;

  @Mock
  private ClusteringTaakRepository clusteringTaakRepository;

  @Mock
  private PlatformTransactionManager transactionManager;

  private KernbezwaarService service;

  @BeforeEach
  void setUp() {
    lenient().when(transactionManager.getTransaction(any()))
        .thenReturn(mock(TransactionStatus.class));
    service = new KernbezwaarService(
        embeddingPoort, clusteringPoort,
        bezwaarRepository, passageRepository, taakRepository,
        antwoordRepository, themaRepository,
        kernbezwaarRepository, referentieRepository,
        clusteringTaakService, clusteringTaakRepository,
        transactionManager);
  }

  @Test
  void groepeertBezwarenPerCategorie() {
    // Arrange: 2 bezwaren in dezelfde categorie "Geluid", zelfde taak
    var bezwaar1 = maakBezwaar(1L, 10L, 1, "samenvatting geluid 1", "Geluid");
    var bezwaar2 = maakBezwaar(2L, 10L, 2, "samenvatting geluid 2", "Geluid");
    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar1, bezwaar2));
    when(bezwaarRepository.findByProjectNaamAndCategorie("windmolens", "Geluid"))
        .thenReturn(List.of(bezwaar1, bezwaar2));

    // Passages: originele tekst voor elke passage
    var passage1 = maakPassage(10L, 1, "originele tekst passage 1");
    var passage2 = maakPassage(10L, 2, "originele tekst passage 2");
    when(passageRepository.findByTaakId(10L)).thenReturn(List.of(passage1, passage2));

    // Taak: bestandsnaam
    var taak = maakTaak(10L, "windmolens", "bezwaar1.pdf");
    when(taakRepository.findById(10L)).thenReturn(Optional.of(taak));

    // Embeddings: retourneer 2 vectoren
    float[] emb1 = {1.0f, 0.0f, 0.0f};
    float[] emb2 = {0.9f, 0.1f, 0.0f};
    when(embeddingPoort.genereerEmbeddings(anyList())).thenReturn(List.of(emb1, emb2));
    when(bezwaarRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    // Clustering: 1 cluster met beide bezwaren, centroid = emb1
    var cluster = new Cluster(0, List.of(1L, 2L), emb1);
    var resultaat = new ClusteringResultaat(List.of(cluster), List.of());
    when(clusteringPoort.cluster(anyList())).thenReturn(resultaat);

    // Thema + kernbezwaar opslag
    when(themaRepository.save(any())).thenAnswer(inv -> {
      var e = (ThemaEntiteit) inv.getArgument(0);
      e.setId(100L);
      return e;
    });
    when(kernbezwaarRepository.save(any())).thenAnswer(inv -> {
      var e = (KernbezwaarEntiteit) inv.getArgument(0);
      e.setId(200L);
      return e;
    });
    when(referentieRepository.save(any())).thenAnswer(inv -> {
      var e = (KernbezwaarReferentieEntiteit) inv.getArgument(0);
      e.setId(300L);
      return e;
    });

    // Act
    var themas = service.groepeer("windmolens");

    // Assert
    assertThat(themas).hasSize(1);
    assertThat(themas.get(0).naam()).isEqualTo("Geluid");
    assertThat(themas.get(0).kernbezwaren()).hasSize(1);
    assertThat(themas.get(0).kernbezwaren().get(0).id()).isEqualTo(200L);
    // Samenvatting is die van het bezwaar dichtst bij de centroid
    assertThat(themas.get(0).kernbezwaren().get(0).samenvatting())
        .isEqualTo("samenvatting geluid 1");
    // Referenties bevatten beide bezwaren
    assertThat(themas.get(0).kernbezwaren().get(0).individueleBezwaren()).hasSize(2);
    verify(themaRepository).deleteByProjectNaam("windmolens");
    verify(embeddingPoort).genereerEmbeddings(anyList());
    verify(clusteringPoort).cluster(anyList());
  }

  @Test
  void noiseItemsWordenGebundeldOnderEenKernbezwaar() {
    // Arrange: 2 bezwaren die als noise worden geclassificeerd
    var bezwaar1 = maakBezwaar(5L, 20L, 1, "uniek bezwaar 1", "Mobiliteit");
    var bezwaar2 = maakBezwaar(6L, 20L, 2, "uniek bezwaar 2", "Mobiliteit");
    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar1, bezwaar2));
    when(bezwaarRepository.findByProjectNaamAndCategorie("windmolens", "Mobiliteit"))
        .thenReturn(List.of(bezwaar1, bezwaar2));

    // Passages: originele tekst
    var passage1 = maakPassage(20L, 1, "originele noise tekst 1");
    var passage2 = maakPassage(20L, 2, "originele noise tekst 2");
    when(passageRepository.findByTaakId(20L)).thenReturn(List.of(passage1, passage2));

    var taak = maakTaak(20L, "windmolens", "brief.pdf");
    when(taakRepository.findById(20L)).thenReturn(Optional.of(taak));

    float[] emb1 = {0.5f, 0.5f};
    float[] emb2 = {0.4f, 0.6f};
    when(embeddingPoort.genereerEmbeddings(anyList())).thenReturn(List.of(emb1, emb2));
    when(bezwaarRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    // Clustering: geen clusters, 2 noise items
    var resultaat = new ClusteringResultaat(List.of(), List.of(5L, 6L));
    when(clusteringPoort.cluster(anyList())).thenReturn(resultaat);

    when(themaRepository.save(any())).thenAnswer(inv -> {
      var e = (ThemaEntiteit) inv.getArgument(0);
      e.setId(100L);
      return e;
    });
    when(kernbezwaarRepository.save(any())).thenAnswer(inv -> {
      var e = (KernbezwaarEntiteit) inv.getArgument(0);
      e.setId(500L);
      return e;
    });
    when(referentieRepository.save(any())).thenAnswer(inv -> {
      var e = (KernbezwaarReferentieEntiteit) inv.getArgument(0);
      e.setId(600L);
      return e;
    });

    // Act
    var themas = service.groepeer("windmolens");

    // Assert: alle noise items onder 1 kernbezwaar
    assertThat(themas).hasSize(1);
    assertThat(themas.get(0).naam()).isEqualTo("Mobiliteit");
    assertThat(themas.get(0).kernbezwaren()).hasSize(1);
    assertThat(themas.get(0).kernbezwaren().get(0).samenvatting())
        .isEqualTo("Niet-geclusterde bezwaren");
    assertThat(themas.get(0).kernbezwaren().get(0).individueleBezwaren()).hasSize(2);
  }

  @Test
  void laadtKernbezwarenUitDatabase() {
    var themaEntiteit = new ThemaEntiteit();
    themaEntiteit.setId(10L);
    themaEntiteit.setProjectNaam("windmolens");
    themaEntiteit.setNaam("Mobiliteit");
    when(themaRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(themaEntiteit));

    var kernEntiteit = new KernbezwaarEntiteit();
    kernEntiteit.setId(20L);
    kernEntiteit.setThemaId(10L);
    kernEntiteit.setSamenvatting("Verkeershinder");
    when(kernbezwaarRepository.findByThemaIdIn(List.of(10L)))
        .thenReturn(List.of(kernEntiteit));

    var refEntiteit = new KernbezwaarReferentieEntiteit();
    refEntiteit.setId(30L);
    refEntiteit.setKernbezwaarId(20L);
    refEntiteit.setBezwaarId(1L);
    refEntiteit.setBestandsnaam("b1.txt");
    refEntiteit.setPassage("passage");
    when(referentieRepository.findByKernbezwaarIdIn(List.of(20L)))
        .thenReturn(List.of(refEntiteit));

    when(antwoordRepository.findByKernbezwaarIdIn(List.of(20L)))
        .thenReturn(List.of());

    var resultaat = service.geefKernbezwaren("windmolens");

    assertThat(resultaat).isPresent();
    assertThat(resultaat.get()).hasSize(1);
    assertThat(resultaat.get().get(0).naam()).isEqualTo("Mobiliteit");
    assertThat(resultaat.get().get(0).kernbezwaren().get(0).samenvatting())
        .isEqualTo("Verkeershinder");
    assertThat(resultaat.get().get(0).kernbezwaren().get(0).individueleBezwaren())
        .hasSize(1);
  }

  @Test
  void slaatAntwoordOp() {
    var entiteit = new KernbezwaarAntwoordEntiteit();
    when(antwoordRepository.save(any())).thenReturn(entiteit);

    service.slaAntwoordOp(42L, "<p>Het weerwoord</p>");

    var captor = ArgumentCaptor.forClass(KernbezwaarAntwoordEntiteit.class);
    verify(antwoordRepository).save(captor.capture());
    assertThat(captor.getValue().getKernbezwaarId()).isEqualTo(42L);
    assertThat(captor.getValue().getInhoud()).isEqualTo("<p>Het weerwoord</p>");
    assertThat(captor.getValue().getBijgewerktOp()).isNotNull();
  }

  @Test
  void gebruiktSamenvattingAlsPassageNietGevonden() {
    // Arrange: bezwaar met passageNr die niet in passages voorkomt
    var bezwaar = maakBezwaar(7L, 30L, 99, "fallback samenvatting", "Natuur");
    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar));
    when(bezwaarRepository.findByProjectNaamAndCategorie("windmolens", "Natuur"))
        .thenReturn(List.of(bezwaar));

    // Geen passages voor dit taakId
    when(passageRepository.findByTaakId(30L)).thenReturn(List.of());

    var taak = maakTaak(30L, "windmolens", "doc.pdf");
    when(taakRepository.findById(30L)).thenReturn(Optional.of(taak));

    float[] emb = {1.0f};
    when(embeddingPoort.genereerEmbeddings(anyList())).thenReturn(List.of(emb));
    when(bezwaarRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    // Noise: 1 item
    var resultaat = new ClusteringResultaat(List.of(), List.of(7L));
    when(clusteringPoort.cluster(anyList())).thenReturn(resultaat);

    when(themaRepository.save(any())).thenAnswer(inv -> {
      var e = (ThemaEntiteit) inv.getArgument(0);
      e.setId(100L);
      return e;
    });
    when(kernbezwaarRepository.save(any())).thenAnswer(inv -> {
      var e = (KernbezwaarEntiteit) inv.getArgument(0);
      e.setId(700L);
      return e;
    });
    when(referentieRepository.save(any())).thenAnswer(inv -> {
      var e = (KernbezwaarReferentieEntiteit) inv.getArgument(0);
      e.setId(800L);
      return e;
    });

    // Act
    var themas = service.groepeer("windmolens");

    // Assert: noise items worden gebundeld onder "Niet-geclusterde bezwaren"
    assertThat(themas).hasSize(1);
    assertThat(themas.get(0).kernbezwaren().get(0).samenvatting())
        .isEqualTo("Niet-geclusterde bezwaren");
  }

  @Test
  void clusterEenCategorie_stoptBijAnnulering() {
    // Arrange: taak is geannuleerd
    when(clusteringTaakService.isGeannuleerd(42L)).thenReturn(true);
    when(bezwaarRepository.findByProjectNaamAndCategorie("windmolens", "Geluid"))
        .thenReturn(List.of(maakBezwaar(1L, 10L, 1, "bezwaar", "Geluid")));
    when(passageRepository.findByTaakId(10L)).thenReturn(List.of());
    when(taakRepository.findById(10L)).thenReturn(Optional.of(maakTaak(10L, "windmolens", "b.pdf")));

    // Act & Assert
    assertThatThrownBy(() -> service.clusterEenCategorie("windmolens", "Geluid", 42L))
        .isInstanceOf(ClusteringGeannuleerdException.class)
        .hasMessage("Clustering is geannuleerd");
  }

  @Test
  void clusterEenCategorie_clustertEenCategorieSuccesvol() {
    // Arrange: 1 bezwaar in categorie "Geluid"
    var bezwaar = maakBezwaar(1L, 10L, 1, "geluidshinder", "Geluid");
    when(bezwaarRepository.findByProjectNaamAndCategorie("windmolens", "Geluid"))
        .thenReturn(List.of(bezwaar));

    var passage = maakPassage(10L, 1, "originele geluidstekst");
    when(passageRepository.findByTaakId(10L)).thenReturn(List.of(passage));

    var taak = maakTaak(10L, "windmolens", "brief.pdf");
    when(taakRepository.findById(10L)).thenReturn(Optional.of(taak));

    float[] emb = {1.0f, 0.0f};
    when(embeddingPoort.genereerEmbeddings(anyList())).thenReturn(List.of(emb));
    when(bezwaarRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    // Clustering: noise item
    var resultaat = new ClusteringResultaat(List.of(), List.of(1L));
    when(clusteringPoort.cluster(anyList())).thenReturn(resultaat);

    when(themaRepository.save(any())).thenAnswer(inv -> {
      var e = (ThemaEntiteit) inv.getArgument(0);
      e.setId(100L);
      return e;
    });
    when(kernbezwaarRepository.save(any())).thenAnswer(inv -> {
      var e = (KernbezwaarEntiteit) inv.getArgument(0);
      e.setId(200L);
      return e;
    });
    when(referentieRepository.save(any())).thenAnswer(inv -> {
      var e = (KernbezwaarReferentieEntiteit) inv.getArgument(0);
      e.setId(300L);
      return e;
    });

    // taakId = null: geen annuleringscontrole
    var thema = service.clusterEenCategorie("windmolens", "Geluid", null);

    assertThat(thema.naam()).isEqualTo("Geluid");
    assertThat(thema.kernbezwaren()).hasSize(1);
    assertThat(thema.kernbezwaren().get(0).samenvatting())
        .isEqualTo("Niet-geclusterde bezwaren");
    verify(themaRepository).deleteByProjectNaamAndNaam("windmolens", "Geluid");
  }

  @Test
  void ruimOpNaDocumentVerwijdering_verwijdertReferentiesEnLegeKernbezwarenEnThemasEnClusteringTaken() {
    service.ruimOpNaDocumentVerwijdering("windmolens", "bezwaar-001.txt");

    verify(referentieRepository).deleteByBestandsnaamAndProjectNaam("bezwaar-001.txt", "windmolens");
    verify(kernbezwaarRepository).deleteZonderReferenties("windmolens");
    verify(themaRepository).deleteZonderKernbezwaren("windmolens");
    verify(clusteringTaakRepository).deleteZonderThema("windmolens");
  }

  @Test
  void ruimOpNaDocumentVerwijdering_roeptStappenInJuisteVolgordeAan() {
    var inOrder = inOrder(referentieRepository, kernbezwaarRepository,
        themaRepository, clusteringTaakRepository);

    service.ruimOpNaDocumentVerwijdering("windmolens", "bezwaar-001.txt");

    inOrder.verify(referentieRepository).deleteByBestandsnaamAndProjectNaam("bezwaar-001.txt", "windmolens");
    inOrder.verify(kernbezwaarRepository).deleteZonderReferenties("windmolens");
    inOrder.verify(themaRepository).deleteZonderKernbezwaren("windmolens");
    inOrder.verify(clusteringTaakRepository).deleteZonderThema("windmolens");
  }

  @Test
  void ruimAllesOpVoorProject_verwijdertAlleKernbezwaarEnClusteringData() {
    service.ruimAllesOpVoorProject("windmolens");

    verify(themaRepository).deleteByProjectNaam("windmolens");
    verify(clusteringTaakRepository).deleteByProjectNaam("windmolens");
  }

  // --- Hulpmethoden ---

  private GeextraheerdBezwaarEntiteit maakBezwaar(Long id, Long taakId,
      int passageNr, String samenvatting, String categorie) {
    var b = new GeextraheerdBezwaarEntiteit();
    b.setId(id);
    b.setTaakId(taakId);
    b.setPassageNr(passageNr);
    b.setSamenvatting(samenvatting);
    b.setCategorie(categorie);
    return b;
  }

  private ExtractiePassageEntiteit maakPassage(Long taakId, int passageNr,
      String tekst) {
    var p = new ExtractiePassageEntiteit();
    p.setTaakId(taakId);
    p.setPassageNr(passageNr);
    p.setTekst(tekst);
    return p;
  }

  private ExtractieTaak maakTaak(Long id, String projectNaam,
      String bestandsnaam) {
    var t = new ExtractieTaak();
    t.setId(id);
    t.setProjectNaam(projectNaam);
    t.setBestandsnaam(bestandsnaam);
    return t;
  }
}
