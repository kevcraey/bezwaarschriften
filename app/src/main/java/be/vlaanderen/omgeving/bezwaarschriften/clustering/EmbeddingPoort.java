package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import java.util.List;

/**
 * Port voor het genereren van vector-embeddings uit tekst.
 */
public interface EmbeddingPoort {

  /**
   * Genereert embeddings voor een lijst teksten.
   *
   * @param teksten de teksten om te vectoriseren
   * @return lijst van embedding-vectoren in dezelfde volgorde als de invoer
   */
  List<float[]> genereerEmbeddings(List<String> teksten);
}
