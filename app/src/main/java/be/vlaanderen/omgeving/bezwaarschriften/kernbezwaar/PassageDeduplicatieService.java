package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarEntiteit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PassageDeduplicatieService {

  private static final double GELIJKENIS_DREMPEL = 0.9;

  public record DeduplicatieGroep(
      String passage,
      String samenvatting,
      GeextraheerdBezwaarEntiteit representatief,
      List<DeduplicatieLid> leden) {}

  public record DeduplicatieLid(Long bezwaarId, String bestandsnaam) {}

  public List<DeduplicatieGroep> groepeer(
      List<GeextraheerdBezwaarEntiteit> bezwaren,
      Map<Long, Map<Integer, String>> passageLookup) {

    if (bezwaren.isEmpty()) {
      return List.of();
    }

    var groepen = new ArrayList<GroepBouwer>();

    for (var bezwaar : bezwaren) {
      var passageTekst = geefPassageTekst(bezwaar, passageLookup);
      var bigramInfo = berekenBigramInfo(passageTekst);
      var bestandsnaam = bezwaar.getBestandsnaam();
      boolean gevonden = false;

      for (var groep : groepen) {
        if (berekenDiceCoefficient(bigramInfo, groep.bigramInfo) >= GELIJKENIS_DREMPEL) {
          groep.voegToe(bezwaar, passageTekst, bigramInfo, bestandsnaam);
          gevonden = true;
          break;
        }
      }

      if (!gevonden) {
        groepen.add(new GroepBouwer(passageTekst, bigramInfo, bezwaar, bestandsnaam));
      }
    }

    return groepen.stream().map(GroepBouwer::bouw).toList();
  }

  // Package-private for testing
  double berekenDiceCoefficient(String a, String b) {
    return berekenDiceCoefficient(berekenBigramInfo(a), berekenBigramInfo(b));
  }

  private double berekenDiceCoefficient(BigramInfo infoA, BigramInfo infoB) {
    if (infoA.genormaliseerd.equals(infoB.genormaliseerd)) {
      return 1.0;
    }
    if (infoA.lengte < 2 || infoB.lengte < 2) {
      return 0.0;
    }

    int overlap = 0;
    for (var entry : infoA.bigrammen.entrySet()) {
      Integer countB = infoB.bigrammen.get(entry.getKey());
      if (countB != null) {
        overlap += Math.min(entry.getValue(), countB);
      }
    }
    return (2.0 * overlap) / (infoA.lengte - 1 + infoB.lengte - 1);
  }

  private BigramInfo berekenBigramInfo(String tekst) {
    var genormaliseerd = tekst == null ? "" : tekst.toLowerCase().trim();
    var bigrammen = new HashMap<String, Integer>();
    for (int i = 0; i < genormaliseerd.length() - 1; i++) {
      var bigram = genormaliseerd.substring(i, i + 2);
      bigrammen.merge(bigram, 1, Integer::sum);
    }
    return new BigramInfo(genormaliseerd, bigrammen, genormaliseerd.length());
  }

  private record BigramInfo(String genormaliseerd, Map<String, Integer> bigrammen, int lengte) {}

  private String geefPassageTekst(
      GeextraheerdBezwaarEntiteit bezwaar, Map<Long, Map<Integer, String>> passageLookup) {
    var taakPassages = passageLookup.get(bezwaar.getTaakId());
    if (taakPassages != null) {
      var tekst = taakPassages.get(bezwaar.getPassageNr());
      if (tekst != null) {
        return tekst;
      }
    }
    return bezwaar.getSamenvatting();
  }

  private static class GroepBouwer {
    String passage;
    BigramInfo bigramInfo;
    GeextraheerdBezwaarEntiteit representatief;
    List<DeduplicatieLid> leden = new ArrayList<>();

    GroepBouwer(
        String passage,
        BigramInfo bigramInfo,
        GeextraheerdBezwaarEntiteit bezwaar,
        String bestandsnaam) {
      this.passage = passage;
      this.bigramInfo = bigramInfo;
      this.representatief = bezwaar;
      this.leden.add(new DeduplicatieLid(bezwaar.getId(), bestandsnaam));
    }

    void voegToe(
        GeextraheerdBezwaarEntiteit bezwaar,
        String passageTekst,
        BigramInfo bigramInfo,
        String bestandsnaam) {
      leden.add(new DeduplicatieLid(bezwaar.getId(), bestandsnaam));
      if (passageTekst.length() > passage.length()) {
        passage = passageTekst;
        this.bigramInfo = bigramInfo;
        representatief = bezwaar;
      }
    }

    DeduplicatieGroep bouw() {
      return new DeduplicatieGroep(
          passage, representatief.getSamenvatting(), representatief, List.copyOf(leden));
    }
  }
}
