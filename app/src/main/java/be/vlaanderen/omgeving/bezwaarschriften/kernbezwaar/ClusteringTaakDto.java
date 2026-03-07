package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.time.Instant;

/**
 * Data transfer object voor een clustering-taak.
 *
 * @param id unieke identifier van de taak
 * @param projectNaam naam van het project
 * @param status huidige status (lowercase)
 * @param aantalBezwaren aantal individuele bezwaren in het project
 * @param aantalKernbezwaren aantal gevonden kernbezwaren, kan null zijn als clustering nog niet klaar is
 * @param aangemaaktOp tijdstip van aanmaak
 * @param verwerkingGestartOp tijdstip waarop verwerking gestart is, kan null zijn
 * @param verwerkingVoltooidOp tijdstip waarop verwerking voltooid is, kan null zijn
 * @param foutmelding foutmelding bij mislukking, kan null zijn
 */
public record ClusteringTaakDto(
    Long id,
    String projectNaam,
    String status,
    int aantalBezwaren,
    Integer aantalKernbezwaren,
    Instant aangemaaktOp,
    Instant verwerkingGestartOp,
    Instant verwerkingVoltooidOp,
    String foutmelding
) {

  /**
   * Converteert een {@link ClusteringTaak} entiteit naar een DTO.
   *
   * @param taak de bron-entiteit
   * @param aantalBezwaren het aantal bezwaren in het project
   * @param aantalKernbezwaren het aantal kernbezwaren, of null als nog niet geclusterd
   * @return het bijbehorende DTO
   */
  public static ClusteringTaakDto van(ClusteringTaak taak, int aantalBezwaren,
      Integer aantalKernbezwaren) {
    return new ClusteringTaakDto(
        taak.getId(),
        taak.getProjectNaam(),
        taak.getStatus().name().toLowerCase(),
        aantalBezwaren,
        aantalKernbezwaren,
        taak.getAangemaaktOp(),
        taak.getVerwerkingGestartOp(),
        taak.getVerwerkingVoltooidOp(),
        taak.getFoutmelding()
    );
  }
}
