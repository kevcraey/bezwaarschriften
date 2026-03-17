package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocument;
import be.vlaanderen.omgeving.bezwaarschriften.project.TekstExtractieStatus;

/**
 * Data transfer object voor een tekst-extractie taak.
 *
 * @param id unieke identifier van het document
 * @param projectNaam naam van het project
 * @param bestandsnaam naam van het bezwaarbestand
 * @param tekstExtractieStatus huidige status (enum naam)
 * @param foutmelding foutmelding bij mislukking, kan null zijn
 */
public record TekstExtractieTaakDto(
    Long id, String projectNaam, String bestandsnaam, String tekstExtractieStatus,
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
        document.getTekstExtractieStatus().name(),
        document.getFoutmelding());
  }
}
