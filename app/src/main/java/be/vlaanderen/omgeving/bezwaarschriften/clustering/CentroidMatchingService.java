package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort.Cluster;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.ToewijzingsMethode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Service die noise-bezwaren toewijst aan het dichtstbijzijnde cluster
 * op basis van cosinus-gelijkenis met de cluster-centroid.
 */
@Service
public class CentroidMatchingService {

  /**
   * Wijst noise-bezwaren toe aan clusters wanneer de cosinus-gelijkenis
   * met de centroid boven de drempel ligt.
   *
   * @param clusters        de bestaande clusters met hun centroids
   * @param noiseEmbeddings map van bezwaar-ID naar embedding voor noise-items
   * @param threshold       minimale cosinus-gelijkenis voor toewijzing
   * @return resultaat met toewijzingen per cluster en resterende noise
   */
  public CentroidMatchingResultaat wijsNoiseToe(
      List<Cluster> clusters,
      Map<Long, float[]> noiseEmbeddings,
      double threshold) {

    var toegewezenPerCluster = new HashMap<Integer, List<Long>>();
    var resterendeNoise = new ArrayList<Long>();
    var toewijzingen = new HashMap<Long, Toewijzing>();

    for (var entry : noiseEmbeddings.entrySet()) {
      Long bezwaarId = entry.getKey();
      float[] embedding = entry.getValue();

      int besteCluster = -1;
      double hoogsteScore = Double.NEGATIVE_INFINITY;

      for (var cluster : clusters) {
        double score = cosinusGelijkenis(embedding, cluster.centroid());
        if (score > hoogsteScore) {
          hoogsteScore = score;
          besteCluster = cluster.label();
        }
      }

      if (besteCluster >= 0 && hoogsteScore >= threshold) {
        toegewezenPerCluster
            .computeIfAbsent(besteCluster, k -> new ArrayList<>())
            .add(bezwaarId);
        toewijzingen.put(
            bezwaarId,
            new Toewijzing(ToewijzingsMethode.CENTROID_FALLBACK, hoogsteScore));
      } else {
        resterendeNoise.add(bezwaarId);
      }
    }

    return new CentroidMatchingResultaat(
        toegewezenPerCluster, resterendeNoise, toewijzingen);
  }

  /**
   * Berekent de top-5 meest gelijkende kernbezwaren voor een gegeven
   * bezwaar-embedding, gesorteerd op afnemende score.
   *
   * @param bezwaarEmbedding       de embedding van het bezwaar
   * @param centroidsPerKernbezwaar map van kernbezwaar-ID naar centroid
   * @return gesorteerde lijst van maximaal 5 suggesties
   */
  public List<Suggestie> berekenTop5Suggesties(
      float[] bezwaarEmbedding, Map<Long, float[]> centroidsPerKernbezwaar) {
    return centroidsPerKernbezwaar.entrySet().stream()
        .map(
            e ->
                new Suggestie(
                    e.getKey(),
                    cosinusGelijkenis(bezwaarEmbedding, e.getValue())))
        .sorted(Comparator.comparingDouble(Suggestie::score).reversed())
        .limit(5)
        .toList();
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

  /** Resultaat van noise-toewijzing aan clusters. */
  public record CentroidMatchingResultaat(
      Map<Integer, List<Long>> toegewezenPerCluster,
      List<Long> resterendeNoise,
      Map<Long, Toewijzing> toewijzingen) {}

  /** Toewijzingsinformatie: methode en score. */
  public record Toewijzing(ToewijzingsMethode methode, double score) {}

  /** Suggestie voor handmatige toewijzing: kernbezwaar-ID en score. */
  public record Suggestie(Long kernbezwaarId, double score) {}
}
