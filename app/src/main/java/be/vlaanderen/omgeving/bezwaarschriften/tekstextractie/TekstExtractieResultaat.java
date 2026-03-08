package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

/**
 * Resultaat van een tekst-extractie uit een document.
 *
 * @param tekst    de geextraheerde tekst
 * @param methode  de gebruikte extractiemethode (digitaal of OCR)
 * @param details  extra informatie over het extractieproces
 */
public record TekstExtractieResultaat(
    String tekst,
    ExtractieMethode methode,
    String details) {
}
