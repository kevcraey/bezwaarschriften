package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

/** Status van een tekst-extractie taak. */
public enum TekstExtractieTaakStatus {
  WACHTEND,
  BEZIG,
  KLAAR,
  MISLUKT,
  OCR_NIET_BESCHIKBAAR
}
