package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Data transfer object voor bezwaar-extractie status van een document.
 *
 * @param id unieke identifier van het document
 * @param projectNaam naam van het project
 * @param bestandsnaam naam van het bezwaarbestand
 * @param status huidige bezwaar-extractie status (lowercase)
 * @param aantalWoorden aantal woorden in het bestand, kan null zijn
 * @param foutmelding foutmelding bij mislukking, kan null zijn
 * @param heeftPassagesDieNietInTekstVoorkomen of er passages zijn die niet in de tekst voorkomen
 * @param heeftManueel of er manueel toegevoegde bezwaren zijn
 */
public record ExtractieTaakDto(
    Long id, String projectNaam, String bestandsnaam, String status,
    Integer aantalWoorden, String foutmelding,
    boolean heeftPassagesDieNietInTekstVoorkomen, boolean heeftManueel) {

  /**
   * Converteert een {@link BezwaarDocument} entiteit naar een DTO.
   *
   * @param doc de bron-entiteit
   * @return het bijbehorende DTO
   */
  static ExtractieTaakDto van(BezwaarDocument doc) {
    return new ExtractieTaakDto(
        doc.getId(), doc.getProjectNaam(), doc.getBestandsnaam(),
        statusNaarString(doc.getBezwaarExtractieStatus()),
        doc.getAantalWoorden(), doc.getFoutmelding(),
        doc.isHeeftPassagesDieNietInTekstVoorkomen(), doc.isHeeftManueel());
  }

  private static String statusNaarString(BezwaarExtractieStatus status) {
    return switch (status) {
      case GEEN -> "geen";
      case BEZIG -> "bezwaar-extractie-bezig";
      case KLAAR -> "bezwaar-extractie-klaar";
      case FOUT -> "bezwaar-extractie-fout";
    };
  }
}
