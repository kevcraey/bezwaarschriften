package be.vlaanderen.omgeving.bezwaarschriften.ingestie;

import java.time.Instant;

/**
 * Brondocument representeert een origineel ingediend document.
 *
 * @param tekst De volledige tekstinhoud van het document
 * @param bestandsnaam De naam van het bronbestand
 * @param pad Het volledige pad naar het bronbestand
 * @param timestamp Het tijdstip waarop het document werd ingelezen
 */
public record Brondocument(
    String tekst,
    String bestandsnaam,
    String pad,
    Instant timestamp
) {
}
