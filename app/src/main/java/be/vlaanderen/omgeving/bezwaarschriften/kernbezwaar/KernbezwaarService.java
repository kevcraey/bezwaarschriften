package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import be.vlaanderen.omgeving.bezwaarschriften.clustering.CentroidMatchingService;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.CentroidMatchingService.Suggestie;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringConfig;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort.ClusteringInvoer;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.DimensieReductiePoort;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.EmbeddingPoort;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.PassageDeduplicatieService.DeduplicatieGroep;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractiePassageEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractiePassageRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaakRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarRepository;
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
  private final PassageDeduplicatieService deduplicatieService;
  private final PassageGroepRepository passageGroepRepository;
  private final PassageGroepLidRepository passageGroepLidRepository;

  /**
   * Constructor met alle benodigde afhankelijkheden.
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
      CentroidMatchingService centroidMatchingService,
      PassageDeduplicatieService deduplicatieService,
      PassageGroepRepository passageGroepRepository,
      PassageGroepLidRepository passageGroepLidRepository) {
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
    this.deduplicatieService = deduplicatieService;
    this.passageGroepRepository = passageGroepRepository;
    this.passageGroepLidRepository = passageGroepLidRepository;
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

    // Bepaal deduplicatiemodus op basis van ClusteringTaak-instelling
    boolean deduplicatieVoor = false;
    if (taakId != null) {
      deduplicatieVoor = clusteringTaakRepository.findById(taakId)
          .map(ClusteringTaak::isDeduplicatieVoorClustering)
          .orElse(false);
    }

    if (deduplicatieVoor) {
      clusterMetDeduplicatieVooraf(projectNaam, taakId, bezwaren,
          passageLookup, bestandsnaamLookup);
    } else {
      clusterMetDeduplicatieAchteraf(projectNaam, taakId, bezwaren,
          passageLookup, bestandsnaamLookup);
    }
  }

  /**
   * Modus A: groepeer bezwaren op passage-gelijkenis VOOR HDBSCAN.
   * Stuurt 1 representatief bezwaar per groep naar HDBSCAN.
   */
  private void clusterMetDeduplicatieVooraf(String projectNaam, Long taakId,
      List<GeextraheerdBezwaarEntiteit> bezwaren,
      Map<Long, Map<Integer, String>> passageLookup,
      Map<Long, String> bestandsnaamLookup) {

    // Groepeer bezwaren op passage-gelijkenis
    var deduplicatieGroepen = deduplicatieService.groepeer(
        bezwaren, passageLookup, bestandsnaamLookup);

    // HDBSCAN ontvangt alleen de representatieve bezwaren (1 per groep)
    var representatieven = deduplicatieGroepen.stream()
        .map(DeduplicatieGroep::representatief)
        .toList();

    var origineleInvoer = representatieven.stream()
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

    // Centroid matching voor noise-representatieven
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

    // Bouw lookup: representatief bezwaar-ID → deduplicatie-groep
    var groepPerRepresentatief = new HashMap<Long, DeduplicatieGroep>();
    for (var groep : deduplicatieGroepen) {
      groepPerRepresentatief.put(groep.representatief().getId(), groep);
    }

    // Bouw lookup van bezwaar-ID naar entiteit
    final var bezwaarById = bezwaren.stream()
        .collect(Collectors.toMap(GeextraheerdBezwaarEntiteit::getId, b -> b));

    // Sla passage-groepen en kernbezwaren op in een transactie
    transactionTemplate.executeWithoutResult(status -> {
      // Verwerk HDBSCAN-clusters
      for (var cluster : clusterResultaat.clusters()) {
        var clusterRepIds = new ArrayList<>(cluster.bezwaarIds());
        var extraIds = centroidResultaat.toegewezenPerCluster()
            .getOrDefault(cluster.label(), List.of());
        clusterRepIds.addAll(extraIds);

        // Verzamel alle bezwaren in dit cluster (via hun groepen)
        var clusterBezwaren = new ArrayList<GeextraheerdBezwaarEntiteit>();
        var clusterGroepen = new ArrayList<DeduplicatieGroep>();
        for (var repId : clusterRepIds) {
          var groep = groepPerRepresentatief.get(repId);
          if (groep != null) {
            clusterGroepen.add(groep);
            clusterBezwaren.add(bezwaarById.get(repId));
          }
        }

        // Bereken centroid in originele embedding-ruimte
        var origineleCentroid = berekenCentroid(
            clusterBezwaren.stream().map(this::geefEmbedding).toList());
        var representatief = vindDichtstBijCentroid(clusterBezwaren, origineleCentroid);
        var samenvatting = representatief.getSamenvatting();

        // Bereken score per representatief bezwaar
        var scores = new HashMap<Long, Double>();
        for (var bezwaar : clusterBezwaren) {
          scores.put(bezwaar.getId(),
              cosinusGelijkenis(geefEmbedding(bezwaar), origineleCentroid));
        }
        for (var extraId : extraIds) {
          var toewijzing = centroidResultaat.toewijzingen().get(extraId);
          if (toewijzing != null) {
            scores.put(extraId, toewijzing.score());
          }
        }

        // Persisteer passage-groepen en maak kernbezwaar-referenties
        var methoden = bepaalToewijzingsmethoden(centroidResultaat);
        var passageGroepIds = persisteerPassageGroepen(
            taakId, projectNaam, clusterGroepen, scores);
        var groepIdMethoden = koppelMethodenAanGroepen(
            clusterRepIds, passageGroepIds, groepPerRepresentatief, methoden);
        slaKernbezwaarOp(projectNaam, samenvatting, passageGroepIds, groepIdMethoden);
      }

      // Verwerk resterende noise
      if (!centroidResultaat.resterendeNoise().isEmpty()) {
        var noiseGroepen = centroidResultaat.resterendeNoise().stream()
            .map(groepPerRepresentatief::get)
            .filter(g -> g != null)
            .toList();
        var passageGroepIds = persisteerPassageGroepen(
            taakId, projectNaam, noiseGroepen, Map.of());
        slaKernbezwaarOp(projectNaam, "Niet-geclusterde bezwaren",
            passageGroepIds, Map.of());
      }
    });
  }

  /**
   * Modus B: HDBSCAN clustert alle bezwaren individueel (bestaand gedrag).
   * Daarna worden bezwaren per cluster gegroepeerd op passage-gelijkenis.
   */
  private void clusterMetDeduplicatieAchteraf(String projectNaam, Long taakId,
      List<GeextraheerdBezwaarEntiteit> bezwaren,
      Map<Long, Map<Integer, String>> passageLookup,
      Map<Long, String> bestandsnaamLookup) {

    // HDBSCAN-clustering op alle bezwaren (bestaand gedrag)
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

    // Sla kernbezwaren + passage-groepen op in een transactie
    transactionTemplate.executeWithoutResult(status -> {
      var methoden = bepaalToewijzingsmethoden(centroidResultaat);

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
        for (var extraId : extraIds) {
          var toewijzing = centroidResultaat.toewijzingen().get(extraId);
          if (toewijzing != null) {
            scores.put(extraId, toewijzing.score());
          }
        }

        // Groepeer bezwaren in dit cluster op passage-gelijkenis
        var deduplicatieGroepen = deduplicatieService.groepeer(
            clusterBezwaren, passageLookup, bestandsnaamLookup);

        var alleBezwaarIds = clusterBezwaren.stream()
            .map(GeextraheerdBezwaarEntiteit::getId).toList();
        var passageGroepIds = persisteerPassageGroepen(
            taakId, projectNaam, deduplicatieGroepen, scores);
        var groepIdMethoden = koppelMethodenAanGroepenModus(
            alleBezwaarIds, passageGroepIds, deduplicatieGroepen, methoden);
        slaKernbezwaarOp(projectNaam, samenvatting, passageGroepIds, groepIdMethoden);
      }

      // Verwerk resterende noise: niet-geclusterde bezwaren
      if (!centroidResultaat.resterendeNoise().isEmpty()) {
        var noiseBezwaren = centroidResultaat.resterendeNoise().stream()
            .map(bezwaarById::get)
            .toList();
        var deduplicatieGroepen = deduplicatieService.groepeer(
            noiseBezwaren, passageLookup, bestandsnaamLookup);
        var passageGroepIds = persisteerPassageGroepen(
            taakId, projectNaam, deduplicatieGroepen, Map.of());
        slaKernbezwaarOp(projectNaam, "Niet-geclusterde bezwaren",
            passageGroepIds, Map.of());
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

    // Haal passage-groep data op via de referenties
    var alleGroepIds = refEntiteiten.stream()
        .map(KernbezwaarReferentieEntiteit::getPassageGroepId)
        .distinct().toList();
    var groepById = passageGroepRepository.findAllById(alleGroepIds).stream()
        .collect(Collectors.toMap(PassageGroepEntiteit::getId, g -> g));

    var kernen = kernEntiteiten.stream()
        .map(ke -> {
          var refs = refPerKern.getOrDefault(ke.getId(), List.of()).stream()
              .map(re -> {
                var groep = groepById.get(re.getPassageGroepId());
                return new IndividueelBezwaarReferentie(
                    re.getId(),
                    null,
                    null,
                    groep != null ? groep.getPassage() : null,
                    groep != null ? groep.getScorePercentage() : null,
                    re.getToewijzingsmethode());
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

    // Bereken centroids per kernbezwaar via passage_groep_lid → bezwaar embeddings
    var refEntiteiten = referentieRepository.findByKernbezwaarIdIn(kernIds);
    var refsPerKern = refEntiteiten.stream()
        .collect(Collectors.groupingBy(KernbezwaarReferentieEntiteit::getKernbezwaarId));

    var alleGroepIds = refEntiteiten.stream()
        .map(KernbezwaarReferentieEntiteit::getPassageGroepId)
        .distinct().toList();
    var alleLeden = passageGroepLidRepository.findByPassageGroepIdIn(alleGroepIds);
    var ledenPerGroep = alleLeden.stream()
        .collect(Collectors.groupingBy(PassageGroepLidEntiteit::getPassageGroepId));

    var centroids = new HashMap<Long, float[]>();
    for (var kernId : kernIds) {
      var refs = refsPerKern.getOrDefault(kernId, List.of());
      var bezwaarIds = refs.stream()
          .flatMap(r -> ledenPerGroep
              .getOrDefault(r.getPassageGroepId(), List.of()).stream())
          .map(PassageGroepLidEntiteit::getBezwaarId)
          .distinct().toList();

      var bezwaren = bezwaarRepository.findAllById(bezwaarIds);
      var embeddings = bezwaren.stream()
          .map(this::geefEmbedding)
          .filter(e -> e != null)
          .toList();

      if (!embeddings.isEmpty()) {
        centroids.put(kernId, berekenCentroid(embeddings));
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
    // TODO: task 9 - cascade verwijdering aanpassen voor passage_groep model
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
    // TODO: task 9 - cascade verwijdering aanpassen voor passage_groep model
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

  private void slaKernbezwaarOp(String projectNaam, String samenvatting,
      List<Long> passageGroepIds, Map<Long, ToewijzingsMethode> groepIdMethoden) {
    var kernEntiteit = new KernbezwaarEntiteit();
    kernEntiteit.setProjectNaam(projectNaam);
    kernEntiteit.setSamenvatting(samenvatting);
    kernEntiteit = kernbezwaarRepository.save(kernEntiteit);

    for (var groepId : passageGroepIds) {
      var refEntiteit = new KernbezwaarReferentieEntiteit();
      refEntiteit.setKernbezwaarId(kernEntiteit.getId());
      refEntiteit.setPassageGroepId(groepId);
      refEntiteit.setToewijzingsmethode(
          groepIdMethoden.getOrDefault(groepId, ToewijzingsMethode.HDBSCAN));
      referentieRepository.save(refEntiteit);
    }
  }

  /**
   * Persisteert passage-groepen en hun leden naar de database.
   * Retourneert de lijst van aangemaakte groep-IDs in dezelfde volgorde.
   */
  private List<Long> persisteerPassageGroepen(Long taakId, String categorie,
      List<DeduplicatieGroep> groepen, Map<Long, Double> scores) {
    var groepIds = new ArrayList<Long>();
    for (var groep : groepen) {
      var entiteit = new PassageGroepEntiteit();
      entiteit.setClusteringTaakId(taakId != null ? taakId : 0L);
      entiteit.setPassage(groep.passage());
      entiteit.setSamenvatting(groep.samenvatting());
      entiteit.setCategorie(categorie);

      // Score voor het representatief bezwaar
      var score = scores.get(groep.representatief().getId());
      entiteit.setScorePercentage(
          score != null ? (int) Math.round(score * 100) : null);

      entiteit = passageGroepRepository.save(entiteit);

      for (var lid : groep.leden()) {
        var lidEntiteit = new PassageGroepLidEntiteit();
        lidEntiteit.setPassageGroepId(entiteit.getId());
        lidEntiteit.setBezwaarId(lid.bezwaarId());
        lidEntiteit.setBestandsnaam(lid.bestandsnaam());
        passageGroepLidRepository.save(lidEntiteit);
      }
      groepIds.add(entiteit.getId());
    }
    return groepIds;
  }

  /**
   * Bepaalt toewijzingsmethoden uit centroid-matching resultaat.
   */
  private Map<Long, ToewijzingsMethode> bepaalToewijzingsmethoden(
      CentroidMatchingService.CentroidMatchingResultaat centroidResultaat) {
    var methoden = new HashMap<Long, ToewijzingsMethode>();
    for (var entry : centroidResultaat.toewijzingen().entrySet()) {
      methoden.put(entry.getKey(), entry.getValue().methode());
    }
    return methoden;
  }

  /**
   * Koppelt toewijzingsmethoden aan passage-groep IDs voor modus A.
   * De methode van een groep is de methode van het representatief bezwaar.
   */
  private Map<Long, ToewijzingsMethode> koppelMethodenAanGroepen(
      List<Long> repIds, List<Long> passageGroepIds,
      Map<Long, DeduplicatieGroep> groepPerRepresentatief,
      Map<Long, ToewijzingsMethode> methoden) {
    var resultaat = new HashMap<Long, ToewijzingsMethode>();
    for (int i = 0; i < repIds.size(); i++) {
      var repId = repIds.get(i);
      if (i < passageGroepIds.size() && methoden.containsKey(repId)) {
        resultaat.put(passageGroepIds.get(i), methoden.get(repId));
      }
    }
    return resultaat;
  }

  /**
   * Koppelt toewijzingsmethoden aan passage-groep IDs voor modus B.
   * Zoekt per groep de meest restrictieve methode van de leden.
   */
  private Map<Long, ToewijzingsMethode> koppelMethodenAanGroepenModus(
      List<Long> bezwaarIds, List<Long> passageGroepIds,
      List<DeduplicatieGroep> groepen, Map<Long, ToewijzingsMethode> methoden) {
    var resultaat = new HashMap<Long, ToewijzingsMethode>();
    for (int i = 0; i < groepen.size() && i < passageGroepIds.size(); i++) {
      var groep = groepen.get(i);
      // Als een lid centroid-fallback is, markeer de hele groep zo
      var heeftCentroid = groep.leden().stream()
          .anyMatch(lid -> methoden.getOrDefault(lid.bezwaarId(),
              ToewijzingsMethode.HDBSCAN) == ToewijzingsMethode.CENTROID_FALLBACK);
      if (heeftCentroid) {
        resultaat.put(passageGroepIds.get(i), ToewijzingsMethode.CENTROID_FALLBACK);
      }
    }
    return resultaat;
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
