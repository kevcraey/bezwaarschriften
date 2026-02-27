package be.vlaanderen.omgeving.bezwaarschriften.ingestie;

/**
 * Exception voor problemen tijdens file ingestie.
 */
public class FileIngestionException extends RuntimeException {

  public FileIngestionException(String message) {
    super(message);
  }

  public FileIngestionException(String message, Throwable cause) {
    super(message, cause);
  }
}
