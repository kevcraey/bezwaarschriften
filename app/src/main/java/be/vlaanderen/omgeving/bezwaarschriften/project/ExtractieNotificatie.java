package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Interface voor het ontvangen van notificaties bij statuswijzigingen van extractie-taken.
 */
public interface ExtractieNotificatie {

  /**
   * Wordt aangeroepen wanneer de status van een extractie-taak wijzigt.
   *
   * @param taak het bijgewerkte DTO van de gewijzigde taak
   */
  void taakGewijzigd(ExtractieTaakDto taak);
}
