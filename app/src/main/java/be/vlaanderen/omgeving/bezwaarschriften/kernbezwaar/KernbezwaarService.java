package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import be.vlaanderen.omgeving.bezwaarschriften.clustering.CentroidMatchingService;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.CentroidMatchingService.Suggestie;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.CentroidMatchingService.Toewijzing;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringConfig;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort.ClusteringInvoer;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.DimensieReductiePoort;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.EmbeddingPoort;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractiePassageEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractiePassageRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaakRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarRepository;
import com.pgvector.PGvector;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestreert de clustering van individuele bezwaren tot kernbezwaren
 * via HDBSCAN-clustering op embedding-vectoren, met centroid-matching
 * als post-processing voor noise-bezwaren.
 */
@Service
public class KernbezwaarService {

  private final EmbeddingPoort embeddingPoort;
  private final ClusteringPoort clusteringPoort;
  private final GeextraheerdBezwaarRepository bezwaarRepository;
  private final ExtractiePassageRepository passageRepository;
  private final ExtractieTaakRepository taakRepository;
  private final KernbezwaarAntwoordRepository antwoordRepository;
  private final KernbezwaarRepository kernbezwaarRepository;
  private final KernbezwaarReferentieRepository referentieRepository;
  private final ClusteringTaakService clusteringTaakService;
  private final ClusteringTaakRepository clusteringTaakRepository;
  private final TransactionTemplate transactionTemplate;
  private final DimensieReductiePoort dimensieReductiePoort;
  private final ClusteringConfig clusteringConfig;
  private final CentroidMatchingService centroidMatchingService;

  /**
   * Constructor met alle benodigde afhankelijkheden.
   *
   * @param embeddingPoort port voor het genereren van embeddings
   * @param clusteringPoort port voor HDBSCAN-clustering
   * @param bezwaarRepository repository voor geextraheerde bezwaren
   * @param passageRepository repository voor extractie-passages
   * @param taakRepository repository voor extractie-taken
   * @param antwoordRepository repository voor kernbezwaar-antwoorden
   * @param kernbezwaarRepository repository voor kernbezwaren
   * @param referentieRepository repository voor kernbezwaar-referenties
   * @param clusteringTaakService service voor clustering-taak levenscyclus
   * @param clusteringTaakRepository repository voor clustering-taken
   * @param transactionManager transaction manager voor korte transactieblokken
   * @param dimensieReductiePoort port voor optionele UMAP-dimensiereductie
   * @param clusteringConfig configuratie voor clustering-parameters
   * @param centroidMatchingService service voor centroid-matching van noise
   */
  public KernbezwaarService(EmbeddingPoort embeddingPoort,
      ClusteringPoort clusteringPoort,
      GeextraheerdBezwaarRepository bezwaarRepository,
      ExtractiePassageRepository passageRepository,
      ExtractieTaakRepository taakRepository,
      KernbezwaarAntwoordRepository antwoordRepository,
      KernbezwaarRepository kernbezwaarRepository,
      KernbezwaarReferentieRepository referentieRepository,
      ClusteringTaakService clusteringTaakService,
      ClusteringTaakRepository clusteringTaakRepository,
      PlatformTransactionManager transactionManager,
      DimensieReductiePoort dimensieReductiePoort,
      ClusteringConfig clusteringConfig,
      CentroidMatchingService centroidMatchingService) {
    this.embeddingPoort = embeddingPoort;
    this.clusteringPoort = clusteringPoort;
    this.bezwaarRepository = bezwaarRepository;
    this.passageRepository = passageRepository;
    this.taakRepository = taakRepository;
    this.antwoordRepository = antwoordRepository;
    this.kernbezwaarRepository = kernbezwaarRepository;
    this.referentieRepository = referentieRepository;
    this.clusteringTaakService = clusteringTaakService;
    this.clusteringTaakRepository = clusteringTaakRepository;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.dimensieReductiePoort = dimensieReductiePoort;
    this.clusteringConfig = clusteringConfig;
    this.centroidMatchingService = centroidMatchingService;
  }

