package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PassageValidator {

  private static final Logger LOG =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final double FUZZY_DREMPEL = 0.90;

  public record ValidatieResultaat(int aantalNietGevonden) {}

  /**
   * Valideert of de passage-tekst van elk bezwaar daadwerkelijk voorkomt in de
   * originele documenttekst. Zet {@code passageGevonden} op elke entiteit.
   */
  public ValidatieResultaat valideer(
      List<IndividueelBezwaar> bezwaren,
      String documentTekst) {

    String genormaliseerdeDocument = normaliseer(documentTekst);
    int aantalNietGevonden = 0;

    for (int i = 0; i < bezwaren.size(); i++) {
      IndividueelBezwaar bezwaar = bezwaren.get(i);
      String passage = bezwaar.getPassageTekst();

      if (passage == null || passage.isBlank()) {
        LOG.warn("Passage ontbreekt voor bezwaar #{} '{}'",
            i + 1, bezwaar.getSamenvatting());
        bezwaar.setPassageGevonden(false);
        aantalNietGevonden++;
        continue;
      }

      String genormaliseerdePassage = normaliseer(passage);

      if (genormaliseerdePassage.isEmpty()
          || genormaliseerdeDocument.isEmpty()) {
        bezwaar.setPassageGevonden(false);
        aantalNietGevonden++;
        continue;
      }

      if (genormaliseerdeDocument.contains(genormaliseerdePassage)) {
        bezwaar.setPassageGevonden(true);
        LOG.debug("Passage #{} exact gevonden", i + 1);
      } else if (fuzzyMatch(
          genormaliseerdePassage, genormaliseerdeDocument)) {
        bezwaar.setPassageGevonden(true);
        LOG.debug("Passage #{} fuzzy gevonden", i + 1);
      } else {
        bezwaar.setPassageGevonden(false);
        aantalNietGevonden++;
        LOG.warn("Passage #{} NIET gevonden: '{}'",
            i + 1,
            passage.length() > 80
                ? passage.substring(0, 80) + "..." : passage);
      }
    }

    return new ValidatieResultaat(aantalNietGevonden);
  }

  String normaliseer(String tekst) {
    if (tekst == null) {
      return "";
    }
    return tekst.replaceAll("\\s+", " ").trim().toLowerCase();
  }

  boolean fuzzyMatch(String passage, String document) {
    if (passage.length() > document.length()) {
      return berekenSimilarity(passage, document) >= FUZZY_DREMPEL;
    }

    int vensterGrootte = passage.length();
    List<String> passageBigrams = bigrams(passage);

    for (int i = 0; i <= document.length() - vensterGrootte; i++) {
      String venster = document.substring(i, i + vensterGrootte);
      double similarity =
          berekenSimilarityMetBigrams(passageBigrams, venster);
      if (similarity >= FUZZY_DREMPEL) {
        return true;
      }
    }
    return false;
  }

  double berekenSimilarity(String tekst1, String tekst2) {
    List<String> bigrams1 = bigrams(tekst1);
    List<String> bigrams2 = bigrams(tekst2);

    if (bigrams1.isEmpty() && bigrams2.isEmpty()) {
      return 1.0;
    }
    if (bigrams1.isEmpty() || bigrams2.isEmpty()) {
      return 0.0;
    }

    List<String> kopie = new ArrayList<>(bigrams2);
    int overlap = 0;
    for (String bigram : bigrams1) {
      int index = kopie.indexOf(bigram);
      if (index >= 0) {
        overlap++;
        kopie.remove(index);
      }
    }

    return (2.0 * overlap) / (bigrams1.size() + bigrams2.size());
  }

  private double berekenSimilarityMetBigrams(
      List<String> passageBigrams, String venster) {
    List<String> vensterBigrams = bigrams(venster);

    if (passageBigrams.isEmpty() && vensterBigrams.isEmpty()) {
      return 1.0;
    }
    if (passageBigrams.isEmpty() || vensterBigrams.isEmpty()) {
      return 0.0;
    }

    List<String> kopie = new ArrayList<>(vensterBigrams);
    int overlap = 0;
    for (String bigram : passageBigrams) {
      int index = kopie.indexOf(bigram);
      if (index >= 0) {
        overlap++;
        kopie.remove(index);
      }
    }

    return (2.0 * overlap)
        / (passageBigrams.size() + vensterBigrams.size());
  }

  private List<String> bigrams(String tekst) {
    List<String> result = new ArrayList<>();
    for (int i = 0; i < tekst.length() - 1; i++) {
      result.add(tekst.substring(i, i + 2));
    }
    return result;
  }
}
