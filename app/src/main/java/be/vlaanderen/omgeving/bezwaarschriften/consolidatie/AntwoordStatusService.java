package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarAntwoordRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarReferentieRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AntwoordStatusService {

  private final KernbezwaarReferentieRepository referentieRepository;
  private final KernbezwaarAntwoordRepository antwoordRepository;
  private final KernbezwaarRepository kernbezwaarRepository;

  public AntwoordStatusService(
      KernbezwaarReferentieRepository referentieRepository,
      KernbezwaarAntwoordRepository antwoordRepository,
      KernbezwaarRepository kernbezwaarRepository) {
    this.referentieRepository = referentieRepository;
    this.antwoordRepository = antwoordRepository;
    this.kernbezwaarRepository = kernbezwaarRepository;
  }

  public Map<String, AntwoordStatus> berekenAntwoordStatus(String projectNaam) {
    var referenties = referentieRepository.findByProjectNaam(projectNaam);
    if (referenties.isEmpty()) {
      return Map.of();
    }

    // TODO: task 8 - bestandsnaam ophalen via passage_groep_lid i.p.v. referentie
    // Group: bestandsnaam -> set of kernbezwaar-IDs
    Map<String, Set<Long>> kernbezwarenPerDocument = new HashMap<>();
    for (var ref : referenties) {
      kernbezwarenPerDocument
          .computeIfAbsent("onbekend", k -> new HashSet<>())
          .add(ref.getKernbezwaarId());
    }

    // All unique kernbezwaar-IDs
    var alleKernbezwaarIds = kernbezwarenPerDocument.values().stream()
        .flatMap(Set::stream)
        .distinct()
        .toList();

    // Which have an answer?
    var idsMetAntwoord = new HashSet<>(
        antwoordRepository.findKernbezwaarIdsMetAntwoord(alleKernbezwaarIds));

    // Fetch samenvattingen
    var kernbezwaarMap = kernbezwaarRepository.findAllById(alleKernbezwaarIds).stream()
        .collect(Collectors.toMap(KernbezwaarEntiteit::getId, Function.identity()));

    // Calculate per document
    return kernbezwarenPerDocument.entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> {
              var kernIds = entry.getValue();
              int totaal = kernIds.size();
              int metAntwoord = (int) kernIds.stream()
                  .filter(idsMetAntwoord::contains).count();
              var details = kernIds.stream()
                  .map(id -> {
                    var kb = kernbezwaarMap.get(id);
                    return new AntwoordStatus.KernbezwaarInfo(
                        kb != null ? kb.getSamenvatting() : "Onbekend",
                        idsMetAntwoord.contains(id));
                  })
                  .sorted((a, b) -> Boolean.compare(a.beantwoord(), b.beantwoord()))
                  .toList();
              return new AntwoordStatus(metAntwoord, totaal, details);
            }));
  }
}
