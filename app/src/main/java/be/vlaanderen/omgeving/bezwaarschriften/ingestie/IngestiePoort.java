package be.vlaanderen.omgeving.bezwaarschriften.ingestie;

import java.nio.file.Path;

/**
 * Port interface voor file ingestie (hexagonale architectuur).
 */
public interface IngestiePoort {

  /**
   * Leest een bestand in en retourneert een Brondocument.
   *
   * @param pad Het pad naar het bestand
   * @return Een Brondocument met de inhoud en metadata
   * @throws FileIngestionException Bij ingestie problemen
   */
  Brondocument leesBestand(Path pad);
}
