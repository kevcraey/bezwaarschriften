package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

/**
 * Poort voor het verwerken van een consolidatie-taak.
 *
 * <p>Implementaties voeren de daadwerkelijke consolidatie uit voor een specifiek
 * bezwaarschrift-document binnen een project.
 */
public interface ConsolidatieVerwerker {

  /**
   * Verwerkt de consolidatie voor een document.
   *
   * @param projectNaam naam van het project
   * @param bestandsnaam naam van het te consolideren bestand
   * @param poging huidige pogingsnummer (0-based)
   */
  void verwerk(String projectNaam, String bestandsnaam, int poging);
}
