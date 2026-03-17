package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Projectie van een bezwaardocument met status-informatie.
 *
 * @param bestandsnaam Naam van het bestand
 * @param tekstExtractieStatus Status van de tekst-extractie
 * @param bezwaarExtractieStatus Status van de bezwaar-extractie
 * @param aantalWoorden Aantal woorden in het document
 * @param aantalBezwaren Aantal geëxtraheerde bezwaren
 * @param heeftPassagesDieNietInTekstVoorkomen Of er passages zijn die niet in de tekst voorkomen
 * @param heeftManueel Of er manueel toegevoegde bezwaren zijn
 * @param extractieMethode De gebruikte extractiemethode (DIGITAAL, OCR, etc.)
 * @param foutmelding Eventuele foutmelding
 */
public record BezwaarBestand(
    String bestandsnaam,
    String tekstExtractieStatus,
    String bezwaarExtractieStatus,
    Integer aantalWoorden,
    Integer aantalBezwaren,
    boolean heeftPassagesDieNietInTekstVoorkomen,
    boolean heeftManueel,
    String extractieMethode,
    String foutmelding
) {}
