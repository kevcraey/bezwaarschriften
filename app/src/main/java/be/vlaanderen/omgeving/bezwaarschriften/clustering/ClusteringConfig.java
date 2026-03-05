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
  private double clusterSelectionEpsilon = 0.0;
  private boolean umapEnabled = true;
  private int umapNComponents = 5;
  private int umapNNeighbors = 15;
  private float umapMinDist = 0.1f;

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

  public double getClusterSelectionEpsilon() {
    return clusterSelectionEpsilon;
  }

  public void setClusterSelectionEpsilon(double clusterSelectionEpsilon) {
    this.clusterSelectionEpsilon = clusterSelectionEpsilon;
  }

  public boolean isUmapEnabled() {
    return umapEnabled;
  }

  public void setUmapEnabled(boolean umapEnabled) {
    this.umapEnabled = umapEnabled;
  }

  public int getUmapNComponents() {
    return umapNComponents;
  }

  public void setUmapNComponents(int umapNComponents) {
    this.umapNComponents = umapNComponents;
  }

  public int getUmapNNeighbors() {
    return umapNNeighbors;
  }

  public void setUmapNNeighbors(int umapNNeighbors) {
    this.umapNNeighbors = umapNNeighbors;
  }

  public float getUmapMinDist() {
    return umapMinDist;
  }

  public void setUmapMinDist(float umapMinDist) {
    this.umapMinDist = umapMinDist;
  }
}
