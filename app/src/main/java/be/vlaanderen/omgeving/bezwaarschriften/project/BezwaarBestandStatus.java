package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Status van een bezwaarbestand in de verwerkingspipeline.
 */
public enum BezwaarBestandStatus {
  TODO,
  TEKST_EXTRACTIE_WACHTEND,
  TEKST_EXTRACTIE_BEZIG,
  TEKST_EXTRACTIE_KLAAR,
  TEKST_EXTRACTIE_MISLUKT,
  TEKST_EXTRACTIE_OCR_NIET_BESCHIKBAAR,
  WACHTEND,
  BEZIG,
  EXTRACTIE_KLAAR,
  FOUT,
  NIET_ONDERSTEUND
}
