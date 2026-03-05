package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.tribuo.MutableDataset;
import org.tribuo.clustering.ClusterID;
import org.tribuo.clustering.ClusteringFactory;
import org.tribuo.clustering.hdbscan.HdbscanTrainer;
import org.tribuo.impl.ArrayExample;
import org.tribuo.math.distance.CosineDistance;
import org.tribuo.math.neighbour.NeighboursQueryFactoryType;
import org.tribuo.provenance.SimpleDataSourceProvenance;

/**
 * Clustering-adapter die HDBSCAN toepast via Oracle Tribuo.
 */
@Component
public class TribuoClusteringAdapter implements ClusteringPoort {

  private final ClusteringConfig config;

  /**
   * Maakt een nieuwe TribuoClusteringAdapter aan.
   *
   * @param config clustering-configuratie
   */
  public TribuoClusteringAdapter(ClusteringConfig config) {
    this.config = config;
  }

  @Override
  public ClusteringResultaat cluster(List<ClusteringInvoer> invoer) {
    if (invoer.isEmpty()) {
      return new ClusteringResultaat(List.of(), List.of());
    }
    if (invoer.size() < config.getMinClusterSize()) {
      return new ClusteringResultaat(List.of(),
          invoer.stream().map(ClusteringInvoer::bezwaarId).toList());
    }

    var dataset = bouwDataset(invoer);
    var trainer = new HdbscanTrainer(
        config.getMinClusterSize(),
        new CosineDistance(),
        config.getMinSamples(),
        1,
        NeighboursQueryFactoryType.BRUTE_FORCE);
    var model = trainer.train(dataset);

    var labels = model.getClusterLabels();
    var clusters = verwerkLabels(invoer, labels);

    if (config.getClusterSelectionEpsilon() > 0.0) {
      var samengevoegd = samenvoegDichteClusters(clusters.clusters(),
          config.getClusterSelectionEpsilon());
      return new ClusteringResultaat(samengevoegd, clusters.noiseIds());
    }
    return clusters;
  }

  private MutableDataset<ClusterID> bouwDataset(
      List<ClusteringInvoer> invoer) {
    var factory = new ClusteringFactory();
    var provenance = new SimpleDataSourceProvenance(
        "bezwaar-embeddings", factory);
    var dataset = new MutableDataset<>(provenance, factory);

    for (var item : invoer) {
      int dims = item.embedding().length;
      var featureNames = new String[dims];
      var featureValues = new double[dims];
      for (int i = 0; i < dims; i++) {
        featureNames[i] = "d" + i;
        featureValues[i] = item.embedding()[i];
      }
      dataset.add(new ArrayExample<>(
          new ClusterID(ClusterID.UNASSIGNED),
          featureNames, featureValues));
    }
    return dataset;
  }

  private ClusteringResultaat verwerkLabels(
      List<ClusteringInvoer> invoer, List<Integer> labels) {
    Map<Integer, List<Long>> clusterMap = new HashMap<>();
    Map<Integer, List<float[]>> clusterVectors = new HashMap<>();
    var noiseIds = new ArrayList<Long>();

    for (int i = 0; i < invoer.size(); i++) {
      var bezwaarId = invoer.get(i).bezwaarId();
      var label = labels.get(i);

      if (label == 0) {
        noiseIds.add(bezwaarId);
      } else {
        clusterMap
            .computeIfAbsent(label, k -> new ArrayList<>())
            .add(bezwaarId);
        clusterVectors
            .computeIfAbsent(label, k -> new ArrayList<>())
            .add(invoer.get(i).embedding());
      }
    }

    var clusters = new ArrayList<Cluster>();
    for (var entry : clusterMap.entrySet()) {
      var centroid =
          berekenCentroid(clusterVectors.get(entry.getKey()));
      clusters.add(
          new Cluster(entry.getKey(), entry.getValue(), centroid));
    }

    return new ClusteringResultaat(clusters, noiseIds);
  }

  /**
   * Voegt clusters samen waarvan de centroiden binnen {@code epsilon} cosine-afstand
   * van elkaar liggen (transitief, via union-find).
   * Cosine-afstand = 1 - cosine_similarity, bereik [0, 2].
   */
  List<Cluster> samenvoegDichteClusters(List<Cluster> clusters, double epsilon) {
    int n = clusters.size();
    int[] parent = new int[n];
    for (int i = 0; i < n; i++) {
      parent[i] = i;
    }

    for (int i = 0; i < n; i++) {
      for (int j = i + 1; j < n; j++) {
        if (cosineAfstand(clusters.get(i).centroid(),
            clusters.get(j).centroid()) < epsilon) {
          union(parent, i, j);
        }
      }
    }

    Map<Integer, List<Long>> groepen = new HashMap<>();
    for (int i = 0; i < n; i++) {
      int root = find(parent, i);
      groepen.computeIfAbsent(root, k -> new ArrayList<>())
          .addAll(clusters.get(i).bezwaarIds());
    }

    var resultaat = new ArrayList<Cluster>();
    int nieuwLabel = 1;
    for (var entry : groepen.entrySet()) {
      var ids = entry.getValue();
      var centroid = berekenCentroidVanIds(clusters, entry.getKey(), parent, n);
      resultaat.add(new Cluster(nieuwLabel++, ids, centroid));
    }
    return resultaat;
  }

  private float[] berekenCentroidVanIds(
      List<Cluster> clusters, int root, int[] parent, int n) {
    var vectoren = new ArrayList<float[]>();
    for (int i = 0; i < n; i++) {
      if (find(parent, i) == root) {
        vectoren.add(clusters.get(i).centroid());
      }
    }
    return berekenCentroid(vectoren);
  }

  private int find(int[] parent, int i) {
    if (parent[i] != i) {
      parent[i] = find(parent, parent[i]);
    }
    return parent[i];
  }

  private void union(int[] parent, int i, int j) {
    parent[find(parent, i)] = find(parent, j);
  }

  private double cosineAfstand(float[] a, float[] b) {
    double dot = 0;
    double normA = 0;
    double normB = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    double denom = Math.sqrt(normA) * Math.sqrt(normB);
    if (denom == 0) {
      return 1.0;
    }
    return 1.0 - (dot / denom);
  }

  private float[] berekenCentroid(List<float[]> vectoren) {
    int dims = vectoren.get(0).length;
    var centroid = new float[dims];
    for (var vector : vectoren) {
      for (int i = 0; i < dims; i++) {
        centroid[i] += vector[i];
      }
    }
    for (int i = 0; i < dims; i++) {
      centroid[i] /= vectoren.size();
    }
    return centroid;
  }
}
