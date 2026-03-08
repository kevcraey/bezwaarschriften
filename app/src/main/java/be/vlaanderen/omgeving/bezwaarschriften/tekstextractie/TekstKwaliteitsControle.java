package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import org.springframework.stereotype.Component;

/** Deterministische kwaliteitscontrole voor geextraheerde tekst. */
@Component
public class TekstKwaliteitsControle {

  private static final int MINIMUM_WOORDEN = 100;
  private static final double MINIMUM_ALFANUMERIEK_RATIO = 0.70;
  private static final double MINIMUM_KLINKER_RATIO = 0.20;
  private static final double MAXIMUM_KLINKER_RATIO = 0.60;
  private static final String KLINKERS = "aeiouAEIOU";

  /** Resultaat van een kwaliteitscontrole. */
  public record Resultaat(boolean isValide, String reden) {

    public static Resultaat valide() {
      return new Resultaat(true, null);
    }

    public static Resultaat ongeldig(String reden) {
      return new Resultaat(false, reden);
    }
  }

  /** Controleert de kwaliteit van de geextraheerde tekst. */
  public Resultaat controleer(String tekst) {
    if (tekst == null || tekst.isBlank()) {
      return Resultaat.ongeldig("Tekst is leeg of null");
    }

    String[] woorden = tekst.trim().split("\\s+");
    if (woorden.length < MINIMUM_WOORDEN) {
      return Resultaat.ongeldig(
          String.format("Te weinig woorden: %d (minimum %d)",
              woorden.length, MINIMUM_WOORDEN));
    }

    String zonderSpaties = tekst.replaceAll("\\s", "");
    if (!zonderSpaties.isEmpty()) {
      long aantalAlfanumeriek = zonderSpaties.chars()
          .filter(Character::isLetterOrDigit)
          .count();
      double alfanumeriekRatio =
          (double) aantalAlfanumeriek / zonderSpaties.length();
      if (alfanumeriekRatio < MINIMUM_ALFANUMERIEK_RATIO) {
        return Resultaat.ongeldig(
            String.format(
                "Te weinig alfanumerieke tekens: %.0f%% (minimum %.0f%%)",
                alfanumeriekRatio * 100,
                MINIMUM_ALFANUMERIEK_RATIO * 100));
      }
    }

    long aantalLetters = tekst.chars()
        .filter(Character::isLetter)
        .count();
    if (aantalLetters > 0) {
      long aantalKlinkers = tekst.chars()
          .filter(c -> KLINKERS.indexOf(c) >= 0)
          .count();
      double klinkerRatio = (double) aantalKlinkers / aantalLetters;
      if (klinkerRatio < MINIMUM_KLINKER_RATIO
          || klinkerRatio > MAXIMUM_KLINKER_RATIO) {
        return Resultaat.ongeldig(
            String.format(
                "Ongewone klinker/letter-ratio: %.0f%% "
                    + "(verwacht tussen %.0f%% en %.0f%%)",
                klinkerRatio * 100,
                MINIMUM_KLINKER_RATIO * 100,
                MAXIMUM_KLINKER_RATIO * 100));
      }
    }

    return Resultaat.valide();
  }
}
