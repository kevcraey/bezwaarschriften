package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Interface voor het verwerken van bezwaarbestand-extracties.
 */
public interface ExtractieVerwerker {

  /**
   * Verwerkt een bezwaarbestand en extraheert bezwaren.
   *
   * @param projectNaam Naam van het project
   * @param bestandsnaam Naam van het bezwaarbestand
   * @param poging Volgnummer van de poging (0-based)
   * @return Het extractieresultaat met aantal woorden en bezwaren
   */
  ExtractieResultaat verwerk(String projectNaam, String bestandsnaam, int poging);
}
