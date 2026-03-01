package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;

/**
 * Port voor het groeperen van individuele bezwaren tot thema's en kernbezwaren.
 */
public interface KernbezwaarPoort {

  /**
   * Groepeert individuele bezwaarteksten tot thema's met kernbezwaren.
   *
   * @param bezwaarTeksten Lijst van individuele bezwaarteksten met hun metadata
   * @return Lijst van thema's met kernbezwaren
   */
  List<Thema> groepeer(List<BezwaarInvoer> bezwaarTeksten);

  /**
   * Invoer voor de groepering: een individueel bezwaar met bron-metadata.
   */
  record BezwaarInvoer(Long id, String bestandsnaam, String tekst) {}
}
