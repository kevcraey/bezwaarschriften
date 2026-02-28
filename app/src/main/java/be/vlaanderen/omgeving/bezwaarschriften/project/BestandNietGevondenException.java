package be.vlaanderen.omgeving.bezwaarschriften.project;

public class BestandNietGevondenException extends RuntimeException {
  private final String bestandsnaam;

  public BestandNietGevondenException(String bestandsnaam) {
    super("Bestand '%s' bestaat niet".formatted(bestandsnaam));
    this.bestandsnaam = bestandsnaam;
  }

  public String getBestandsnaam() {
    return bestandsnaam;
  }
}
