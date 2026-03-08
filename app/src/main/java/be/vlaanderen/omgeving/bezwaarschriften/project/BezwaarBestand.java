package be.vlaanderen.omgeving.bezwaarschriften.project;

public record BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status,
    Integer aantalWoorden, Integer aantalBezwaren, boolean heeftPassagesDieNietInTekstVoorkomen,
    boolean heeftManueel, String extractieMethode) {

  public BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status) {
    this(bestandsnaam, status, null, null, false, false, null);
  }
}
