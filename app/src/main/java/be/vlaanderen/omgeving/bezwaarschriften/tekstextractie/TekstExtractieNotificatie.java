package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

/**
 * Interface voor het ontvangen van notificaties bij statuswijzigingen van tekst-extractie taken.
 */
public interface TekstExtractieNotificatie {

  /**
   * Wordt aangeroepen wanneer de status van een tekst-extractie taak wijzigt.
   *
   * @param taak het bijgewerkte DTO van de gewijzigde taak
   */
  void tekstExtractieTaakGewijzigd(TekstExtractieTaakDto taak);
}
