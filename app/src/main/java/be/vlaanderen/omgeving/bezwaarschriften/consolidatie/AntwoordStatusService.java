package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarAntwoordRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarReferentieRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AntwoordStatusService {

  private final KernbezwaarReferentieRepository referentieRepository;
  private final KernbezwaarAntwoordRepository antwoordRepository;

  public AntwoordStatusService(
      KernbezwaarReferentieRepository referentieRepository,
      KernbezwaarAntwoordRepository antwoordRepository) {
    this.referentieRepository = referentieRepository;
    this.antwoordRepository = antwoordRepository;
  }

  public Map<String, AntwoordStatus> berekenAntwoordStatus(String projectNaam) {
    var referenties = referentieRepository.findByProjectNaam(projectNaam);
    if (referenties.isEmpty()) {
      return Map.of();
    }

    // Group: bestandsnaam -> set of kernbezwaar-IDs
    Map<String, Set<Long>> kernbezwarenPerDocument = new HashMap<>();
    for (var ref : referenties) {
      kernbezwarenPerDocument
          .computeIfAbsent(ref.getBestandsnaam(), k -> new HashSet<>())
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

    // Calculate per document
    return kernbezwarenPerDocument.entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> {
              var kernIds = entry.getValue();
              int totaal = kernIds.size();
              int metAntwoord = (int) kernIds.stream()
                  .filter(idsMetAntwoord::contains).count();
              return new AntwoordStatus(metAntwoord, totaal);
            }));
  }
}
