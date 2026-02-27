package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Representeert een bezwaarbestand met zijn verwerkingsstatus.
 *
 * @param bestandsnaam Naam van het bezwaarbestand
 * @param status Huidige verwerkingsstatus
 */
public record BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status) {

  /**
   * Geeft een nieuwe instantie terug met een bijgewerkte status.
   *
   * @param nieuweStatus De nieuwe status
   * @return Nieuwe BezwaarBestand instantie
   */
  public BezwaarBestand withStatus(BezwaarBestandStatus nieuweStatus) {
    return new BezwaarBestand(this.bestandsnaam, nieuweStatus);
  }
}
