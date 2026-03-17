package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocument;
import be.vlaanderen.omgeving.bezwaarschriften.project.TekstExtractieStatus;

/**
 * Data transfer object voor een tekst-extractie taak.
 *
 * @param id unieke identifier van het document
 * @param projectNaam naam van het project
 * @param bestandsnaam naam van het bezwaarbestand
 * @param status huidige status (lowercase, met tekst-extractie- prefix)
 * @param foutmelding foutmelding bij mislukking, kan null zijn
 */
public record TekstExtractieTaakDto(
    Long id, String projectNaam, String bestandsnaam, String status,
    String foutmelding) {

  /**
   * Converteert een {@link BezwaarDocument} entiteit naar een DTO.
   *
   * @param document het brondocument
   * @return het bijbehorende DTO
   */
  static TekstExtractieTaakDto van(BezwaarDocument document) {
    return new TekstExtractieTaakDto(
        document.getId(), document.getProjectNaam(), document.getBestandsnaam(),
        statusNaarString(document.getTekstExtractieStatus()),
        document.getFoutmelding());
  }

  private static String statusNaarString(TekstExtractieStatus status) {
    return switch (status) {
      case GEEN -> "tekst-extractie-geen";
      case BEZIG -> "tekst-extractie-bezig";
      case KLAAR -> "tekst-extractie-klaar";
      case FOUT -> "tekst-extractie-fout";
    };
  }
}
