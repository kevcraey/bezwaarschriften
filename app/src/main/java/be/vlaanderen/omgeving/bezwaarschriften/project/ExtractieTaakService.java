package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.List;
import javax.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service voor het beheren van extractie-taken in de verwerkingsqueue.
 *
 * <p>Ondersteunt het indienen, oppakken, afronden en foutafhandeling van extractie-taken
 * met een configureerbaar maximum aantal gelijktijdige verwerkingen.
 */
@Service
public class ExtractieTaakService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ExtractieTaakRepository repository;
  private final ExtractieNotificatie notificatie;
  private final int maxConcurrent;

  /**
   * Maakt een nieuwe ExtractieTaakService aan.
   *
   * @param repository repository voor extractie-taken
   * @param notificatie notificatie-interface voor statuswijzigingen
   * @param maxConcurrent maximum aantal gelijktijdig verwerkbare taken
   */
  public ExtractieTaakService(
      ExtractieTaakRepository repository,
      ExtractieNotificatie notificatie,
      @Value("${bezwaarschriften.extractie.max-concurrent:3}") int maxConcurrent) {
    this.repository = repository;
    this.notificatie = notificatie;
    this.maxConcurrent = maxConcurrent;
  }

  /**
   * Dient nieuwe extractie-taken in met status WACHTEND.
   *
   * @param projectNaam naam van het project
   * @param bestandsnamen lijst van bestandsnamen waarvoor taken worden aangemaakt
   * @return lijst van aangemaakte taak-DTOs
   */
  @Transactional
  public List<ExtractieTaakDto> indienen(String projectNaam, List<String> bestandsnamen) {
    return bestandsnamen.stream()
        .map(bestandsnaam -> {
          var taak = new ExtractieTaak();
          taak.setProjectNaam(projectNaam);
          taak.setBestandsnaam(bestandsnaam);
          taak.setStatus(ExtractieTaakStatus.WACHTEND);
          taak.setAantalPogingen(0);
          taak.setMaxPogingen(3);
          taak.setAangemaaktOp(Instant.now());
          var opgeslagen = repository.save(taak);
          var dto = ExtractieTaakDto.van(opgeslagen);
          notificatie.taakGewijzigd(dto);
          LOGGER.info("Extractie-taak ingediend: project='{}', bestand='{}'",
              projectNaam, bestandsnaam);
          return dto;
        })
        .toList();
  }

  /**
   * Geeft alle extractie-taken voor een project.
   *
   * @param projectNaam naam van het project
   * @return lijst van taak-DTOs
   */
  public List<ExtractieTaakDto> geefTaken(String projectNaam) {
    return repository.findByProjectNaam(projectNaam).stream()
        .map(ExtractieTaakDto::van)
        .toList();
  }

  /**
   * Pakt wachtende taken op voor verwerking tot het maximum aantal gelijktijdige taken.
   *
   * <p>Berekent het aantal beschikbare slots op basis van het aantal taken met status BEZIG
   * en het geconfigureerde maximum. Pakt de oudste wachtende taken op en zet hun status op BEZIG.
   *
   * @return lijst van opgepakte taken (entiteiten), leeg als geen slots beschikbaar
   */
  @Transactional
  public List<ExtractieTaak> pakOpVoorVerwerking() {
    long aantalBezig = repository.countByStatus(ExtractieTaakStatus.BEZIG);
    int beschikbareSlots = maxConcurrent - (int) aantalBezig;

    if (beschikbareSlots <= 0) {
      LOGGER.debug("Geen beschikbare slots (bezig={}, max={})", aantalBezig, maxConcurrent);
      return List.of();
    }

    var wachtend = repository.findByStatusOrderByAangemaaktOpAsc(ExtractieTaakStatus.WACHTEND);
    var opTePakken = wachtend.stream().limit(beschikbareSlots).toList();

    for (var taak : opTePakken) {
      taak.setStatus(ExtractieTaakStatus.BEZIG);
      taak.setVerwerkingGestartOp(Instant.now());
      repository.save(taak);
      notificatie.taakGewijzigd(ExtractieTaakDto.van(taak));
      LOGGER.info("Taak {} opgepakt voor verwerking: project='{}', bestand='{}'",
          taak.getId(), taak.getProjectNaam(), taak.getBestandsnaam());
    }

    return opTePakken;
  }

  /**
   * Markeert een taak als succesvol afgerond.
   *
   * @param taakId id van de taak
   * @param aantalWoorden aantal woorden in het verwerkte bestand
   * @param aantalBezwaren aantal geextraheerde bezwaren
   */
  @Transactional
  public void markeerKlaar(Long taakId, int aantalWoorden, int aantalBezwaren) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    taak.setStatus(ExtractieTaakStatus.KLAAR);
    taak.setAantalWoorden(aantalWoorden);
    taak.setAantalBezwaren(aantalBezwaren);
    taak.setAfgerondOp(Instant.now());
    repository.save(taak);
    notificatie.taakGewijzigd(ExtractieTaakDto.van(taak));
    LOGGER.info("Taak {} afgerond: {} woorden, {} bezwaren", taakId, aantalWoorden, aantalBezwaren);
  }

  /**
   * Markeert een taak als mislukt. Indien het maximum aantal pogingen nog niet bereikt is,
   * wordt de taak teruggezet naar WACHTEND voor een nieuwe poging.
   *
   * @param taakId id van de taak
   * @param foutmelding omschrijving van de fout
   */
  @Transactional
  public void markeerFout(Long taakId, String foutmelding) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    taak.setAantalPogingen(taak.getAantalPogingen() + 1);

    if (taak.getAantalPogingen() < taak.getMaxPogingen()) {
      taak.setStatus(ExtractieTaakStatus.WACHTEND);
      taak.setVerwerkingGestartOp(null);
      LOGGER.warn("Taak {} mislukt (poging {}/{}), terug naar wachtend: {}",
          taakId, taak.getAantalPogingen(), taak.getMaxPogingen(), foutmelding);
    } else {
      taak.setStatus(ExtractieTaakStatus.FOUT);
      taak.setFoutmelding(foutmelding);
      taak.setAfgerondOp(Instant.now());
      LOGGER.error("Taak {} definitief mislukt na {} pogingen: {}",
          taakId, taak.getAantalPogingen(), foutmelding);
    }

    repository.save(taak);
    notificatie.taakGewijzigd(ExtractieTaakDto.van(taak));
  }

  /**
   * Geeft de meest recente extractie-taak voor een bestand binnen een project.
   *
   * @param projectNaam naam van het project
   * @param bestandsnaam naam van het bestand
   * @return de meest recente taak, of null als er geen bestaat
   */
  public ExtractieTaak geefLaatsteTaak(String projectNaam, String bestandsnaam) {
    return repository.findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
        projectNaam, bestandsnaam).orElse(null);
  }
}
