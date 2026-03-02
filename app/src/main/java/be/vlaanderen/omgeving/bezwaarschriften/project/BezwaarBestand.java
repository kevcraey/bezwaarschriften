package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Representeert een bezwaarbestand met zijn verwerkingsstatus.
 *
 * @param bestandsnaam Naam van het bezwaarbestand
 * @param status Huidige verwerkingsstatus
 * @param aantalWoorden Aantal woorden in het bestand (null als niet verwerkt)
 * @param aantalBezwaren Aantal geextraheerde bezwaren (null als niet geextraheerd)
 * @param heeftOpmerkingen of er opmerkingen zijn (bv. passage-validatie waarschuwingen)
 */
public record BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status,
    Integer aantalWoorden, Integer aantalBezwaren, boolean heeftOpmerkingen) {

  public BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status) {
    this(bestandsnaam, status, null, null, false);
  }
}
