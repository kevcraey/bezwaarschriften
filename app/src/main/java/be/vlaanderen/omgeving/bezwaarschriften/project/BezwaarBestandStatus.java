package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Status van een bezwaarbestand in de verwerkingspipeline.
 */
public enum BezwaarBestandStatus {
  TODO,
  WACHTEND,
  BEZIG,
  EXTRACTIE_KLAAR,
  FOUT,
  NIET_ONDERSTEUND
}
