package be.vlaanderen.omgeving.bezwaarschriften.project;

public record BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status,
    Integer aantalWoorden, Integer aantalBezwaren, boolean heeftPassagesDieNietInTekstVoorkomen,
    boolean heeftManueel, String extractieMethode,
    String tekstExtractieAangemaaktOp, String tekstExtractieGestartOp,
    Long tekstExtractieTaakId) {

  public BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status) {
    this(bestandsnaam, status, null, null, false, false, null, null, null, null);
  }

  public BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status,
      Integer aantalWoorden, Integer aantalBezwaren, boolean heeftPassagesDieNietInTekstVoorkomen,
      boolean heeftManueel, String extractieMethode) {
    this(bestandsnaam, status, aantalWoorden, aantalBezwaren,
        heeftPassagesDieNietInTekstVoorkomen, heeftManueel, extractieMethode, null, null, null);
  }
}
