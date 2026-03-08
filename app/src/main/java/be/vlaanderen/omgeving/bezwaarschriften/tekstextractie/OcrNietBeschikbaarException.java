package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

/** Exceptie wanneer OCR-software (Tesseract) niet beschikbaar is op het systeem. */
public class OcrNietBeschikbaarException extends RuntimeException {

  /** Maakt een nieuwe OcrNietBeschikbaarException aan.
   *
   * @param message beschrijving van de fout
   */
  public OcrNietBeschikbaarException(String message) {
    super(message);
  }
}