  /**
   * Clustert alle individuele bezwaren van een project tot kernbezwaren
   * via embedding-generatie, HDBSCAN-clustering en centroid-matching
   * voor noise-bezwaren.
   *
   * @param projectNaam naam van het project
   * @param taakId optioneel ID van de clustering-taak (null bij synchrone clustering)
   * @throws ClusteringGeannuleerdException als de taak geannuleerd is
   */
  public void clusterProject(String projectNaam, Long taakId) {
    var bezwaren = bezwaarRepository.findByProjectNaam(projectNaam);

    // Bouw lookups voor originele passage-teksten en bestandsnamen
    var taakIds = bezwaren.stream()
        .map(GeextraheerdBezwaarEntiteit::getTaakId)
        .distinct()
        .toList();
    final var passageLookup = bouwPassageLookup(taakIds);
    final var bestandsnaamLookup = bouwBestandsnaamLookup(taakIds);

    // Verwijder bestaande kernbezwaar-data in eigen transactie
    transactionTemplate.executeWithoutResult(status ->
        kernbezwaarRepository.deleteByProjectNaam(projectNaam));

    // Genereer embeddings alleen voor bezwaren die er nog geen hebben (legacy data)
    var zonderEmbedding = bezwaren.stream()
        .filter(b -> b.getEmbeddingPassage() == null)
        .toList();
    if (!zonderEmbedding.isEmpty()) {
      var passageTeksten = zonderEmbedding.stream()
          .map(b -> geefPassageTekst(b, passageLookup))
          .toList();
      var samenvattingen = zonderEmbedding.stream()
          .map(GeextraheerdBezwaarEntiteit::getSamenvatting)
          .toList();
      var passageEmbeddings = embeddingPoort.genereerEmbeddings(passageTeksten);
      var samenvattingEmbeddings = embeddingPoort.genereerEmbeddings(samenvattingen);
      transactionTemplate.executeWithoutResult(status -> {
        for (int i = 0; i < zonderEmbedding.size(); i++) {
          zonderEmbedding.get(i).setEmbeddingPassage(passageEmbeddings.get(i));
          zonderEmbedding.get(i).setEmbeddingSamenvatting(samenvattingEmbeddings.get(i));
        }
        bezwaarRepository.saveAll(zonderEmbedding);
      });
    }

    // Controleer annulering na embedding-generatie
    if (taakId != null && clusteringTaakService.isGeannuleerd(taakId)) {
      throw new ClusteringGeannuleerdException();
    }

    // HDBSCAN-clustering (buiten transactie)
    var origineleInvoer = bezwaren.stream()
        .map(b -> new ClusteringInvoer(b.getId(), geefEmbedding(b)))
        .toList();

    // Optionele UMAP-dimensiereductie
    var clusterInvoer = origineleInvoer;
    if (clusteringConfig.isUmapEnabled()
        && origineleInvoer.size() >= clusteringConfig.getUmapNNeighbors() + 1) {
      var vectoren = origineleInvoer.stream()
          .map(ClusteringInvoer::embedding).toList();
      var gereduceerd = dimensieReductiePoort.reduceer(vectoren);
      clusterInvoer = IntStream.range(0, origineleInvoer.size())
          .mapToObj(i -> new ClusteringInvoer(
              origineleInvoer.get(i).bezwaarId(), gereduceerd.get(i)))
          .toList();
    }

    var clusterResultaat = clusteringPoort.cluster(clusterInvoer);

    // Centroid matching: wijs noise-bezwaren toe aan clusters
    var bezwaarById = bezwaren.stream()
        .collect(Collectors.toMap(GeextraheerdBezwaarEntiteit::getId, b -> b));

    // Gebruik embeddings uit clusterInvoer (UMAP-gereduceerd indien actief)
    // zodat noise-embeddings dezelfde dimensie hebben als cluster-centroids
    var clusterEmbeddingLookup = new HashMap<Long, float[]>();
    for (var ci : clusterInvoer) {
      clusterEmbeddingLookup.put(ci.bezwaarId(), ci.embedding());
    }

    var noiseEmbeddings = new HashMap<Long, float[]>();
    for (var noiseId : clusterResultaat.noiseIds()) {
      var embedding = clusterEmbeddingLookup.get(noiseId);
      if (embedding != null) {
        noiseEmbeddings.put(noiseId, embedding);
      }
    }

    var centroidResultaat = centroidMatchingService.wijsNoiseToe(
        clusterResultaat.clusters(), noiseEmbeddings,
        clusteringConfig.getCentroidMatchingThreshold());

    // Sla kernbezwaren + referenties op in een transactie
    transactionTemplate.executeWithoutResult(status -> {
      // Bereken toewijzingsmethoden: HDBSCAN voor cluster-leden,
      // CENTROID_FALLBACK voor centroid-matched noise
      var methoden = new HashMap<Long, ToewijzingsMethode>();
      for (var toewijzing : centroidResultaat.toewijzingen().entrySet()) {
        methoden.put(toewijzing.getKey(), toewijzing.getValue().methode());
      }

      // Verwerk HDBSCAN-clusters
      for (var cluster : clusterResultaat.clusters()) {
        var clusterBezwaren = new ArrayList<>(cluster.bezwaarIds().stream()
            .map(bezwaarById::get)
            .toList());

        // Voeg centroid-matched noise toe aan dit cluster
        var extraIds = centroidResultaat.toegewezenPerCluster()
            .getOrDefault(cluster.label(), List.of());
        for (var extraId : extraIds) {
          clusterBezwaren.add(bezwaarById.get(extraId));
        }

        // Herbereken centroid in originele embedding-ruimte
        var origineleCentroid = berekenCentroid(
            clusterBezwaren.stream().map(this::geefEmbedding).toList());
        var representatief = vindDichtstBijCentroid(clusterBezwaren, origineleCentroid);
        var samenvatting = representatief.getSamenvatting();

        // Bereken score per bezwaar
        var scores = new HashMap<Long, Double>();
        for (var bezwaar : clusterBezwaren) {
          scores.put(bezwaar.getId(),
              cosinusGelijkenis(geefEmbedding(bezwaar), origineleCentroid));
        }

        // Voeg centroid-matching scores toe
        for (var extraId : extraIds) {
          var toewijzing = centroidResultaat.toewijzingen().get(extraId);
          if (toewijzing != null) {
            scores.put(extraId, toewijzing.score());
          }
        }

        var referenties = bouwReferenties(
            clusterBezwaren, passageLookup, bestandsnaamLookup, scores, methoden);
        slaKernbezwaarOp(projectNaam, samenvatting, referenties);
      }

      // Verwerk resterende noise: niet-geclusterde bezwaren
      if (!centroidResultaat.resterendeNoise().isEmpty()) {
        var noiseBezwaren = centroidResultaat.resterendeNoise().stream()
            .map(bezwaarById::get)
            .toList();
        var referenties = bouwReferenties(
            noiseBezwaren, passageLookup, bestandsnaamLookup, Map.of(), Map.of());
        slaKernbezwaarOp(projectNaam, "Niet-geclusterde bezwaren", referenties);
      }
    });
  }

