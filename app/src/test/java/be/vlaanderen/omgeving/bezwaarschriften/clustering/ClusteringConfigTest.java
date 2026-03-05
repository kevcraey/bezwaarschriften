package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClusteringConfigTest {

  @Test
  void defaultUmapWaarden() {
    var config = new ClusteringConfig();

    assertThat(config.isUmapEnabled()).isTrue();
    assertThat(config.getUmapNComponents()).isEqualTo(5);
    assertThat(config.getUmapNNeighbors()).isEqualTo(15);
    assertThat(config.getUmapMinDist()).isEqualTo(0.1f);
  }

  @Test
  void umapWaardenAanpasbaar() {
    var config = new ClusteringConfig();

    config.setUmapEnabled(false);
    config.setUmapNComponents(10);
    config.setUmapNNeighbors(30);
    config.setUmapMinDist(0.5f);

    assertThat(config.isUmapEnabled()).isFalse();
    assertThat(config.getUmapNComponents()).isEqualTo(10);
    assertThat(config.getUmapNNeighbors()).isEqualTo(30);
    assertThat(config.getUmapMinDist()).isEqualTo(0.5f);
  }
}
