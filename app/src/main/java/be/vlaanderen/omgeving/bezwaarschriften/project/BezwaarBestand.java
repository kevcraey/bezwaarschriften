package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Representeert een bezwaarbestand met zijn verwerkingsstatus.
 *
 * @param bestandsnaam Naam van het bezwaarbestand
 * @param status Huidige verwerkingsstatus
 */
public record BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status,
    Integer aantalWoorden) {

  public BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status) {
    this(bestandsnaam, status, null);
  }
}
