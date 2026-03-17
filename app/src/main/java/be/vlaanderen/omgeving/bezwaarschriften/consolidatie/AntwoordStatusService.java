package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarAntwoordRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarReferentieEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarReferentieRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.BezwaarGroepLid;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.BezwaarGroepLidRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocument;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocumentRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.IndividueelBezwaar;
import be.vlaanderen.omgeving.bezwaarschriften.project.IndividueelBezwaarRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
  private final BezwaarGroepLidRepository bezwaarGroepLidRepository;
  private final IndividueelBezwaarRepository bezwaarRepository;
  private final BezwaarDocumentRepository documentRepository;

  public AntwoordStatusService(
      KernbezwaarReferentieRepository referentieRepository,
      KernbezwaarAntwoordRepository antwoordRepository,
      KernbezwaarRepository kernbezwaarRepository,
      BezwaarGroepLidRepository bezwaarGroepLidRepository,
      IndividueelBezwaarRepository bezwaarRepository,
      BezwaarDocumentRepository documentRepository) {
    this.referentieRepository = referentieRepository;
    this.antwoordRepository = antwoordRepository;
    this.kernbezwaarRepository = kernbezwaarRepository;
    this.bezwaarGroepLidRepository = bezwaarGroepLidRepository;
    this.bezwaarRepository = bezwaarRepository;
    this.documentRepository = documentRepository;
  }

  public Map<String, AntwoordStatus> berekenAntwoordStatus(String projectNaam) {
    var referenties = referentieRepository.findByProjectNaam(projectNaam);
    if (referenties.isEmpty()) {
      return Map.of();
    }

    // Haal bezwaar-groep leden op
    var groepIds = referenties.stream()
        .map(KernbezwaarReferentieEntiteit::getBezwaarGroepId)
        .distinct().toList();
    var alleLeden = bezwaarGroepLidRepository.findByBezwaarGroepIdIn(groepIds);
    var ledenPerGroep = alleLeden.stream()
        .collect(Collectors.groupingBy(BezwaarGroepLid::getBezwaarGroepId));

    // Bouw bestandsnaam-lookup per bezwaar-ID via document
    var alleBezwaarIds = alleLeden.stream()
        .map(BezwaarGroepLid::getBezwaarId).distinct().toList();
    var bezwaren = bezwaarRepository.findAllById(alleBezwaarIds);
    var documentIds = bezwaren.stream()
        .map(IndividueelBezwaar::getDocumentId).distinct().toList();
    var documentNaamById = documentRepository.findAllById(documentIds).stream()
        .collect(Collectors.toMap(BezwaarDocument::getId, BezwaarDocument::getBestandsnaam));
    var bestandsnaamPerBezwaarId = bezwaren.stream()
        .collect(Collectors.toMap(
            IndividueelBezwaar::getId,
            b -> documentNaamById.getOrDefault(b.getDocumentId(), "onbekend")));

    // Group: bestandsnaam -> set of kernbezwaar-IDs
    Map<String, Set<Long>> kernbezwarenPerDocument = new HashMap<>();
    for (var ref : referenties) {
      var leden = ledenPerGroep.getOrDefault(ref.getBezwaarGroepId(), List.of());
      for (var lid : leden) {
        var bestandsnaam = bestandsnaamPerBezwaarId.getOrDefault(
            lid.getBezwaarId(), "onbekend");
        kernbezwarenPerDocument
            .computeIfAbsent(bestandsnaam, k -> new HashSet<>())
            .add(ref.getKernbezwaarId());
      }
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
