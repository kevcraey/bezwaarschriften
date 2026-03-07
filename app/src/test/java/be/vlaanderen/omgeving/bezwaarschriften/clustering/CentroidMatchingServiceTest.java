package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort.Cluster;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.ToewijzingsMethode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CentroidMatchingServiceTest {

  private CentroidMatchingService service;

  @BeforeEach
  void setUp() {
    service = new CentroidMatchingService();
  }

  @Test
  void noiseBovenDrempelWordtToegewezen() {
    // Cluster met centroid [1,0,0], noise-bezwaar [0.99,0.1,0]
    // Cosine similarity ~0.995 -> boven drempel 0.85
    var clusters =
        List.of(new Cluster(1, List.of(10L), new float[] {1f, 0f, 0f}));
    var noiseEmbeddings = Map.of(20L, new float[] {0.99f, 0.1f, 0f});

    var resultaat = service.wijsNoiseToe(clusters, noiseEmbeddings, 0.85);

    assertThat(resultaat.toegewezenPerCluster().get(1)).contains(20L);
    assertThat(resultaat.resterendeNoise()).isEmpty();
    assertThat(resultaat.toewijzingen().get(20L).methode())
        .isEqualTo(ToewijzingsMethode.CENTROID_FALLBACK);
  }

  @Test
  void noiseOnderDrempelBlijftNoise() {
    var clusters =
        List.of(new Cluster(1, List.of(10L), new float[] {1f, 0f, 0f}));
    var noiseEmbeddings = Map.of(30L, new float[] {0f, 1f, 0f});

    var resultaat = service.wijsNoiseToe(clusters, noiseEmbeddings, 0.85);

    assertThat(resultaat.toegewezenPerCluster()).isEmpty();
    assertThat(resultaat.resterendeNoise()).contains(30L);
  }

  @Test
  void noiseWordtAanDichtstbijzijndeClusterToegewezen() {
    var clusters =
        List.of(
            new Cluster(1, List.of(10L), new float[] {1f, 0f, 0f}),
            new Cluster(2, List.of(11L), new float[] {0f, 1f, 0f}));
    var noiseEmbeddings = Map.of(20L, new float[] {0.1f, 0.99f, 0f});

    var resultaat = service.wijsNoiseToe(clusters, noiseEmbeddings, 0.85);

    assertThat(resultaat.toegewezenPerCluster().get(2)).contains(20L);
    assertThat(resultaat.toegewezenPerCluster().containsKey(1)).isFalse();
  }

  @Test
  void legeNoiseGeeftLeegResultaat() {
    var clusters =
        List.of(new Cluster(1, List.of(10L), new float[] {1f, 0f, 0f}));

    var resultaat = service.wijsNoiseToe(clusters, Map.of(), 0.85);

    assertThat(resultaat.toegewezenPerCluster()).isEmpty();
    assertThat(resultaat.resterendeNoise()).isEmpty();
  }

  @Test
  void legeClustersLatenAlleNoiseStaan() {
    var noiseEmbeddings = Map.of(20L, new float[] {1f, 0f, 0f});

    var resultaat = service.wijsNoiseToe(List.of(), noiseEmbeddings, 0.85);

    assertThat(resultaat.resterendeNoise()).contains(20L);
  }

  @Test
  void berekenTop5GeeftGesorteerdeResultaten() {
    var bezwaarEmbedding = new float[] {0.9f, 0.1f, 0f};
    var centroids =
        Map.of(
            1L, new float[] {1f, 0f, 0f},
            2L, new float[] {0f, 1f, 0f},
            3L, new float[] {0.8f, 0.2f, 0f});

    var suggesties = service.berekenTop5Suggesties(bezwaarEmbedding, centroids);

    assertThat(suggesties).hasSizeLessThanOrEqualTo(5);
    assertThat(suggesties.get(0).score()).isGreaterThan(suggesties.get(1).score());
  }
}
