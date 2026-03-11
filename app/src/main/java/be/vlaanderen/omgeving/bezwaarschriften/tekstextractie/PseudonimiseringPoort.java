package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

/**
 * Poort voor pseudonimisering van teksten.
 *
 * <p>Vervangt persoonlijk identificeerbare informatie (PII) door generieke tokens.
 * De mapping-ID maakt het mogelijk om de originele tekst later te herstellen.
 */
public interface PseudonimiseringPoort {

  /**
   * Pseudonimiseert de gegeven tekst.
   *
   * @param tekst de te pseudonimiseren tekst
   * @return resultaat met gepseudonimiseerde tekst en mapping-ID
   * @throws PseudonimiseringException als de pseudonimisering mislukt
   */
  PseudonimiseringResultaat pseudonimiseer(String tekst);
}
