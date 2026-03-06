package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestreert de clustering van individuele bezwaren tot thema's en kernbezwaren
 * via HDBSCAN-clustering op embedding-vectoren.
 */
@Service
public class KernbezwaarService {

  private final EmbeddingPoort embeddingPoort;
  private final ClusteringPoort clusteringPoort;
  private final GeextraheerdBezwaarRepository bezwaarRepository;
  private final ExtractiePassageRepository passageRepository;
  private final ExtractieTaakRepository taakRepository;
  private final KernbezwaarAntwoordRepository antwoordRepository;
  private final ThemaRepository themaRepository;
  private final KernbezwaarRepository kernbezwaarRepository;
  private final KernbezwaarReferentieRepository referentieRepository;
  private final ClusteringTaakService clusteringTaakService;
  private final ClusteringTaakRepository clusteringTaakRepository;
  private final TransactionTemplate transactionTemplate;
  private final DimensieReductiePoort dimensieReductiePoort;
  private final ClusteringConfig clusteringConfig;

  /**
   * Constructor met alle benodigde afhankelijkheden.
   *
   * @param embeddingPoort port voor het genereren van embeddings
   * @param clusteringPoort port voor HDBSCAN-clustering
   * @param bezwaarRepository repository voor geextraheerde bezwaren
   * @param passageRepository repository voor extractie-passages
   * @param taakRepository repository voor extractie-taken
   * @param antwoordRepository repository voor kernbezwaar-antwoorden
   * @param themaRepository repository voor thema's
   * @param kernbezwaarRepository repository voor kernbezwaren
   * @param referentieRepository repository voor kernbezwaar-referenties
   * @param clusteringTaakService service voor clustering-taak levenscyclus
   * @param clusteringTaakRepository repository voor clustering-taken
   * @param transactionManager transaction manager voor korte transactieblokken
   * @param dimensieReductiePoort port voor optionele UMAP-dimensiereductie
   * @param clusteringConfig configuratie voor clustering-parameters
   */
  public KernbezwaarService(EmbeddingPoort embeddingPoort,
      ClusteringPoort clusteringPoort,
      GeextraheerdBezwaarRepository bezwaarRepository,
      ExtractiePassageRepository passageRepository,
      ExtractieTaakRepository taakRepository,
      KernbezwaarAntwoordRepository antwoordRepository,
      ThemaRepository themaRepository,
      KernbezwaarRepository kernbezwaarRepository,
      KernbezwaarReferentieRepository referentieRepository,
      ClusteringTaakService clusteringTaakService,
      ClusteringTaakRepository clusteringTaakRepository,
      PlatformTransactionManager transactionManager,
      DimensieReductiePoort dimensieReductiePoort,
      ClusteringConfig clusteringConfig) {
    this.embeddingPoort = embeddingPoort;
    this.clusteringPoort = clusteringPoort;
    this.bezwaarRepository = bezwaarRepository;
    this.passageRepository = passageRepository;
    this.taakRepository = taakRepository;
    this.antwoordRepository = antwoordRepository;
    this.themaRepository = themaRepository;
    this.kernbezwaarRepository = kernbezwaarRepository;
    this.referentieRepository = referentieRepository;
    this.clusteringTaakService = clusteringTaakService;
    this.clusteringTaakRepository = clusteringTaakRepository;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.dimensieReductiePoort = dimensieReductiePoort;
    this.clusteringConfig = clusteringConfig;
  }

  /**
   * Clustert de individuele bezwaren van een project tot thema's en kernbezwaren
   * via embedding-generatie en HDBSCAN-clustering.
   *
   * <p>Delegeert naar {@link #clusterEenCategorie} per categorie.
   *
   * @param projectNaam naam van het project
   * @return lijst van thema's met kernbezwaren
   */
  public List<Thema> groepeer(String projectNaam) {
    var alleBezwaren = bezwaarRepository.findByProjectNaam(projectNaam);

    // Cluster bezwaren per categorie
    var categorien = alleBezwaren.stream()
        .map(GeextraheerdBezwaarEntiteit::getCategorie)
        .distinct()
        .toList();

    // Verwijder bestaande thema-data in eigen transactie
    transactionTemplate.executeWithoutResult(status ->
        themaRepository.deleteByProjectNaam(projectNaam));

    // Cluster per categorie via clusterEenCategorie (zonder taakId)
    var resultaat = new ArrayList<Thema>();
    for (var categorie : categorien) {
      var thema = clusterEenCategorie(projectNaam, categorie, null);
      resultaat.add(thema);
    }
    return resultaat;
  }

