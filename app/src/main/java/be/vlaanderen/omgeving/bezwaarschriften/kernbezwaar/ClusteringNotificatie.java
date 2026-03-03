package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

/**
 * Interface voor het ontvangen van notificaties bij statuswijzigingen van clustering-taken.
 */
public interface ClusteringNotificatie {

  /**
   * Wordt aangeroepen wanneer de status van een clustering-taak wijzigt.
   *
   * @param taak het bijgewerkte DTO van de gewijzigde taak
   */
  void clusteringTaakGewijzigd(ClusteringTaakDto taak);
}
