package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocument;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocumentRepository;
import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConsolidatieTaakService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ConsolidatieTaakRepository repository;
  private final BezwaarDocumentRepository documentRepository;
  private final ConsolidatieNotificatie notificatie;
  private final int maxConcurrent;
  private final int maxPogingen;

  public ConsolidatieTaakService(
      ConsolidatieTaakRepository repository,
      BezwaarDocumentRepository documentRepository,
      ConsolidatieNotificatie notificatie,
      @Value("${bezwaarschriften.consolidatie.max-concurrent:3}") int maxConcurrent,
      @Value("${bezwaarschriften.consolidatie.max-pogingen:3}") int maxPogingen) {
    this.repository = repository;
    this.documentRepository = documentRepository;
    this.notificatie = notificatie;
    this.maxConcurrent = maxConcurrent;
    this.maxPogingen = maxPogingen;
  }

  @Transactional
  public List<ConsolidatieTaakDto> indienen(String projectNaam, List<String> bestandsnamen) {
    return bestandsnamen.stream()
        .map(bestandsnaam -> {
          var document = documentRepository.findByProjectNaamAndBestandsnaam(
                  projectNaam, bestandsnaam)
              .orElseThrow(() -> new IllegalArgumentException(
                  "Document niet gevonden: " + projectNaam + "/" + bestandsnaam));

          var taak = new ConsolidatieTaak();
          taak.setDocumentId(document.getId());
          taak.setStatus(ConsolidatieTaakStatus.WACHTEND);
          taak.setAantalPogingen(0);
          taak.setMaxPogingen(maxPogingen);
          taak.setAangemaaktOp(Instant.now());
          var opgeslagen = repository.save(taak);
          var dto = ConsolidatieTaakDto.van(opgeslagen, projectNaam, bestandsnaam);
          notificatie.consolidatieTaakGewijzigd(dto);
          LOGGER.info("Consolidatie-taak ingediend: project='{}', bestand='{}'",
              projectNaam, bestandsnaam);
          return dto;
        })
        .toList();
  }

  public List<ConsolidatieTaakDto> geefTaken(String projectNaam) {
    var taken = repository.findByProjectNaam(projectNaam);
    var documentIds = taken.stream().map(ConsolidatieTaak::getDocumentId).distinct().toList();
    var documentenPerId = zoekDocumentenPerId(documentIds);

    return taken.stream()
        .map(taak -> {
          var document = documentenPerId.get(taak.getDocumentId());
          return ConsolidatieTaakDto.van(taak,
              document != null ? document.getProjectNaam() : null,
              document != null ? document.getBestandsnaam() : null);
        })
        .toList();
  }

  @Transactional
  public List<ConsolidatieTaak> pakOpVoorVerwerking() {
    long aantalBezig = repository.countByStatus(ConsolidatieTaakStatus.BEZIG);
    int beschikbareSlots = maxConcurrent - (int) aantalBezig;

    if (beschikbareSlots <= 0) {
      return List.of();
    }

    var wachtend = repository.findByStatusOrderByAangemaaktOpAsc(
        ConsolidatieTaakStatus.WACHTEND);
    var opTePakken = wachtend.stream().limit(beschikbareSlots).toList();

    if (!opTePakken.isEmpty()) {
      var documentIds = opTePakken.stream()
          .map(ConsolidatieTaak::getDocumentId).distinct().toList();
      var documentenPerId = zoekDocumentenPerId(documentIds);

      for (var taak : opTePakken) {
        taak.setStatus(ConsolidatieTaakStatus.BEZIG);
        taak.setVerwerkingGestartOp(Instant.now());
        repository.save(taak);
        var document = documentenPerId.get(taak.getDocumentId());
        notificatie.consolidatieTaakGewijzigd(ConsolidatieTaakDto.van(taak,
            document != null ? document.getProjectNaam() : null,
            document != null ? document.getBestandsnaam() : null));
        LOGGER.info("Consolidatie-taak {} opgepakt: documentId={}", taak.getId(),
            taak.getDocumentId());
      }
    }

    return opTePakken;
  }

  @Transactional
  public void markeerKlaar(Long taakId) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    taak.setStatus(ConsolidatieTaakStatus.KLAAR);
    taak.setAfgerondOp(Instant.now());
    repository.save(taak);
    notificatie.consolidatieTaakGewijzigd(maakDto(taak));
    LOGGER.info("Consolidatie-taak {} afgerond", taakId);
  }

  @Transactional
  public void markeerFout(Long taakId, String foutmelding) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    taak.setAantalPogingen(taak.getAantalPogingen() + 1);

    if (taak.getAantalPogingen() < taak.getMaxPogingen()) {
      taak.setStatus(ConsolidatieTaakStatus.WACHTEND);
      taak.setVerwerkingGestartOp(null);
    } else {
      taak.setStatus(ConsolidatieTaakStatus.FOUT);
      taak.setFoutmelding(foutmelding);
      taak.setAfgerondOp(Instant.now());
    }

    repository.save(taak);
    notificatie.consolidatieTaakGewijzigd(maakDto(taak));
  }

  public List<String> vindKlareBestandsnamen(String projectNaam,
      Collection<String> bestandsnamen) {
    var documentIds = zoekDocumentIdsVoorBestandsnamen(projectNaam, bestandsnamen);
    var klareTaken = repository.findByDocumentIdInAndStatus(
        documentIds, ConsolidatieTaakStatus.KLAAR);
    var klareDocumentIds = klareTaken.stream()
        .map(ConsolidatieTaak::getDocumentId).distinct().toList();
    var documentenPerId = zoekDocumentenPerId(klareDocumentIds);
    return klareTaken.stream()
        .map(taak -> {
          var document = documentenPerId.get(taak.getDocumentId());
          return document != null ? document.getBestandsnaam() : null;
        })
        .distinct()
        .toList();
  }

  @Transactional
  public void verwijderKlareTaken(String projectNaam, Collection<String> bestandsnamen) {
    var documentIds = zoekDocumentIdsVoorBestandsnamen(projectNaam, bestandsnamen);
    var taken = repository.findByDocumentIdInAndStatus(documentIds, ConsolidatieTaakStatus.KLAAR);
    repository.deleteAll(taken);
    LOGGER.info("{} klare consolidatie-taken verwijderd voor project '{}'",
        taken.size(), projectNaam);
  }

  @Transactional
  public void verwijderTaak(String projectNaam, Long taakId) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    var document = documentRepository.findById(taak.getDocumentId())
        .orElseThrow(() -> new IllegalArgumentException(
            "Document niet gevonden voor taak: " + taakId));
    if (!document.getProjectNaam().equals(projectNaam)) {
      throw new IllegalArgumentException(
          "Taak " + taakId + " behoort niet tot project: " + projectNaam);
    }
    repository.delete(taak);
    LOGGER.info("Consolidatie-taak {} verwijderd uit project '{}'", taakId, projectNaam);
  }

  private ConsolidatieTaakDto maakDto(ConsolidatieTaak taak) {
    var document = documentRepository.findById(taak.getDocumentId()).orElse(null);
    return ConsolidatieTaakDto.van(taak,
        document != null ? document.getProjectNaam() : null,
        document != null ? document.getBestandsnaam() : null);
  }

  private Map<Long, BezwaarDocument> zoekDocumentenPerId(List<Long> documentIds) {
    return documentRepository.findAllById(documentIds).stream()
        .collect(Collectors.toMap(BezwaarDocument::getId, Function.identity()));
  }

  private List<Long> zoekDocumentIdsVoorBestandsnamen(String projectNaam,
      Collection<String> bestandsnamen) {
    return documentRepository.findByProjectNaam(projectNaam).stream()
        .filter(d -> bestandsnamen.contains(d.getBestandsnaam()))
        .map(BezwaarDocument::getId)
        .toList();
  }
}
