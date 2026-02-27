package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Status van een bezwaarbestand in de verwerkingspipeline.
 */
public enum BezwaarBestandStatus {
  TODO,
  EXTRACTIE_KLAAR,
  FOUT,
  NIET_ONDERSTEUND
}