  /**
   * Clustert de bezwaren binnen een enkele categorie van een project tot een thema
   * met kernbezwaren via embedding-generatie en HDBSCAN-clustering.
   *
   * <p>Als een taakId meegegeven wordt, wordt periodiek gecontroleerd of de taak
   * geannuleerd is. Bij annulering wordt een {@link ClusteringGeannuleerdException} geworpen.
   *
   * @param projectNaam naam van het project
   * @param categorie naam van de categorie
   * @param taakId optioneel ID van de clustering-taak (null bij synchrone clustering)
   * @return het thema met kernbezwaren
   * @throws ClusteringGeannuleerdException als de taak geannuleerd is
   */
  public Thema clusterEenCategorie(String projectNaam, String categorie, Long taakId) {
    var bezwaren = bezwaarRepository.findByProjectNaamAndCategorie(projectNaam, categorie);

    // Bouw lookups voor originele passage-teksten en bestandsnamen
    var taakIds = bezwaren.stream()
        .map(GeextraheerdBezwaarEntiteit::getTaakId)
        .distinct()
        .toList();
    var passageLookup = bouwPassageLookup(taakIds);
    var bestandsnaamLookup = bouwBestandsnaamLookup(taakIds);

    // Verwijder bestaand thema voor deze categorie in eigen transactie
    transactionTemplate.executeWithoutResult(status ->
        themaRepository.deleteByProjectNaamAndNaam(projectNaam, categorie));

    // Controleer annulering voor embedding-generatie (buiten transactie: ziet altijd verse data)
    if (taakId != null && clusteringTaakService.isGeannuleerd(taakId)) {
      throw new ClusteringGeannuleerdException();
    }

    var thema = clusterCategorie(
        projectNaam, categorie, bezwaren, passageLookup, bestandsnaamLookup, taakId);

    return thema;
  }

