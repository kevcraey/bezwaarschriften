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
}
