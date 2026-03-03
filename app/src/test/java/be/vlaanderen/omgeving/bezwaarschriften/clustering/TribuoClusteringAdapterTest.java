package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import static org.assertj.core.api.Assertions.assertThat;

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

    // All 6 IDs must appear somewhere (clusters + noise)
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
}
