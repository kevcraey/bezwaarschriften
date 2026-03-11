package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

/** Exceptie wanneer pseudonimisering via Obscuro mislukt. */
public class PseudonimiseringException extends RuntimeException {

  public PseudonimiseringException(String message) {
    super(message);
  }

  public PseudonimiseringException(String message, Throwable cause) {
    super(message, cause);
  }
}
