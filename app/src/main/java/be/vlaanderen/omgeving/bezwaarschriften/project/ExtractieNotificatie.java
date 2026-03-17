package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Interface voor het ontvangen van notificaties bij statuswijzigingen van bezwaar-extractie.
 */
public interface ExtractieNotificatie {

  /**
   * Wordt aangeroepen wanneer de bezwaar-extractie status van een document wijzigt.
   *
   * @param taak het bijgewerkte DTO van het gewijzigde document
   */
  void taakGewijzigd(ExtractieTaakDto taak);
}
