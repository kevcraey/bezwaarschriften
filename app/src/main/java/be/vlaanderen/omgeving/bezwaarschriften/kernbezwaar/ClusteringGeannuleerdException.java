package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

/**
 * Exceptie die geworpen wordt wanneer een clustering-taak geannuleerd is
 * terwijl de verwerking nog bezig was.
 */
public class ClusteringGeannuleerdException extends RuntimeException {

  public ClusteringGeannuleerdException() {
    super("Clustering is geannuleerd");
  }
}
