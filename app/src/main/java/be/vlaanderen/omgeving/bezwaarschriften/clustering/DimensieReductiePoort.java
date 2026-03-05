package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import java.util.List;

/**
 * Port voor dimensiereductie van embedding-vectoren.
 *
 * <p>Reduceert hoog-dimensionale vectoren (bv. 1024D) naar een compactere
 * ruimte met behoud van semantische relaties, zodat clustering-algoritmen
 * beter presteren.
 */
public interface DimensieReductiePoort {

  /**
   * Reduceert de gegeven vectoren naar een lagere dimensie.
   *
   * @param vectoren lijst van embedding-vectoren
   * @return lijst van gereduceerde vectoren in dezelfde volgorde
   */
  List<float[]> reduceer(List<float[]> vectoren);
}