  /**
   * Geeft eerder berekende kernbezwaren voor een project.
   *
   * @param projectNaam naam van het project
   * @return optional met de lijst van thema's, of leeg als nog niet geclusterd
   */
  public Optional<List<Thema>> geefKernbezwaren(String projectNaam) {
    var themaEntiteiten = themaRepository.findByProjectNaam(projectNaam);
    if (themaEntiteiten.isEmpty()) {
      return Optional.empty();
    }

    var themaIds = themaEntiteiten.stream().map(ThemaEntiteit::getId).toList();
    var kernEntiteiten = kernbezwaarRepository.findByThemaIdIn(themaIds);
    var kernIds = kernEntiteiten.stream().map(KernbezwaarEntiteit::getId).toList();
    var refEntiteiten = referentieRepository.findByKernbezwaarIdIn(kernIds);

    // Groepeer referenties per kernbezwaar
    var refPerKern = refEntiteiten.stream()
        .collect(Collectors.groupingBy(KernbezwaarReferentieEntiteit::getKernbezwaarId));

    // Groepeer kernbezwaren per thema
    var kernPerThema = kernEntiteiten.stream()
        .collect(Collectors.groupingBy(KernbezwaarEntiteit::getThemaId));

    // Assembleer domain records
    var themas = themaEntiteiten.stream()
        .map(te -> {
          var kernen = kernPerThema.getOrDefault(te.getId(), List.of()).stream()
              .map(ke -> {
                var refs = refPerKern.getOrDefault(ke.getId(), List.of()).stream()
                    .map(re -> new IndividueelBezwaarReferentie(
                        re.getBezwaarId(), re.getBestandsnaam(), re.getPassage()))
                    .toList();
                return new Kernbezwaar(ke.getId(), ke.getSamenvatting(), refs, null);
              })
              .toList();
          return new Thema(te.getNaam(), kernen);
        })
        .toList();

    return Optional.of(verrijkMetAntwoorden(themas));
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
   * Verwijdert referenties voor het bestand, daarna lege kernbezwaren, lege thema's,
   * en clustering-taken waarvan het corresponderende thema niet meer bestaat.
   */
  public void ruimOpNaDocumentVerwijdering(String projectNaam, String bestandsnaam) {
    referentieRepository.deleteByBestandsnaamAndProjectNaam(bestandsnaam, projectNaam);
    kernbezwaarRepository.deleteZonderReferenties(projectNaam);
    themaRepository.deleteZonderKernbezwaren(projectNaam);
    clusteringTaakRepository.deleteZonderThema(projectNaam);
  }

  /**
   * Ruimt alle kernbezwaar- en clusteringdata op voor een project.
   * Kernbezwaren, referenties en antwoorden worden via ON DELETE CASCADE
   * op database-niveau meeverwijderd bij het verwijderen van thema's.
   *
   * @param projectNaam naam van het project
   */
  public void ruimAllesOpVoorProject(String projectNaam) {
    themaRepository.deleteByProjectNaam(projectNaam);
    clusteringTaakRepository.deleteByProjectNaam(projectNaam);
  }

  private Thema clusterCategorie(String projectNaam, String categorieNaam,
      List<GeextraheerdBezwaarEntiteit> bezwaren,
      Map<Long, Map<Integer, String>> passageLookup,
      Map<Long, String> bestandsnaamLookup,
      Long taakId) {

    // Genereer embeddings alleen voor bezwaren die er nog geen hebben (legacy data)
    var zonderEmbedding = bezwaren.stream()
        .filter(b -> b.getEmbedding() == null)
        .toList();
    if (!zonderEmbedding.isEmpty()) {
      var teksten = zonderEmbedding.stream()
          .map(b -> geefPassageTekst(b, passageLookup))
          .toList();
      var embeddings = embeddingPoort.genereerEmbeddings(teksten);
      transactionTemplate.executeWithoutResult(status -> {
        for (int i = 0; i < zonderEmbedding.size(); i++) {
          zonderEmbedding.get(i).setEmbedding(embeddings.get(i));
        }
        bezwaarRepository.saveAll(zonderEmbedding);
      });
    }

    // Controleer annulering na embedding-generatie (buiten transactie: ziet verse data)
    if (taakId != null && clusteringTaakService.isGeannuleerd(taakId)) {
      throw new ClusteringGeannuleerdException();
    }

    // Externe call: HDBSCAN-clustering (buiten transactie)
    var origineleInvoer = bezwaren.stream()
        .map(b -> new ClusteringInvoer(b.getId(), b.getEmbedding()))
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

    // Lookup voor bezwaren op ID (nodig bij opslaan thema/kernbezwaren)
    var bezwaarById = bezwaren.stream()
        .collect(Collectors.toMap(GeextraheerdBezwaarEntiteit::getId, b -> b));

    // Sla thema + kernbezwaren + referenties op in één transactie
    return transactionTemplate.execute(status -> {
      var themaEntiteit = new ThemaEntiteit();
      themaEntiteit.setProjectNaam(projectNaam);
      themaEntiteit.setNaam(categorieNaam);
      var opgeslagenThema = themaRepository.save(themaEntiteit);

      var kernbezwaren = new ArrayList<Kernbezwaar>();

      // Verwerk clusters: vind representatief bezwaar (dichtst bij centroid)
      for (var cluster : clusterResultaat.clusters()) {
        var clusterBezwaren = cluster.bezwaarIds().stream()
            .map(bezwaarById::get)
            .toList();
        // Herbereken centroid in originele embedding-ruimte
        var origineleCentroid = berekenOrigineleCentroid(clusterBezwaren);
        var representatief = vindDichtstBijCentroid(clusterBezwaren, origineleCentroid);
        var samenvatting = representatief.getSamenvatting();

        var referenties = bouwReferenties(clusterBezwaren, passageLookup, bestandsnaamLookup);
        var kern = slaKernbezwaarOp(opgeslagenThema.getId(), samenvatting, referenties);
        kernbezwaren.add(kern);
      }

      // Verwerk noise items: alle niet-geclusterde bezwaren onder één kernbezwaar
      if (!clusterResultaat.noiseIds().isEmpty()) {
        var noiseBezwaren = clusterResultaat.noiseIds().stream()
            .map(bezwaarById::get)
            .toList();
        var referenties = bouwReferenties(noiseBezwaren, passageLookup, bestandsnaamLookup);
        var kern = slaKernbezwaarOp(
            opgeslagenThema.getId(), "Niet-geclusterde bezwaren", referenties);
        kernbezwaren.add(kern);
      }

      return new Thema(categorieNaam, kernbezwaren);
    });
  }

  private float[] berekenOrigineleCentroid(List<GeextraheerdBezwaarEntiteit> bezwaren) {
    int dims = bezwaren.get(0).getEmbedding().length;
    var centroid = new float[dims];
    for (var bezwaar : bezwaren) {
      var emb = bezwaar.getEmbedding();
      for (int i = 0; i < dims; i++) {
        centroid[i] += emb[i];
      }
    }
    for (int i = 0; i < dims; i++) {
      centroid[i] /= bezwaren.size();
    }
    return centroid;
  }

  private GeextraheerdBezwaarEntiteit vindDichtstBijCentroid(
      List<GeextraheerdBezwaarEntiteit> bezwaren, float[] centroid) {
    GeextraheerdBezwaarEntiteit dichtstbij = null;
    double hoogsteGelijkenis = Double.NEGATIVE_INFINITY;
    for (var bezwaar : bezwaren) {
      double gelijkenis = cosinusGelijkenis(bezwaar.getEmbedding(), centroid);
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

  private Kernbezwaar slaKernbezwaarOp(Long themaId, String samenvatting,
      List<IndividueelBezwaarReferentie> referenties) {
    var kernEntiteit = new KernbezwaarEntiteit();
    kernEntiteit.setThemaId(themaId);
    kernEntiteit.setSamenvatting(samenvatting);
    kernEntiteit = kernbezwaarRepository.save(kernEntiteit);

    var opgeslagenReferenties = new ArrayList<IndividueelBezwaarReferentie>();
    for (var ref : referenties) {
      var refEntiteit = new KernbezwaarReferentieEntiteit();
      refEntiteit.setKernbezwaarId(kernEntiteit.getId());
      refEntiteit.setBezwaarId(ref.bezwaarId());
      refEntiteit.setBestandsnaam(ref.bestandsnaam());
      refEntiteit.setPassage(ref.passage());
      referentieRepository.save(refEntiteit);
      opgeslagenReferenties.add(ref);
    }

    return new Kernbezwaar(kernEntiteit.getId(), samenvatting,
        opgeslagenReferenties, null);
  }

  private List<IndividueelBezwaarReferentie> bouwReferenties(
      List<GeextraheerdBezwaarEntiteit> bezwaren,
      Map<Long, Map<Integer, String>> passageLookup,
      Map<Long, String> bestandsnaamLookup) {
    return bezwaren.stream()
        .map(b -> new IndividueelBezwaarReferentie(
            b.getId(),
            bestandsnaamLookup.getOrDefault(b.getTaakId(), "onbekend"),
            geefPassageTekst(b, passageLookup)))
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

  private List<Thema> verrijkMetAntwoorden(List<Thema> themas) {
    var alleIds = themas.stream()
        .flatMap(t -> t.kernbezwaren().stream())
        .map(Kernbezwaar::id)
        .toList();
    var antwoorden = antwoordRepository.findByKernbezwaarIdIn(alleIds);
    var antwoordMap = antwoorden.stream()
        .collect(Collectors.toMap(
            KernbezwaarAntwoordEntiteit::getKernbezwaarId,
            KernbezwaarAntwoordEntiteit::getInhoud));
    return themas.stream()
        .map(thema -> new Thema(thema.naam(),
            thema.kernbezwaren().stream()
                .map(kern -> new Kernbezwaar(kern.id(), kern.samenvatting(),
                    kern.individueleBezwaren(),
                    antwoordMap.get(kern.id())))
                .toList()))
        .toList();
  }
}
