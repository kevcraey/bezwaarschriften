package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

/**
 * Resultaat van een verwijder-clustering-actie.
 *
 * @param verwijderd true als de clustering daadwerkelijk verwijderd is
 * @param bevestigingNodig true als er antwoorden gekoppeld zijn en bevestiging nodig is
 * @param aantalAntwoorden het aantal gekoppelde antwoorden dat verloren gaat bij verwijdering
 */
public record VerwijderResultaat(boolean verwijderd, boolean bevestigingNodig,
    long aantalAntwoorden) {

  public static VerwijderResultaat succesvolVerwijderd() {
    return new VerwijderResultaat(true, false, 0);
  }

  public static VerwijderResultaat bevestigingVereist(long aantalAntwoorden) {
    return new VerwijderResultaat(false, true, aantalAntwoorden);
  }
}
