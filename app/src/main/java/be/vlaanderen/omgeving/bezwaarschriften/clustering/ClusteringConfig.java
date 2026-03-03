package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuratie voor het clustering-algoritme.
 */
@Component
@ConfigurationProperties(prefix = "bezwaarschriften.clustering")
public class ClusteringConfig {

  private int minClusterSize = 5;
  private int minSamples = 3;

  public int getMinClusterSize() {
    return minClusterSize;
  }

  public void setMinClusterSize(int minClusterSize) {
    this.minClusterSize = minClusterSize;
  }

  public int getMinSamples() {
    return minSamples;
  }

  public void setMinSamples(int minSamples) {
    this.minSamples = minSamples;
  }
}
