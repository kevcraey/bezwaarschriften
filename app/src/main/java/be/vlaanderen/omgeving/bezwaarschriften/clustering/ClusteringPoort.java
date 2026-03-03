package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import java.util.List;

/**
 * Port voor het clusteren van vectoren.
 *
 * <p>Abstraheert het clustering-algoritme zodat de implementatie
 * vervangbaar is (bv. Tribuo HDBSCAN, Python sidecar, ander algoritme).
 */
public interface ClusteringPoort {

  /**
   * Clustert de gegeven vectoren.
   *
   * @param invoer lijst van bezwaar-IDs met hun embedding-vector
   * @return resultaat met clusters en noise-IDs
   */
  ClusteringResultaat cluster(List<ClusteringInvoer> invoer);

  /** Invoer: een bezwaar-ID gekoppeld aan zijn embedding-vector. */
  record ClusteringInvoer(Long bezwaarId, float[] embedding) {}

  /** Een cluster met label, leden en zwaartepunt. */
  record Cluster(int label, List<Long> bezwaarIds, float[] centroid) {}

  /** Resultaat: clusters en noise-items. */
  record ClusteringResultaat(List<Cluster> clusters, List<Long> noiseIds) {}
}
