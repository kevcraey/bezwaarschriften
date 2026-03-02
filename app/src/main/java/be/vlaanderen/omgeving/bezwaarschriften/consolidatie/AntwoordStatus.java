package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

public record AntwoordStatus(int aantalMetAntwoord, int totaal) {

  public boolean isVolledig() {
    return totaal > 0 && aantalMetAntwoord == totaal;
  }
}
