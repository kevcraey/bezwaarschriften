package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Wordt gegooid wanneer een gevraagd project niet bestaat.
 */
public class ProjectNietGevondenException extends RuntimeException {

  /** Naam van het niet-gevonden project. */
  private final String projectNaam;

  /**
   * Maakt een nieuwe ProjectNietGevondenException aan.
   *
   * @param naam Naam van het niet-gevonden project
   */
  public ProjectNietGevondenException(final String naam) {
    super("Project '%s' bestaat niet".formatted(naam));
    this.projectNaam = naam;
  }

  /**
   * Geeft de naam van het niet-gevonden project terug.
   *
   * @return Projectnaam
   */
  public String getProjectNaam() {
    return projectNaam;
  }
}
