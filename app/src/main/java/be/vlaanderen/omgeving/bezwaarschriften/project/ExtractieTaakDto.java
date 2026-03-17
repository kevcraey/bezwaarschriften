package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Data transfer object voor een extractie-taak.
 *
 * @param id unieke identifier van de taak
 * @param projectNaam naam van het project
 * @param bestandsnaam naam van het bezwaarbestand
 * @param status huidige status (lowercase)
 * @param aantalPogingen aantal uitgevoerde pogingen
 * @param aangemaaktOp tijdstip van aanmaak als ISO-string
 * @param verwerkingGestartOp tijdstip waarop verwerking gestart is, kan null zijn
 * @param aantalWoorden aantal woorden in het bestand, kan null zijn
 * @param aantalBezwaren aantal geextraheerde bezwaren, kan null zijn
 * @param foutmelding foutmelding bij mislukking, kan null zijn
 * @param heeftPassagesDieNietInTekstVoorkomen of er passages zijn die niet in de tekst voorkomen
 * @param heeftManueel of er manueel toegevoegde bezwaren zijn
 * @param afgerondOp tijdstip waarop de taak is afgerond, kan null zijn
 */
public record ExtractieTaakDto(
    Long id, String projectNaam, String bestandsnaam, String status,
    int aantalPogingen, String aangemaaktOp, String verwerkingGestartOp,
    Integer aantalWoorden, Integer aantalBezwaren, String foutmelding,
    boolean heeftPassagesDieNietInTekstVoorkomen, boolean heeftManueel,
    String afgerondOp) {

  /**
   * Converteert een {@link ExtractieTaak} entiteit naar een DTO.
   *
   * @param taak de bron-entiteit
   * @return het bijbehorende DTO
   */
  static ExtractieTaakDto van(ExtractieTaak taak) {
    return new ExtractieTaakDto(
        taak.getId(), taak.getProjectNaam(), taak.getBestandsnaam(),
        statusNaarString(taak.getStatus()), taak.getAantalPogingen(),
        taak.getAangemaaktOp().toString(),
        taak.getVerwerkingGestartOp() != null ? taak.getVerwerkingGestartOp().toString() : null,
        taak.getAantalWoorden(), taak.getAantalBezwaren(), taak.getFoutmelding(),
        taak.isHeeftPassagesDieNietInTekstVoorkomen(), taak.isHeeftManueel(),
        taak.getAfgerondOp() != null ? taak.getAfgerondOp().toString() : null);
  }

  private static String statusNaarString(ExtractieTaakStatus status) {
    return switch (status) {
      case WACHTEND -> "bezwaar-extractie-wachtend";
      case BEZIG -> "bezwaar-extractie-bezig";
      case KLAAR -> "bezwaar-extractie-klaar";
      case FOUT -> "bezwaar-extractie-fout";
    };
  }
}
