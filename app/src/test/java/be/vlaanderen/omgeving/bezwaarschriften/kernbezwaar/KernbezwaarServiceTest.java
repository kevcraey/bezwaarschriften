package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.clustering.CentroidMatchingService;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.CentroidMatchingService.CentroidMatchingResultaat;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringConfig;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort.Cluster;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort.ClusteringInvoer;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort.ClusteringResultaat;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.DimensieReductiePoort;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.EmbeddingPoort;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.PassageDeduplicatieService.DeduplicatieGroep;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.PassageDeduplicatieService.DeduplicatieLid;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractiePassageEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractiePassageRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaak;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaakRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
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
  private KernbezwaarRepository kernbezwaarRepository;

  @Mock
  private KernbezwaarReferentieRepository referentieRepository;

  @Mock
  private ClusteringTaakService clusteringTaakService;

  @Mock
  private ClusteringTaakRepository clusteringTaakRepository;

  @Mock
  private PlatformTransactionManager transactionManager;

  @Mock
  private DimensieReductiePoort dimensieReductiePoort;

  @Mock
  private CentroidMatchingService centroidMatchingService;

  @Mock
  private PassageDeduplicatieService deduplicatieService;

  @Mock
  private PassageGroepRepository passageGroepRepository;

  @Mock
  private PassageGroepLidRepository passageGroepLidRepository;

  @Spy
  private ClusteringConfig clusteringConfig = new ClusteringConfig();

  private KernbezwaarService service;

  private final AtomicLong passageGroepIdCounter = new AtomicLong(1000L);

  @BeforeEach
  void setUp() {
    lenient().when(transactionManager.getTransaction(any()))
        .thenReturn(mock(TransactionStatus.class));
    // Standaard: UMAP uitgeschakeld zodat bestaande tests ongewijzigd werken
    clusteringConfig.setUmapEnabled(false);
    passageGroepIdCounter.set(1000L);

    // Standaard: deduplicatieService geeft 1 groep per bezwaar terug
    lenient().when(deduplicatieService.groepeer(anyList(), anyMap(), anyMap()))
        .thenAnswer(inv -> {
          List<GeextraheerdBezwaarEntiteit> bezwaren = inv.getArgument(0);
          Map<Long, String> bestandsnaamLookup = inv.getArgument(2);
          return bezwaren.stream()
              .map(b -> new DeduplicatieGroep(
                  b.getSamenvatting(), b.getSamenvatting(), b,
                  List.of(new DeduplicatieLid(b.getId(),
                      bestandsnaamLookup.getOrDefault(b.getTaakId(), "onbekend")))))
              .toList();
        });

    // Standaard: passageGroepRepository.save geeft entiteit met incrementerend ID terug
    lenient().when(passageGroepRepository.save(any())).thenAnswer(inv -> {
      var e = (PassageGroepEntiteit) inv.getArgument(0);
      e.setId(passageGroepIdCounter.getAndIncrement());
      return e;
    });

    service = maakService(centroidMatchingService);
  }

  private KernbezwaarService maakService(CentroidMatchingService cms) {
    return new KernbezwaarService(
        embeddingPoort, clusteringPoort,
        bezwaarRepository, passageRepository, taakRepository,
        antwoordRepository,
        kernbezwaarRepository, referentieRepository,
        clusteringTaakService, clusteringTaakRepository,
        transactionManager, dimensieReductiePoort, clusteringConfig,
        cms, deduplicatieService, passageGroepRepository,
        passageGroepLidRepository);
  }

  @Test
  void clusterProjectClustertBezwarenSuccesvol() {
    // Arrange: 2 bezwaren in hetzelfde project, zelfde taak
    var bezwaar1 = maakBezwaar(1L, 10L, 1, "samenvatting geluid 1");
    var bezwaar2 = maakBezwaar(2L, 10L, 2, "samenvatting geluid 2");
    when(bezwaarRepository.findByProjectNaam("windmolens"))
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

    // Clustering: 1 cluster met beide bezwaren
    var cluster = new Cluster(0, List.of(1L, 2L), emb1);
    var resultaat = new ClusteringResultaat(List.of(cluster), List.of());
    when(clusteringPoort.cluster(anyList())).thenReturn(resultaat);

    // Centroid matching: geen noise, geen toewijzingen
    when(centroidMatchingService.wijsNoiseToe(anyList(), any(), anyDouble()))
        .thenReturn(new CentroidMatchingResultaat(Map.of(), List.of(), Map.of()));

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
    service.clusterProject("windmolens", null);

    // Assert
    verify(kernbezwaarRepository).deleteByProjectNaam("windmolens");
    verify(embeddingPoort, times(2)).genereerEmbeddings(anyList());
    verify(clusteringPoort).cluster(anyList());
    verify(kernbezwaarRepository).save(any());
  }

  @Test
  void noiseItemsWordenGebundeldOnderNietGeclusterdKernbezwaar() {
    // Arrange: 2 bezwaren die als noise worden geclassificeerd
    var bezwaar1 = maakBezwaar(5L, 20L, 1, "uniek bezwaar 1");
    var bezwaar2 = maakBezwaar(6L, 20L, 2, "uniek bezwaar 2");
    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar1, bezwaar2));

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

    // Centroid matching: geen clusters om naar te matchen, alles resterende noise
    when(centroidMatchingService.wijsNoiseToe(anyList(), any(), anyDouble()))
        .thenReturn(new CentroidMatchingResultaat(Map.of(), List.of(5L, 6L), Map.of()));

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
    service.clusterProject("windmolens", null);

    // Assert: kernbezwaar met samenvatting "Niet-geclusterde bezwaren" wordt aangemaakt
    var captor = ArgumentCaptor.forClass(KernbezwaarEntiteit.class);
    verify(kernbezwaarRepository).save(captor.capture());
    assertThat(captor.getValue().getSamenvatting()).isEqualTo("Niet-geclusterde bezwaren");
    assertThat(captor.getValue().getProjectNaam()).isEqualTo("windmolens");
  }

  @Test
  void laadtKernbezwarenUitDatabase() {
    var kernEntiteit = new KernbezwaarEntiteit();
    kernEntiteit.setId(20L);
    kernEntiteit.setProjectNaam("windmolens");
    kernEntiteit.setSamenvatting("Verkeershinder");
    when(kernbezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(kernEntiteit));

    var refEntiteit = new KernbezwaarReferentieEntiteit();
    refEntiteit.setId(30L);
    refEntiteit.setKernbezwaarId(20L);
    refEntiteit.setPassageGroepId(0L); // TODO: task 8
    refEntiteit.setToewijzingsmethode(ToewijzingsMethode.HDBSCAN);
    when(referentieRepository.findByKernbezwaarIdIn(List.of(20L)))
        .thenReturn(List.of(refEntiteit));

    when(antwoordRepository.findByKernbezwaarIdIn(List.of(20L)))
        .thenReturn(List.of());

    var resultaat = service.geefKernbezwaren("windmolens");

    assertThat(resultaat).isPresent();
    assertThat(resultaat.get()).hasSize(1);
    assertThat(resultaat.get().get(0).samenvatting())
        .isEqualTo("Verkeershinder");
    assertThat(resultaat.get().get(0).individueleBezwaren())
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
    var bezwaar = maakBezwaar(7L, 30L, 99, "fallback samenvatting");
    when(bezwaarRepository.findByProjectNaam("windmolens"))
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

    // Centroid matching: resterende noise
    when(centroidMatchingService.wijsNoiseToe(anyList(), any(), anyDouble()))
        .thenReturn(new CentroidMatchingResultaat(Map.of(), List.of(7L), Map.of()));

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
    service.clusterProject("windmolens", null);

    // Assert: noise items worden gebundeld onder "Niet-geclusterde bezwaren"
    var captor = ArgumentCaptor.forClass(KernbezwaarEntiteit.class);
    verify(kernbezwaarRepository).save(captor.capture());
    assertThat(captor.getValue().getSamenvatting()).isEqualTo("Niet-geclusterde bezwaren");
  }

  @Test
  void clusterProject_stoptBijAnnulering() {
    // Arrange: taak is geannuleerd
    when(clusteringTaakService.isGeannuleerd(42L)).thenReturn(true);
    var bezwaar = maakBezwaar(1L, 10L, 1, "bezwaar");
    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar));
    when(passageRepository.findByTaakId(10L)).thenReturn(List.of());
    when(taakRepository.findById(10L))
        .thenReturn(Optional.of(maakTaak(10L, "windmolens", "b.pdf")));

    float[] emb = {1.0f};
    when(embeddingPoort.genereerEmbeddings(anyList())).thenReturn(List.of(emb));
    when(bezwaarRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    // Act & Assert
    assertThatThrownBy(() -> service.clusterProject("windmolens", 42L))
        .isInstanceOf(ClusteringGeannuleerdException.class)
        .hasMessage("Clustering is geannuleerd");
  }

  @Test
  void clusterProject_clustertProjectSuccesvol() {
    // Arrange: 1 bezwaar
    var bezwaar = maakBezwaarMetEmbedding(1L, 10L, 1, "geluidshinder",
        new float[]{1.0f, 0.0f});
    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar));

    var passage = maakPassage(10L, 1, "originele geluidstekst");
    when(passageRepository.findByTaakId(10L)).thenReturn(List.of(passage));

    var taak = maakTaak(10L, "windmolens", "brief.pdf");
    when(taakRepository.findById(10L)).thenReturn(Optional.of(taak));

    // Clustering: noise item
    var resultaat = new ClusteringResultaat(List.of(), List.of(1L));
    when(clusteringPoort.cluster(anyList())).thenReturn(resultaat);

    // Centroid matching: resterende noise
    when(centroidMatchingService.wijsNoiseToe(anyList(), any(), anyDouble()))
        .thenReturn(new CentroidMatchingResultaat(Map.of(), List.of(1L), Map.of()));

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
    service.clusterProject("windmolens", null);

    verify(kernbezwaarRepository).deleteByProjectNaam("windmolens");
    verify(kernbezwaarRepository).save(any());
  }

  // TODO: task 9 - herschrijf cascade verwijdering tests met passage_groep model
  @Test
  void ruimOpNaDocumentVerwijdering_verwijdertLegeKernbezwaren() {
    service.ruimOpNaDocumentVerwijdering("windmolens", "bezwaar-001.txt");

    verify(kernbezwaarRepository).deleteZonderReferenties("windmolens");
  }

  @Test
  void ruimOpNaBestandenVerwijdering_verwijdertOrphanedData() {
    var bestandsnamen = List.of("doc-a.txt", "doc-b.txt");

    service.ruimOpNaBestandenVerwijdering("testproject", bestandsnamen);

    verify(kernbezwaarRepository).deleteZonderReferenties("testproject");
  }

  @Test
  void ruimAllesOpVoorProject_verwijdertAlleKernbezwaarEnClusteringData() {
    service.ruimAllesOpVoorProject("windmolens");

    verify(kernbezwaarRepository).deleteByProjectNaam("windmolens");
    verify(clusteringTaakRepository).deleteByProjectNaam("windmolens");
  }

  @Test
  void umapDimensieReductieWordtToegepastAlsIngeschakeld() {
    // Arrange: UMAP ingeschakeld, genoeg bezwaren (> nNeighbors + 1)
    clusteringConfig.setUmapEnabled(true);
    clusteringConfig.setUmapNNeighbors(2);

    var bezwaar1 = maakBezwaarMetEmbedding(1L, 10L, 1, "bezwaar 1",
        new float[]{1.0f, 0.0f, 0.0f});
    var bezwaar2 = maakBezwaarMetEmbedding(2L, 10L, 2, "bezwaar 2",
        new float[]{0.9f, 0.1f, 0.0f});
    var bezwaar3 = maakBezwaarMetEmbedding(3L, 10L, 3, "bezwaar 3",
        new float[]{0.8f, 0.2f, 0.0f});
    var bezwaren = List.of(bezwaar1, bezwaar2, bezwaar3);

    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(bezwaren);
    when(passageRepository.findByTaakId(10L)).thenReturn(List.of());
    when(taakRepository.findById(10L))
        .thenReturn(Optional.of(maakTaak(10L, "windmolens", "b.pdf")));

    // UMAP reduceert naar lagere dimensie
    var gereduceerd = List.of(
        new float[]{0.1f, 0.2f},
        new float[]{0.3f, 0.4f},
        new float[]{0.5f, 0.6f});
    when(dimensieReductiePoort.reduceer(anyList())).thenReturn(gereduceerd);

    // Clustering: noise items (eenvoudig scenario)
    var resultaat = new ClusteringResultaat(List.of(), List.of(1L, 2L, 3L));
    when(clusteringPoort.cluster(anyList())).thenReturn(resultaat);

    // Centroid matching: resterende noise
    when(centroidMatchingService.wijsNoiseToe(anyList(), any(), anyDouble()))
        .thenReturn(new CentroidMatchingResultaat(Map.of(), List.of(1L, 2L, 3L), Map.of()));

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
    service.clusterProject("windmolens", null);

    // Assert: UMAP werd aangeroepen
    verify(dimensieReductiePoort).reduceer(anyList());
  }

  @Test
  @SuppressWarnings("unchecked")
  void umapNoiseEmbeddingsGebruikenGereduceerdeDimensie() {
    // Reproduceert bug: "Index 5 out of bounds for length 5"
    // Bij UMAP-reductie hebben cluster-centroids een lagere dimensie (2D)
    // dan de originele embeddings (3D). Noise-embeddings moeten ook in
    // de gereduceerde ruimte zitten, anders crasht cosinusGelijkenis.
    clusteringConfig.setUmapEnabled(true);
    clusteringConfig.setUmapNNeighbors(2);

    // Originele embeddings zijn 3D
    var bezwaar1 = maakBezwaarMetEmbedding(1L, 10L, 1, "cluster-bezwaar",
        new float[]{1.0f, 0.0f, 0.0f});
    var bezwaar2 = maakBezwaarMetEmbedding(2L, 10L, 2, "noise-bezwaar",
        new float[]{0.0f, 1.0f, 0.0f});
    var bezwaar3 = maakBezwaarMetEmbedding(3L, 10L, 3, "cluster-bezwaar-2",
        new float[]{0.9f, 0.1f, 0.0f});

    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar1, bezwaar2, bezwaar3));
    when(passageRepository.findByTaakId(10L)).thenReturn(List.of());
    when(taakRepository.findById(10L))
        .thenReturn(Optional.of(maakTaak(10L, "windmolens", "b.pdf")));

    // UMAP reduceert van 3D naar 2D
    when(dimensieReductiePoort.reduceer(anyList())).thenReturn(List.of(
        new float[]{0.1f, 0.2f},
        new float[]{0.8f, 0.9f},
        new float[]{0.15f, 0.25f}));

    // Cluster met centroid in 2D, bezwaar 2 is noise
    var cluster = new Cluster(1, List.of(1L, 3L), new float[]{0.125f, 0.225f});
    var resultaat = new ClusteringResultaat(List.of(cluster), List.of(2L));
    when(clusteringPoort.cluster(anyList())).thenReturn(resultaat);

    // Gebruik ECHTE CentroidMatchingService zodat dimensie-mismatch crasht
    var echteCentroidService = new CentroidMatchingService();
    service = maakService(echteCentroidService);

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

    // Act: dit moet NIET crashen met ArrayIndexOutOfBoundsException
    service.clusterProject("windmolens", null);

    // Assert: clustering is geslaagd (geen ArrayIndexOutOfBoundsException)
    verify(kernbezwaarRepository).save(any());
  }

  @Test
  void umapDimensieReductieWordtNietToegepastAlsUitgeschakeld() {
    // Arrange: UMAP uitgeschakeld (standaard in setUp)
    var bezwaar1 = maakBezwaarMetEmbedding(1L, 10L, 1, "bezwaar 1",
        new float[]{1.0f, 0.0f, 0.0f});
    var bezwaar2 = maakBezwaarMetEmbedding(2L, 10L, 2, "bezwaar 2",
        new float[]{0.9f, 0.1f, 0.0f});

    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar1, bezwaar2));
    when(passageRepository.findByTaakId(10L)).thenReturn(List.of());
    when(taakRepository.findById(10L))
        .thenReturn(Optional.of(maakTaak(10L, "windmolens", "b.pdf")));

    var resultaat = new ClusteringResultaat(List.of(), List.of(1L, 2L));
    when(clusteringPoort.cluster(anyList())).thenReturn(resultaat);

    when(centroidMatchingService.wijsNoiseToe(anyList(), any(), anyDouble()))
        .thenReturn(new CentroidMatchingResultaat(Map.of(), List.of(1L, 2L), Map.of()));

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
    service.clusterProject("windmolens", null);

    // Assert: UMAP werd NIET aangeroepen
    verify(dimensieReductiePoort, never()).reduceer(anyList());
  }

  @Test
  void umapWordtOvergeslagenAlsTeWeinigBezwaren() {
    // Arrange: UMAP ingeschakeld maar te weinig bezwaren (< nNeighbors + 1)
    clusteringConfig.setUmapEnabled(true);
    clusteringConfig.setUmapNNeighbors(15);

    var bezwaar1 = maakBezwaarMetEmbedding(1L, 10L, 1, "bezwaar 1",
        new float[]{1.0f, 0.0f});
    var bezwaar2 = maakBezwaarMetEmbedding(2L, 10L, 2, "bezwaar 2",
        new float[]{0.9f, 0.1f});

    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar1, bezwaar2));
    when(passageRepository.findByTaakId(10L)).thenReturn(List.of());
    when(taakRepository.findById(10L))
        .thenReturn(Optional.of(maakTaak(10L, "windmolens", "b.pdf")));

    var resultaat = new ClusteringResultaat(List.of(), List.of(1L, 2L));
    when(clusteringPoort.cluster(anyList())).thenReturn(resultaat);

    when(centroidMatchingService.wijsNoiseToe(anyList(), any(), anyDouble()))
        .thenReturn(new CentroidMatchingResultaat(Map.of(), List.of(1L, 2L), Map.of()));

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
    service.clusterProject("windmolens", null);

    // Assert: UMAP werd NIET aangeroepen (te weinig bezwaren)
    verify(dimensieReductiePoort, never()).reduceer(anyList());
  }

  @Test
  void umapGebruiktOrigineleEmbeddingsVoorCentroidBerekening() {
    // Arrange: UMAP ingeschakeld, bezwaren met bekende embeddings
    clusteringConfig.setUmapEnabled(true);
    clusteringConfig.setUmapNNeighbors(2);

    float[] origEmb1 = {1.0f, 0.0f, 0.0f};
    float[] origEmb2 = {0.8f, 0.2f, 0.0f};
    float[] origEmb3 = {0.9f, 0.1f, 0.0f};

    var bezwaar1 = maakBezwaarMetEmbedding(1L, 10L, 1, "samenvatting 1", origEmb1);
    var bezwaar2 = maakBezwaarMetEmbedding(2L, 10L, 2, "samenvatting 2", origEmb2);
    var bezwaar3 = maakBezwaarMetEmbedding(3L, 10L, 3, "samenvatting 3", origEmb3);

    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar1, bezwaar2, bezwaar3));
    when(passageRepository.findByTaakId(10L)).thenReturn(List.of());
    when(taakRepository.findById(10L))
        .thenReturn(Optional.of(maakTaak(10L, "windmolens", "b.pdf")));

    // UMAP reduceert naar 2D
    when(dimensieReductiePoort.reduceer(anyList())).thenReturn(List.of(
        new float[]{0.1f, 0.2f},
        new float[]{0.3f, 0.4f},
        new float[]{0.5f, 0.6f}));

    // Clustering: 1 cluster met alle 3
    var cluster = new Cluster(0, List.of(1L, 2L, 3L), new float[]{0.3f, 0.4f});
    var resultaat = new ClusteringResultaat(List.of(cluster), List.of());
    when(clusteringPoort.cluster(anyList())).thenReturn(resultaat);

    // Centroid matching: geen noise
    when(centroidMatchingService.wijsNoiseToe(anyList(), any(), anyDouble()))
        .thenReturn(new CentroidMatchingResultaat(Map.of(), List.of(), Map.of()));

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
    service.clusterProject("windmolens", null);

    // Assert: representatief bezwaar is bezwaar3 (origEmb3={0.9,0.1,0} is dichtst bij
    // centroid in originele ruimte = gemiddelde van 3 embeddings = {0.9,0.1,0})
    var captor = ArgumentCaptor.forClass(KernbezwaarEntiteit.class);
    verify(kernbezwaarRepository).save(captor.capture());
    assertThat(captor.getValue().getSamenvatting()).isEqualTo("samenvatting 3");
  }

  @Test
  void gebruiktSamenvattingEmbeddingsBijClusterOpPassagesFalse() {
    clusteringConfig.setClusterOpPassages(false);

    var passageEmb = new float[]{1.0f, 0.0f, 0.0f};
    var samenvattingEmb = new float[]{0.0f, 1.0f, 0.0f};

    var b1 = maakBezwaar(1L, 100L, 1, "samenvatting 1");
    b1.setEmbeddingPassage(passageEmb);
    b1.setEmbeddingSamenvatting(samenvattingEmb);
    var b2 = maakBezwaar(2L, 100L, 2, "samenvatting 2");
    b2.setEmbeddingPassage(passageEmb);
    b2.setEmbeddingSamenvatting(samenvattingEmb);

    when(bezwaarRepository.findByProjectNaam("test"))
        .thenReturn(List.of(b1, b2));
    when(passageRepository.findByTaakId(100L)).thenReturn(List.of());
    when(taakRepository.findById(100L))
        .thenReturn(Optional.of(maakTaak(100L, "test", "doc.pdf")));

    var cluster = new Cluster(0, List.of(1L, 2L), samenvattingEmb);
    var resultaat = new ClusteringResultaat(List.of(cluster), List.of());
    when(clusteringPoort.cluster(anyList())).thenReturn(resultaat);

    when(centroidMatchingService.wijsNoiseToe(anyList(), any(), anyDouble()))
        .thenReturn(new CentroidMatchingResultaat(Map.of(), List.of(), Map.of()));

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

    service.clusterProject("test", null);

    // Verify clustering used samenvatting embedding
    @SuppressWarnings("unchecked")
    var invoerCaptor = ArgumentCaptor.forClass(List.class);
    verify(clusteringPoort).cluster(invoerCaptor.capture());
    @SuppressWarnings("unchecked")
    var invoer = (List<ClusteringInvoer>) invoerCaptor.getValue();
    assertArrayEquals(samenvattingEmb, invoer.get(0).embedding());
  }

  @Test
  void clusteringBerekentScorePercentageVoorClusterReferenties() {
    float[] emb1 = {1.0f, 0.0f, 0.0f};
    float[] emb2 = {0.9f, 0.1f, 0.0f};
    float[] emb3 = {0.5f, 0.5f, 0.0f};

    var bezwaar1 = maakBezwaarMetEmbedding(1L, 10L, 1, "bezwaar A", emb1);
    var bezwaar2 = maakBezwaarMetEmbedding(2L, 10L, 2, "bezwaar B", emb2);
    var bezwaar3 = maakBezwaarMetEmbedding(3L, 10L, 3, "bezwaar C", emb3);

    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar1, bezwaar2, bezwaar3));
    when(passageRepository.findByTaakId(10L)).thenReturn(List.of());
    when(taakRepository.findById(10L))
        .thenReturn(Optional.of(maakTaak(10L, "windmolens", "b.pdf")));

    // 1 cluster met alle 3
    var cluster = new Cluster(0, List.of(1L, 2L, 3L), emb1);
    var resultaat = new ClusteringResultaat(List.of(cluster), List.of());
    when(clusteringPoort.cluster(anyList())).thenReturn(resultaat);

    when(centroidMatchingService.wijsNoiseToe(anyList(), any(), anyDouble()))
        .thenReturn(new CentroidMatchingResultaat(Map.of(), List.of(), Map.of()));

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
    service.clusterProject("windmolens", null);

    // Assert: alle referenties zijn opgeslagen
    var captor = ArgumentCaptor.forClass(KernbezwaarReferentieEntiteit.class);
    verify(referentieRepository, times(3)).save(captor.capture());
    // TODO: task 8 - score zit nu op passage_groep, niet op referentie
    assertThat(captor.getAllValues()).hasSize(3);
  }

  @Test
  void noiseItemsKrijgenNullAlsScorePercentage() {
    // Arrange: 2 bezwaren als noise
    var bezwaar1 = maakBezwaarMetEmbedding(5L, 20L, 1, "noise 1",
        new float[]{0.5f, 0.5f});
    var bezwaar2 = maakBezwaarMetEmbedding(6L, 20L, 2, "noise 2",
        new float[]{0.4f, 0.6f});

    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar1, bezwaar2));
    when(passageRepository.findByTaakId(20L)).thenReturn(List.of());
    when(taakRepository.findById(20L))
        .thenReturn(Optional.of(maakTaak(20L, "windmolens", "brief.pdf")));

    // Clustering: geen clusters, 2 noise items
    var resultaat = new ClusteringResultaat(List.of(), List.of(5L, 6L));
    when(clusteringPoort.cluster(anyList())).thenReturn(resultaat);

    when(centroidMatchingService.wijsNoiseToe(anyList(), any(), anyDouble()))
        .thenReturn(new CentroidMatchingResultaat(Map.of(), List.of(5L, 6L), Map.of()));

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
    service.clusterProject("windmolens", null);

    // Assert: noise referenties zijn opgeslagen
    var captor = ArgumentCaptor.forClass(KernbezwaarReferentieEntiteit.class);
    verify(referentieRepository, times(2)).save(captor.capture());
    // TODO: task 8 - score zit nu op passage_groep, niet op referentie
    assertThat(captor.getAllValues()).hasSize(2);
  }

  @Test
  void geefKernbezwarenSorteertReferentiesOpScoreAflopend() {
    var kernEntiteit = new KernbezwaarEntiteit();
    kernEntiteit.setId(20L);
    kernEntiteit.setProjectNaam("windmolens");
    kernEntiteit.setSamenvatting("Geluidshinder");
    when(kernbezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(kernEntiteit));

    var ref1 = new KernbezwaarReferentieEntiteit();
    ref1.setId(31L);
    ref1.setKernbezwaarId(20L);
    ref1.setPassageGroepId(100L);
    ref1.setToewijzingsmethode(ToewijzingsMethode.HDBSCAN);

    var ref2 = new KernbezwaarReferentieEntiteit();
    ref2.setId(32L);
    ref2.setKernbezwaarId(20L);
    ref2.setPassageGroepId(101L);
    ref2.setToewijzingsmethode(ToewijzingsMethode.HDBSCAN);

    var ref3 = new KernbezwaarReferentieEntiteit();
    ref3.setId(33L);
    ref3.setKernbezwaarId(20L);
    ref3.setPassageGroepId(102L);
    ref3.setToewijzingsmethode(ToewijzingsMethode.HDBSCAN);

    when(referentieRepository.findByKernbezwaarIdIn(List.of(20L)))
        .thenReturn(List.of(ref1, ref2, ref3));

    // Passage groepen met verschillende scores
    var groep100 = maakPassageGroep(100L, "passage A", 85);
    var groep101 = maakPassageGroep(101L, "passage B", 92);
    var groep102 = maakPassageGroep(102L, "passage C", 78);
    when(passageGroepRepository.findAllById(anyList()))
        .thenReturn(List.of(groep100, groep101, groep102));

    when(antwoordRepository.findByKernbezwaarIdIn(List.of(20L)))
        .thenReturn(List.of());

    // Act
    var resultaat = service.geefKernbezwaren("windmolens");

    // Assert: 3 referenties gekoppeld, gesorteerd op score aflopend
    assertThat(resultaat).isPresent();
    var refs = resultaat.get().get(0).individueleBezwaren();
    assertThat(refs).hasSize(3);
    assertThat(refs.get(0).scorePercentage()).isEqualTo(92);
    assertThat(refs.get(1).scorePercentage()).isEqualTo(85);
    assertThat(refs.get(2).scorePercentage()).isEqualTo(78);
    assertThat(refs.get(0).passage()).isEqualTo("passage B");
  }

  @Test
  void geefKernbezwarenPlaatstNullScoresAchteraan() {
    var kernEntiteit = new KernbezwaarEntiteit();
    kernEntiteit.setId(20L);
    kernEntiteit.setProjectNaam("windmolens");
    kernEntiteit.setSamenvatting("Geluidshinder");
    when(kernbezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(kernEntiteit));

    var ref1 = new KernbezwaarReferentieEntiteit();
    ref1.setId(31L);
    ref1.setKernbezwaarId(20L);
    ref1.setPassageGroepId(100L);
    ref1.setToewijzingsmethode(ToewijzingsMethode.HDBSCAN);

    var ref2 = new KernbezwaarReferentieEntiteit();
    ref2.setId(32L);
    ref2.setKernbezwaarId(20L);
    ref2.setPassageGroepId(101L);
    ref2.setToewijzingsmethode(ToewijzingsMethode.HDBSCAN);

    when(referentieRepository.findByKernbezwaarIdIn(List.of(20L)))
        .thenReturn(List.of(ref1, ref2));

    // Groep 100 heeft score, groep 101 heeft null score
    var groep100 = maakPassageGroep(100L, "passage A", 75);
    var groep101 = maakPassageGroep(101L, "passage B", null);
    when(passageGroepRepository.findAllById(anyList()))
        .thenReturn(List.of(groep100, groep101));

    when(antwoordRepository.findByKernbezwaarIdIn(List.of(20L)))
        .thenReturn(List.of());

    // Act
    var resultaat = service.geefKernbezwaren("windmolens");

    // Assert: referentie met score komt eerst, null-score achteraan
    assertThat(resultaat).isPresent();
    var refs = resultaat.get().get(0).individueleBezwaren();
    assertThat(refs).hasSize(2);
    assertThat(refs.get(0).scorePercentage()).isEqualTo(75);
    assertThat(refs.get(1).scorePercentage()).isNull();
  }

  @Test
  void scoreWordtOpgeslagenBijPassageGroep() {
    // Arrange: 1 bezwaar in cluster
    float[] emb = {1.0f, 0.0f};
    var bezwaar = maakBezwaarMetEmbedding(1L, 10L, 1, "bezwaar", emb);

    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar));
    when(passageRepository.findByTaakId(10L)).thenReturn(List.of());
    when(taakRepository.findById(10L))
        .thenReturn(Optional.of(maakTaak(10L, "windmolens", "b.pdf")));

    // 1 cluster met 1 bezwaar
    var cluster = new Cluster(0, List.of(1L), emb);
    var resultaat = new ClusteringResultaat(List.of(cluster), List.of());
    when(clusteringPoort.cluster(anyList())).thenReturn(resultaat);

    when(centroidMatchingService.wijsNoiseToe(anyList(), any(), anyDouble()))
        .thenReturn(new CentroidMatchingResultaat(Map.of(), List.of(), Map.of()));

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
    service.clusterProject("windmolens", null);

    // Assert: passage groep wordt opgeslagen met score
    var groepCaptor = ArgumentCaptor.forClass(PassageGroepEntiteit.class);
    verify(passageGroepRepository).save(groepCaptor.capture());
    assertThat(groepCaptor.getValue().getScorePercentage()).isEqualTo(100);

    // Assert: referentie verwijst naar passage groep
    var refCaptor = ArgumentCaptor.forClass(KernbezwaarReferentieEntiteit.class);
    verify(referentieRepository).save(refCaptor.capture());
    assertThat(refCaptor.getValue().getPassageGroepId()).isNotNull();
  }

  @Test
  void berekenCentroidBerekentGemiddelde() {
    float[] emb1 = {1.0f, 0.0f};
    float[] emb2 = {0.0f, 1.0f};

    var centroid = service.berekenCentroid(List.of(emb1, emb2));

    assertThat(centroid[0]).isEqualTo(0.5f);
    assertThat(centroid[1]).isEqualTo(0.5f);
  }

  @Test
  void clusterProject_metDeduplicatie_groepeertIdentiekePassages() {
    // Arrange: 3 bezwaren, waarvan 2 met identieke passage → 2 passage-groepen
    var bezwaar1 = maakBezwaarMetEmbedding(1L, 10L, 1, "geluidshinder A",
        new float[]{1.0f, 0.0f});
    var bezwaar2 = maakBezwaarMetEmbedding(2L, 10L, 2, "geluidshinder B",
        new float[]{0.9f, 0.1f});
    var bezwaar3 = maakBezwaarMetEmbedding(3L, 10L, 3, "verkeerslast",
        new float[]{0.0f, 1.0f});
    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar1, bezwaar2, bezwaar3));

    when(passageRepository.findByTaakId(10L)).thenReturn(List.of());
    when(taakRepository.findById(10L))
        .thenReturn(Optional.of(maakTaak(10L, "windmolens", "brief.pdf")));

    // ClusteringTaak met deduplicatieVoorClustering = true (modus A)
    var clusteringTaak = new ClusteringTaak();
    clusteringTaak.setId(42L);
    clusteringTaak.setDeduplicatieVoorClustering(true);
    when(clusteringTaakRepository.findById(42L))
        .thenReturn(Optional.of(clusteringTaak));

    // DeduplicatieService groepeert bezwaar1+2 samen, bezwaar3 apart
    when(deduplicatieService.groepeer(anyList(), anyMap(), anyMap()))
        .thenReturn(List.of(
            new DeduplicatieGroep("geluidshinder passage", "geluidshinder A", bezwaar1,
                List.of(new DeduplicatieLid(1L, "brief.pdf"),
                    new DeduplicatieLid(2L, "brief.pdf"))),
            new DeduplicatieGroep("verkeerslast passage", "verkeerslast", bezwaar3,
                List.of(new DeduplicatieLid(3L, "brief.pdf")))));

    // HDBSCAN ontvangt 2 items (representatieven) en maakt 1 cluster
    var cluster = new Cluster(0, List.of(1L, 3L), new float[]{0.5f, 0.5f});
    var clusterResultaat = new ClusteringResultaat(List.of(cluster), List.of());
    when(clusteringPoort.cluster(anyList())).thenReturn(clusterResultaat);

    when(centroidMatchingService.wijsNoiseToe(anyList(), any(), anyDouble()))
        .thenReturn(new CentroidMatchingResultaat(Map.of(), List.of(), Map.of()));

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
    service.clusterProject("windmolens", 42L);

    // Assert: HDBSCAN ontving 2 items (niet 3)
    @SuppressWarnings("unchecked")
    var invoerCaptor = ArgumentCaptor.forClass(List.class);
    verify(clusteringPoort).cluster(invoerCaptor.capture());
    @SuppressWarnings("unchecked")
    var invoer = (List<ClusteringInvoer>) invoerCaptor.getValue();
    assertThat(invoer).hasSize(2);

    // Assert: 2 passage-groepen aangemaakt
    verify(passageGroepRepository, times(2)).save(any());

    // Assert: 2 referenties (1 per passage-groep) naar 1 kernbezwaar
    verify(referentieRepository, times(2)).save(any());

    // Assert: 1 passage-groep lid met 2 leden (geluidshinder)
    // en 1 passage-groep lid met 1 lid (verkeerslast)
    verify(passageGroepLidRepository, times(3)).save(any());
  }

  @Test
  void clusterProject_zonderDeduplicatie_groepeertNaClustering() {
    // Arrange: 3 bezwaren, 2 met identieke passage
    var bezwaar1 = maakBezwaarMetEmbedding(1L, 10L, 1, "geluidshinder A",
        new float[]{1.0f, 0.0f});
    var bezwaar2 = maakBezwaarMetEmbedding(2L, 10L, 2, "geluidshinder B",
        new float[]{0.9f, 0.1f});
    var bezwaar3 = maakBezwaarMetEmbedding(3L, 10L, 3, "verkeerslast",
        new float[]{0.0f, 1.0f});
    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar1, bezwaar2, bezwaar3));

    when(passageRepository.findByTaakId(10L)).thenReturn(List.of());
    when(taakRepository.findById(10L))
        .thenReturn(Optional.of(maakTaak(10L, "windmolens", "brief.pdf")));

    // ClusteringTaak met deduplicatieVoorClustering = false (modus B)
    var clusteringTaak = new ClusteringTaak();
    clusteringTaak.setId(42L);
    clusteringTaak.setDeduplicatieVoorClustering(false);
    when(clusteringTaakRepository.findById(42L))
        .thenReturn(Optional.of(clusteringTaak));

    // DeduplicatieService wordt NA clustering aangeroepen per cluster
    // Groepeer bezwaar1+2 samen, bezwaar3 apart
    when(deduplicatieService.groepeer(anyList(), anyMap(), anyMap()))
        .thenAnswer(inv -> {
          List<GeextraheerdBezwaarEntiteit> bezwaren = inv.getArgument(0);
          Map<Long, String> bestandsnaamLookup = inv.getArgument(2);
          // Simuleer: als alle 3 in dezelfde cluster zitten, groepeer 1+2
          if (bezwaren.size() == 3) {
            return List.of(
                new DeduplicatieGroep("geluidshinder passage", "geluidshinder A",
                    bezwaren.get(0),
                    List.of(new DeduplicatieLid(bezwaren.get(0).getId(), "brief.pdf"),
                        new DeduplicatieLid(bezwaren.get(1).getId(), "brief.pdf"))),
                new DeduplicatieGroep("verkeerslast passage", "verkeerslast",
                    bezwaren.get(2),
                    List.of(new DeduplicatieLid(bezwaren.get(2).getId(), "brief.pdf"))));
          }
          return bezwaren.stream()
              .map(b -> new DeduplicatieGroep(
                  b.getSamenvatting(), b.getSamenvatting(), b,
                  List.of(new DeduplicatieLid(b.getId(),
                      bestandsnaamLookup.getOrDefault(b.getTaakId(), "onbekend")))))
              .toList();
        });

    // HDBSCAN ontvangt alle 3 bezwaren en maakt 1 cluster
    var cluster = new Cluster(0, List.of(1L, 2L, 3L), new float[]{0.5f, 0.5f});
    var clusterResultaat = new ClusteringResultaat(List.of(cluster), List.of());
    when(clusteringPoort.cluster(anyList())).thenReturn(clusterResultaat);

    when(centroidMatchingService.wijsNoiseToe(anyList(), any(), anyDouble()))
        .thenReturn(new CentroidMatchingResultaat(Map.of(), List.of(), Map.of()));

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
    service.clusterProject("windmolens", 42L);

    // Assert: HDBSCAN ontving alle 3 bezwaren
    @SuppressWarnings("unchecked")
    var invoerCaptor = ArgumentCaptor.forClass(List.class);
    verify(clusteringPoort).cluster(invoerCaptor.capture());
    @SuppressWarnings("unchecked")
    var invoer = (List<ClusteringInvoer>) invoerCaptor.getValue();
    assertThat(invoer).hasSize(3);

    // Assert: deduplicatieService werd aangeroepen NA clustering
    verify(deduplicatieService).groepeer(anyList(), anyMap(), anyMap());

    // Assert: 2 passage-groepen (geluidshinder gegroepeerd, verkeerslast apart)
    verify(passageGroepRepository, times(2)).save(any());

    // Assert: 2 referenties (1 per passage-groep)
    verify(referentieRepository, times(2)).save(any());
  }

  // --- Hulpmethoden ---

  private GeextraheerdBezwaarEntiteit maakBezwaarMetEmbedding(Long id, Long taakId,
      int passageNr, String samenvatting, float[] embedding) {
    var b = maakBezwaar(id, taakId, passageNr, samenvatting);
    b.setEmbeddingPassage(embedding);
    b.setEmbeddingSamenvatting(embedding);
    return b;
  }

  private GeextraheerdBezwaarEntiteit maakBezwaar(Long id, Long taakId,
      int passageNr, String samenvatting) {
    var b = new GeextraheerdBezwaarEntiteit();
    b.setId(id);
    b.setTaakId(taakId);
    b.setPassageNr(passageNr);
    b.setSamenvatting(samenvatting);
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

  private PassageGroepEntiteit maakPassageGroep(Long id, String passage,
      Integer scorePercentage) {
    var g = new PassageGroepEntiteit();
    g.setId(id);
    g.setPassage(passage);
    g.setSamenvatting(passage);
    g.setCategorie("test");
    g.setClusteringTaakId(0L);
    g.setScorePercentage(scorePercentage);
    return g;
  }
}
