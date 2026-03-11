package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

/**
 * Resultaat van een pseudonimisering van tekst.
 *
 * @param gepseudonimiseerdeTekst de tekst met PII vervangen door tokens
 * @param mappingId               UUID voor de-pseudonimisering via Obscuro
 */
public record PseudonimiseringResultaat(
    String gepseudonimiseerdeTekst,
    String mappingId) {
}
