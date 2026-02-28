package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Resultaat van een bezwaarbestand-extractie.
 *
 * @param aantalWoorden Aantal woorden in het verwerkte bestand
 * @param aantalBezwaren Aantal geextraheerde bezwaren
 */
public record ExtractieResultaat(int aantalWoorden, int aantalBezwaren) {
}
