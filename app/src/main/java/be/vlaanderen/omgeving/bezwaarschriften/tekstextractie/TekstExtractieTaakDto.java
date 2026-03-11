package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

/**
 * Data transfer object voor een tekst-extractie taak.
 *
 * @param id unieke identifier van de taak
 * @param projectNaam naam van het project
 * @param bestandsnaam naam van het bezwaarbestand
 * @param status huidige status (lowercase, met tekst-extractie- prefix)
 * @param aangemaaktOp tijdstip van aanmaak als ISO-string
 * @param verwerkingGestartOp tijdstip waarop verwerking gestart is, kan null zijn
 * @param foutmelding foutmelding bij mislukking, kan null zijn
 */
public record TekstExtractieTaakDto(
    Long id, String projectNaam, String bestandsnaam, String status,
    String aangemaaktOp, String verwerkingGestartOp, String foutmelding) {

  /**
   * Converteert een {@link TekstExtractieTaak} entiteit naar een DTO.
   *
   * @param taak de bron-entiteit
   * @return het bijbehorende DTO
   */
  static TekstExtractieTaakDto van(TekstExtractieTaak taak) {
    return new TekstExtractieTaakDto(
        taak.getId(), taak.getProjectNaam(), taak.getBestandsnaam(),
        statusNaarString(taak.getStatus()),
        taak.getAangemaaktOp().toString(),
        taak.getVerwerkingGestartOp() != null ? taak.getVerwerkingGestartOp().toString() : null,
        taak.getFoutmelding());
  }

  private static String statusNaarString(TekstExtractieTaakStatus status) {
    return switch (status) {
      case WACHTEND -> "tekst-extractie-wachtend";
      case BEZIG -> "tekst-extractie-bezig";
      case KLAAR -> "tekst-extractie-klaar";
      case MISLUKT -> "tekst-extractie-mislukt";
      case OCR_NIET_BESCHIKBAAR -> "tekst-extractie-ocr-niet-beschikbaar";
    };
  }
}
