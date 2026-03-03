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
    var trainer = new HdbscanTrainer(config.getMinClusterSize());
    var model = trainer.train(dataset);

    var labels = model.getClusterLabels();
    return verwerkLabels(invoer, labels);
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
