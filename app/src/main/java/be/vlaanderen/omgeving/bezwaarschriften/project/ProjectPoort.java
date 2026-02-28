package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;

/**
 * Port interface voor het opvragen van projecten en bezwaarbestanden.
 */
public interface ProjectPoort {

  /**
   * Geeft de lijst van beschikbare projecten terug.
   *
   * @return Lijst van projectnamen
   */
  List<String> geefProjecten();

  /**
   * Geeft de bestandsnamen in de bezwaren-map van een project terug.
   *
   * @param projectNaam Naam van het project
   * @return Lijst van bestandsnamen
   * @throws ProjectNietGevondenException Als het project niet bestaat
   */
  List<String> geefBestandsnamen(String projectNaam);

  /**
   * Slaat een bestand op in de bezwaren-map van een project.
   *
   * @param projectNaam Naam van het project
   * @param bestandsnaam Naam van het bestand
   * @param inhoud Inhoud van het bestand als byte-array
   * @throws ProjectNietGevondenException Als het project niet bestaat
   */
  void slaBestandOp(String projectNaam, String bestandsnaam, byte[] inhoud);

  /**
   * Verwijdert een bestand uit de bezwaren-map van een project.
   *
   * @param projectNaam Naam van het project
   * @param bestandsnaam Naam van het bestand
   * @return {@code true} als het bestand verwijderd is, {@code false} als het niet gevonden is
   * @throws ProjectNietGevondenException Als het project niet bestaat
   */
  boolean verwijderBestand(String projectNaam, String bestandsnaam);
}
