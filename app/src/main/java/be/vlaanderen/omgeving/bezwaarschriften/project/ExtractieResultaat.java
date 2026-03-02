package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;

/**
 * Resultaat van een bezwaarbestand-extractie.
 *
 * @param aantalWoorden Aantal woorden in het verwerkte bestand
 * @param aantalBezwaren Aantal geextraheerde bezwaren
 * @param passages Geidentificeerde passages uit het brondocument
 * @param bezwaren Individuele bezwaren met verwijzing naar bron-passage
 * @param documentSamenvatting Korte samenvatting van het volledige document
 */
public record ExtractieResultaat(
    int aantalWoorden,
    int aantalBezwaren,
    List<Passage> passages,
    List<GeextraheerdBezwaar> bezwaren,
    String documentSamenvatting) {

  /**
   * Terugwaarts-compatibele constructor met enkel counts (voor MockExtractieVerwerker).
   */
  public ExtractieResultaat(int aantalWoorden, int aantalBezwaren) {
    this(aantalWoorden, aantalBezwaren, List.of(), List.of(), null);
  }
}
