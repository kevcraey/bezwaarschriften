package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import java.util.List;

public record AntwoordStatus(int aantalMetAntwoord, int totaal,
    List<KernbezwaarInfo> kernbezwaren) {

  public record KernbezwaarInfo(String samenvatting, boolean beantwoord) {}

  public boolean isVolledig() {
    return totaal > 0 && aantalMetAntwoord == totaal;
  }
}
