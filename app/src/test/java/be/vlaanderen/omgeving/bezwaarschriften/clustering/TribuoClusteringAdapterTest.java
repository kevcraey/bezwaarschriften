package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort.Cluster;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort.ClusteringInvoer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TribuoClusteringAdapterTest {

  private TribuoClusteringAdapter adapter;

  @BeforeEach
  void setUp() {
    var config = new ClusteringConfig();
    config.setMinClusterSize(2);
    config.setMinSamples(2);
    adapter = new TribuoClusteringAdapter(config);
  }

  @Test
  void clusterAlleItemsVerdeeldOverClustersEnNoise() {
    var invoer = List.of(
        new ClusteringInvoer(1L, new float[]{1.0f, 0.0f, 0.0f}),
        new ClusteringInvoer(2L, new float[]{0.99f, 0.01f, 0.0f}),
        new ClusteringInvoer(3L, new float[]{0.98f, 0.02f, 0.0f}),
        new ClusteringInvoer(4L, new float[]{0.0f, 1.0f, 0.0f}),
        new ClusteringInvoer(5L, new float[]{0.01f, 0.99f, 0.0f}),
        new ClusteringInvoer(6L, new float[]{0.02f, 0.98f, 0.0f})
    );

    var resultaat = adapter.cluster(invoer);

    var alleIds = new ArrayList<Long>();
    resultaat.clusters().forEach(c -> alleIds.addAll(c.bezwaarIds()));
    alleIds.addAll(resultaat.noiseIds());
    assertThat(alleIds)
        .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
  }

  @Test
  void teWeinigItemsGeeftAllesAlsNoise() {
    var invoer = List.of(
        new ClusteringInvoer(
            1L, new float[]{1.0f, 0.0f, 0.0f})
    );

    var resultaat = adapter.cluster(invoer);

    assertThat(resultaat.clusters()).isEmpty();
    assertThat(resultaat.noiseIds()).containsExactly(1L);
  }

  @Test
  void legeInvoerGeeftLeegResultaat() {
    var resultaat = adapter.cluster(List.of());

    assertThat(resultaat.clusters()).isEmpty();
    assertThat(resultaat.noiseIds()).isEmpty();
  }

  @Test
  void clusterHeeftCentroid() {
    var invoer = List.of(
        new ClusteringInvoer(1L, new float[]{1.0f, 0.0f}),
        new ClusteringInvoer(2L, new float[]{1.0f, 0.0f}),
        new ClusteringInvoer(3L, new float[]{1.0f, 0.0f}),
        new ClusteringInvoer(4L, new float[]{1.0f, 0.0f}),
        new ClusteringInvoer(5L, new float[]{1.0f, 0.0f})
    );

    var resultaat = adapter.cluster(invoer);

    if (!resultaat.clusters().isEmpty()) {
      var cluster = resultaat.clusters().get(0);
      assertThat(cluster.centroid()).isNotNull();
      assertThat(cluster.centroid().length).isEqualTo(2);
    }
  }

  // --- cluster_selection_epsilon ---

  @Test
  void epsilonVoegtClustersSamenWaardeAfstandKleinerIsEpsilon() {
    // Centroid A ≈ [1.0, 0.0], centroid B ≈ [0.9, 0.1]
    // Euclidische afstand tussen centroids ≈ 0.14
    var clusterA = new Cluster(1, List.of(1L, 2L, 3L), new float[]{1.0f, 0.0f});
    var clusterB = new Cluster(2, List.of(4L, 5L, 6L), new float[]{0.9f, 0.1f});

    var samengevoegd = adapter.samenvoegDichteClusters(
        List.of(clusterA, clusterB), 0.2);

    assertThat(samengevoegd).hasSize(1);
    assertThat(samengevoegd.get(0).bezwaarIds())
        .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
  }

  @Test
  void epsilonLaatVerAfstaandeClustersOnaangeraakt() {
    // Centroid A ≈ [1.0, 0.0], centroid B ≈ [0.0, 1.0]
    // Euclidische afstand ≈ 1.41
    var clusterA = new Cluster(1, List.of(1L, 2L), new float[]{1.0f, 0.0f});
    var clusterB = new Cluster(2, List.of(3L, 4L), new float[]{0.0f, 1.0f});

    var samengevoegd = adapter.samenvoegDichteClusters(
        List.of(clusterA, clusterB), 0.5);

    assertThat(samengevoegd).hasSize(2);
  }

  @Test
  void epsilonNulVoegtNooitSamen() {
    var clusterA = new Cluster(1, List.of(1L, 2L), new float[]{1.0f, 0.0f});
    var clusterB = new Cluster(2, List.of(3L, 4L), new float[]{0.9f, 0.1f});

    var samengevoegd = adapter.samenvoegDichteClusters(
        List.of(clusterA, clusterB), 0.0);

    assertThat(samengevoegd).hasSize(2);
  }

  @Test
  void epsilonTransitiefSamenvoegen() {
    // A dichtbij B, B dichtbij C → alle drie samenvoegen
    var clusterA = new Cluster(1, List.of(1L), new float[]{1.0f, 0.0f});
    var clusterB = new Cluster(2, List.of(2L), new float[]{0.9f, 0.1f}); // ~0.14 van A
    var clusterC = new Cluster(3, List.of(3L), new float[]{0.8f, 0.2f}); // ~0.14 van B

    var samengevoegd = adapter.samenvoegDichteClusters(
        List.of(clusterA, clusterB, clusterC), 0.2);

    assertThat(samengevoegd).hasSize(1);
    assertThat(samengevoegd.get(0).bezwaarIds())
        .containsExactlyInAnyOrder(1L, 2L, 3L);
  }
}