  /**
   * Geeft eerder berekende kernbezwaren voor een project.
   *
   * @param projectNaam naam van het project
   * @return optional met de lijst van kernbezwaren, of leeg als nog niet geclusterd
   */
  public Optional<List<Kernbezwaar>> geefKernbezwaren(String projectNaam) {
    var kernEntiteiten = kernbezwaarRepository.findByProjectNaam(projectNaam);
    if (kernEntiteiten.isEmpty()) {
      return Optional.empty();
    }

    var kernIds = kernEntiteiten.stream().map(KernbezwaarEntiteit::getId).toList();
    var refEntiteiten = referentieRepository.findByKernbezwaarIdIn(kernIds);
    var refPerKern = refEntiteiten.stream()
        .collect(Collectors.groupingBy(KernbezwaarReferentieEntiteit::getKernbezwaarId));

    // Haal antwoorden op
    var antwoorden = antwoordRepository.findByKernbezwaarIdIn(kernIds);
    var antwoordMap = antwoorden.stream()
        .collect(Collectors.toMap(
            KernbezwaarAntwoordEntiteit::getKernbezwaarId,
            KernbezwaarAntwoordEntiteit::getInhoud));

    var kernen = kernEntiteiten.stream()
        .map(ke -> {
          var refs = refPerKern.getOrDefault(ke.getId(), List.of()).stream()
              .map(re -> {
                Integer scorePercentage = re.getScore() != null
                    ? (int) Math.round(re.getScore() * 100) : null;
                return new IndividueelBezwaarReferentie(
                    re.getId(), re.getBezwaarId(), re.getBestandsnaam(), re.getPassage(),
                    scorePercentage, re.getToewijzingsmethode());
              })
              .sorted(Comparator.comparing(
                  IndividueelBezwaarReferentie::scorePercentage,
                  Comparator.nullsLast(Comparator.reverseOrder())))
              .toList();
          return new Kernbezwaar(ke.getId(), ke.getSamenvatting(), refs,
              antwoordMap.get(ke.getId()));
        })
        .toList();

    return Optional.of(kernen);
  }

