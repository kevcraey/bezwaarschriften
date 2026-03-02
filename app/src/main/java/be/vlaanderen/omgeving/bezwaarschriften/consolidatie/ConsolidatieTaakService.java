package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.List;
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
  private final ConsolidatieNotificatie notificatie;
  private final int maxConcurrent;
  private final int maxPogingen;

  public ConsolidatieTaakService(
      ConsolidatieTaakRepository repository,
      ConsolidatieNotificatie notificatie,
      @Value("${bezwaarschriften.consolidatie.max-concurrent:3}") int maxConcurrent,
      @Value("${bezwaarschriften.consolidatie.max-pogingen:3}") int maxPogingen) {
    this.repository = repository;
    this.notificatie = notificatie;
    this.maxConcurrent = maxConcurrent;
    this.maxPogingen = maxPogingen;
  }

  @Transactional
  public List<ConsolidatieTaakDto> indienen(String projectNaam, List<String> bestandsnamen) {
    return bestandsnamen.stream()
        .map(bestandsnaam -> {
          var taak = new ConsolidatieTaak();
          taak.setProjectNaam(projectNaam);
          taak.setBestandsnaam(bestandsnaam);
          taak.setStatus(ConsolidatieTaakStatus.WACHTEND);
          taak.setAantalPogingen(0);
          taak.setMaxPogingen(maxPogingen);
          taak.setAangemaaktOp(Instant.now());
          var opgeslagen = repository.save(taak);
          var dto = ConsolidatieTaakDto.van(opgeslagen);
          notificatie.consolidatieTaakGewijzigd(dto);
          LOGGER.info("Consolidatie-taak ingediend: project='{}', bestand='{}'",
              projectNaam, bestandsnaam);
          return dto;
        })
        .toList();
  }

  public List<ConsolidatieTaakDto> geefTaken(String projectNaam) {
    return repository.findByProjectNaam(projectNaam).stream()
        .map(ConsolidatieTaakDto::van)
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

    for (var taak : opTePakken) {
      taak.setStatus(ConsolidatieTaakStatus.BEZIG);
      taak.setVerwerkingGestartOp(Instant.now());
      repository.save(taak);
      notificatie.consolidatieTaakGewijzigd(ConsolidatieTaakDto.van(taak));
      LOGGER.info("Consolidatie-taak {} opgepakt: project='{}', bestand='{}'",
          taak.getId(), taak.getProjectNaam(), taak.getBestandsnaam());
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
    notificatie.consolidatieTaakGewijzigd(ConsolidatieTaakDto.van(taak));
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
    notificatie.consolidatieTaakGewijzigd(ConsolidatieTaakDto.van(taak));
  }

  @Transactional
  public void verwijderTaak(String projectNaam, Long taakId) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    if (!taak.getProjectNaam().equals(projectNaam)) {
      throw new IllegalArgumentException(
          "Taak " + taakId + " behoort niet tot project: " + projectNaam);
    }
    repository.delete(taak);
    LOGGER.info("Consolidatie-taak {} verwijderd uit project '{}'", taakId, projectNaam);
  }
}
