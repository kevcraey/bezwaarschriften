package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.time.Instant;

/**
 * Data transfer object voor bezwaar-extractie status van een document.
 *
 * @param id unieke identifier van het document
 * @param projectNaam naam van het project
 * @param bestandsnaam naam van het bezwaarbestand
 * @param bezwaarExtractieStatus huidige bezwaar-extractie status (enum naam)
 * @param aantalWoorden aantal woorden in het bestand, kan null zijn
 * @param aantalBezwaren aantal geextraheerde bezwaren, kan null zijn
 * @param foutmelding foutmelding bij mislukking, kan null zijn
 * @param heeftPassagesDieNietInTekstVoorkomen of er passages zijn die niet in de tekst voorkomen
 * @param heeftManueel of er manueel toegevoegde bezwaren zijn
 * @param aangemaaktOp tijdstip waarop de extractie ingediend is
 * @param verwerkingGestartOp tijdstip waarop de verwerking daadwerkelijk gestart is
 */
public record ExtractieTaakDto(
    Long id, String projectNaam, String bestandsnaam, String bezwaarExtractieStatus,
    Integer aantalWoorden, Integer aantalBezwaren, String foutmelding,
    boolean heeftPassagesDieNietInTekstVoorkomen, boolean heeftManueel,
    Instant aangemaaktOp, Instant verwerkingGestartOp) {

  /**
   * Converteert een {@link BezwaarDocument} entiteit naar een DTO.
   *
   * @param doc de bron-entiteit
   * @return het bijbehorende DTO
   */
  static ExtractieTaakDto van(BezwaarDocument doc) {
    return van(doc, null);
  }

  /**
   * Converteert een {@link BezwaarDocument} entiteit naar een DTO met aantalBezwaren.
   *
   * @param doc de bron-entiteit
   * @param aantalBezwaren aantal geextraheerde bezwaren, kan null zijn
   * @return het bijbehorende DTO
   */
  static ExtractieTaakDto van(BezwaarDocument doc, Integer aantalBezwaren) {
    return new ExtractieTaakDto(
        doc.getId(), doc.getProjectNaam(), doc.getBestandsnaam(),
        doc.getBezwaarExtractieStatus().name(),
        doc.getAantalWoorden(), aantalBezwaren, doc.getFoutmelding(),
        doc.isHeeftPassagesDieNietInTekstVoorkomen(), doc.isHeeftManueel(),
        doc.getExtractieIngediendOp(), doc.getExtractieGestartOp());
  }
}