  /**
   * Geeft suggesties voor het toewijzen van een bezwaar aan een kernbezwaar.
   * Berekent de top-5 meest gelijkende kernbezwaren op basis van centroid-matching.
   *
   * @param projectNaam naam van het project
   * @param bezwaarId ID van het bezwaar
   * @return gesorteerde lijst van maximaal 5 suggesties
   */
  public List<Suggestie> geefSuggesties(String projectNaam, Long bezwaarId) {
    var bezwaar = bezwaarRepository.findById(bezwaarId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Bezwaar niet gevonden: " + bezwaarId));

    var kernEntiteiten = kernbezwaarRepository.findByProjectNaam(projectNaam);
    var kernIds = kernEntiteiten.stream()
        .filter(k -> !"Niet-geclusterde bezwaren".equals(k.getSamenvatting()))
        .map(KernbezwaarEntiteit::getId)
        .toList();
    if (kernIds.isEmpty()) {
      return List.of();
    }

    var centroidRows = clusteringConfig.isClusterOpPassages()
        ? referentieRepository.berekenCentroidsOpPassage(kernIds)
        : referentieRepository.berekenCentroidsOpSamenvatting(kernIds);

    var centroids = new HashMap<Long, float[]>();
    for (var row : centroidRows) {
      var kernId = ((Number) row[0]).longValue();
      if (row[1] != null) {
        try {
          centroids.put(kernId, new PGvector((String) row[1]).toArray());
        } catch (java.sql.SQLException e) {
          throw new IllegalStateException("Ongeldige centroid vector", e);
        }
      }
    }

    return centroidMatchingService.berekenTop5Suggesties(
        geefEmbedding(bezwaar), centroids);
  }

  /**
   * Wijst een referentie toe aan een ander kernbezwaar (handmatige toewijzing).
   * Ruimt lege kernbezwaren op na de verplaatsing.
   *
   * @param referentieId ID van de referentie
   * @param doelKernbezwaarId ID van het doel-kernbezwaar
   */
  @Transactional
  public void wijsToeAanKernbezwaar(Long referentieId, Long doelKernbezwaarId) {
    var ref = referentieRepository.findById(referentieId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Referentie niet gevonden: " + referentieId));
    ref.setKernbezwaarId(doelKernbezwaarId);
    ref.setToewijzingsmethode(ToewijzingsMethode.MANUEEL);
    referentieRepository.save(ref);

    // Ruim lege kernbezwaren op
    var kernbezwaar = kernbezwaarRepository.findById(doelKernbezwaarId).orElseThrow();
    kernbezwaarRepository.deleteZonderReferenties(kernbezwaar.getProjectNaam());
  }

  /**
   * Geeft de samenvatting van een kernbezwaar.
   *
   * @param kernbezwaarId ID van het kernbezwaar
   * @return de samenvatting, of "Onbekend" als het kernbezwaar niet gevonden is
   */
  public String geefSamenvatting(Long kernbezwaarId) {
    return kernbezwaarRepository.findById(kernbezwaarId)
        .map(KernbezwaarEntiteit::getSamenvatting)
        .orElse("Onbekend");
  }

  /**
   * Slaat een antwoord op voor een kernbezwaar.
   *
   * @param kernbezwaarId ID van het kernbezwaar
   * @param inhoud HTML-inhoud van het antwoord
   */
  public void slaAntwoordOp(Long kernbezwaarId, String inhoud) {
    if (inhoud == null || inhoud.isBlank()) {
      if (antwoordRepository.existsById(kernbezwaarId)) {
        antwoordRepository.deleteById(kernbezwaarId);
      }
      return;
    }
    var entiteit = new KernbezwaarAntwoordEntiteit();
    entiteit.setKernbezwaarId(kernbezwaarId);
    entiteit.setInhoud(inhoud);
    entiteit.setBijgewerktOp(Instant.now());
    antwoordRepository.save(entiteit);
  }

  /**
   * Ruimt kernbezwaar-data op na verwijdering van een document.
   * Verwijdert referenties voor het bestand, daarna lege kernbezwaren.
   */
  public void ruimOpNaDocumentVerwijdering(String projectNaam, String bestandsnaam) {
    referentieRepository.deleteByBestandsnaamAndProjectNaam(bestandsnaam, projectNaam);
    kernbezwaarRepository.deleteZonderReferenties(projectNaam);
  }

  /**
   * Ruimt kernbezwaar-data op na verwijdering van meerdere bestanden.
   * Verwijdert referenties voor alle bestanden, daarna lege kernbezwaren.
   *
   * @param projectNaam naam van het project
   * @param bestandsnamen lijst van bestandsnamen die verwijderd worden
   */
  public void ruimOpNaBestandenVerwijdering(String projectNaam, List<String> bestandsnamen) {
    for (String bestandsnaam : bestandsnamen) {
      referentieRepository.deleteByBestandsnaamAndProjectNaam(bestandsnaam, projectNaam);
    }
    kernbezwaarRepository.deleteZonderReferenties(projectNaam);
    // Als er geen kernbezwaren meer over zijn, ruim ook de clustering-taak op
    if (kernbezwaarRepository.countByProjectNaam(projectNaam) == 0) {
      clusteringTaakRepository.deleteByProjectNaam(projectNaam);
    }
  }

  /**
   * Ruimt alle kernbezwaar- en clusteringdata op voor een project.
   *
   * @param projectNaam naam van het project
   */
  public void ruimAllesOpVoorProject(String projectNaam) {
    kernbezwaarRepository.deleteByProjectNaam(projectNaam);
    clusteringTaakRepository.deleteByProjectNaam(projectNaam);
  }

  float[] berekenCentroid(List<float[]> embeddings) {
    int dims = embeddings.get(0).length;
    var centroid = new float[dims];
    for (var emb : embeddings) {
      for (int i = 0; i < dims; i++) {
        centroid[i] += emb[i];
      }
    }
    for (int i = 0; i < dims; i++) {
      centroid[i] /= embeddings.size();
    }
    return centroid;
  }

  private GeextraheerdBezwaarEntiteit vindDichtstBijCentroid(
      List<GeextraheerdBezwaarEntiteit> bezwaren, float[] centroid) {
    GeextraheerdBezwaarEntiteit dichtstbij = null;
    double hoogsteGelijkenis = Double.NEGATIVE_INFINITY;
    for (var bezwaar : bezwaren) {
      double gelijkenis = cosinusGelijkenis(geefEmbedding(bezwaar), centroid);
      if (gelijkenis > hoogsteGelijkenis) {
        hoogsteGelijkenis = gelijkenis;
        dichtstbij = bezwaar;
      }
    }
    return dichtstbij;
  }

  private double cosinusGelijkenis(float[] a, float[] b) {
    double dot = 0.0;
    double normA = 0.0;
    double normB = 0.0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    double deler = Math.sqrt(normA) * Math.sqrt(normB);
    return deler == 0.0 ? 0.0 : dot / deler;
  }

  float[] geefEmbedding(GeextraheerdBezwaarEntiteit bezwaar) {
    return clusteringConfig.isClusterOpPassages()
        ? bezwaar.getEmbeddingPassage()
        : bezwaar.getEmbeddingSamenvatting();
  }

  private Kernbezwaar slaKernbezwaarOp(String projectNaam, String samenvatting,
      List<IndividueelBezwaarReferentie> referenties) {
    var kernEntiteit = new KernbezwaarEntiteit();
    kernEntiteit.setProjectNaam(projectNaam);
    kernEntiteit.setSamenvatting(samenvatting);
    kernEntiteit = kernbezwaarRepository.save(kernEntiteit);

    var opgeslagenReferenties = new ArrayList<IndividueelBezwaarReferentie>();
    for (var ref : referenties) {
      var refEntiteit = new KernbezwaarReferentieEntiteit();
      refEntiteit.setKernbezwaarId(kernEntiteit.getId());
      refEntiteit.setBezwaarId(ref.bezwaarId());
      refEntiteit.setBestandsnaam(ref.bestandsnaam());
      refEntiteit.setPassage(ref.passage());
      refEntiteit.setScore(ref.scorePercentage() != null ? ref.scorePercentage() / 100.0 : null);
      refEntiteit.setToewijzingsmethode(ref.toewijzingsmethode());
      referentieRepository.save(refEntiteit);
      opgeslagenReferenties.add(ref);
    }

    return new Kernbezwaar(kernEntiteit.getId(), samenvatting,
        opgeslagenReferenties, null);
  }

  private List<IndividueelBezwaarReferentie> bouwReferenties(
      List<GeextraheerdBezwaarEntiteit> bezwaren,
      Map<Long, Map<Integer, String>> passageLookup,
      Map<Long, String> bestandsnaamLookup,
      Map<Long, Double> scores,
      Map<Long, ToewijzingsMethode> methoden) {
    return bezwaren.stream()
        .map(b -> {
          Double score = scores.get(b.getId());
          Integer scorePercentage = score != null ? (int) Math.round(score * 100) : null;
          return new IndividueelBezwaarReferentie(
              null, b.getId(),
              bestandsnaamLookup.getOrDefault(b.getTaakId(), "onbekend"),
              geefPassageTekst(b, passageLookup),
              scorePercentage,
              methoden.getOrDefault(b.getId(), ToewijzingsMethode.HDBSCAN));
        })
        .toList();
  }

  private String geefPassageTekst(GeextraheerdBezwaarEntiteit bezwaar,
      Map<Long, Map<Integer, String>> passageLookup) {
    var taakPassages = passageLookup.get(bezwaar.getTaakId());
    if (taakPassages != null) {
      var tekst = taakPassages.get(bezwaar.getPassageNr());
      if (tekst != null) {
        return tekst;
      }
    }
    return bezwaar.getSamenvatting();
  }

  private Map<Long, Map<Integer, String>> bouwPassageLookup(List<Long> taakIds) {
    var lookup = new HashMap<Long, Map<Integer, String>>();
    for (var taakId : taakIds) {
      var passages = passageRepository.findByTaakId(taakId);
      var passageMap = passages.stream()
          .collect(Collectors.toMap(
              ExtractiePassageEntiteit::getPassageNr,
              ExtractiePassageEntiteit::getTekst));
      lookup.put(taakId, passageMap);
    }
    return lookup;
  }

  private Map<Long, String> bouwBestandsnaamLookup(List<Long> taakIds) {
    var lookup = new HashMap<Long, String>();
    for (var taakId : taakIds) {
      taakRepository.findById(taakId)
          .ifPresent(taak -> lookup.put(taakId, taak.getBestandsnaam()));
    }
    return lookup;
  }
}
