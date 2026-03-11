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
  BEZWAAR_EXTRACTIE_WACHTEND,
  BEZWAAR_EXTRACTIE_BEZIG,
  BEZWAAR_EXTRACTIE_KLAAR,
  BEZWAAR_EXTRACTIE_FOUT,
  NIET_ONDERSTEUND
}
