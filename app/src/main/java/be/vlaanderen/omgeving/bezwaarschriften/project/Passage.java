package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Een passage uit een bezwaarschrift die als bron dient voor een of meer bezwaren.
 *
 * @param id Volgnummer van de passage binnen het document
 * @param tekst De letterlijke tekst uit het bezwaarschrift
 */
public record Passage(int id, String tekst) {}
