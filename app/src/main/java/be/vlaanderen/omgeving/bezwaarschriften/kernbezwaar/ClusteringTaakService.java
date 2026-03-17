package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import be.vlaanderen.omgeving.bezwaarschriften.project.IndividueelBezwaarRepository;
import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Beheert de levenscyclus van clustering-taken: indienen, statusupdates,
 * annuleren en verwijderen van clusteringresultaten.
 */
@Service
public class ClusteringTaakService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ClusteringTaakRepository taakRepository;
  private final KernbezwaarRepository kernbezwaarRepository;
  private final KernbezwaarAntwoordRepository antwoordRepository;
  private final IndividueelBezwaarRepository bezwaarRepository;
  private final ClusteringNotificatie notificatie;
  private final int maxConcurrent;

  public ClusteringTaakService(ClusteringTaakRepository taakRepository,
      KernbezwaarRepository kernbezwaarRepository,
      KernbezwaarAntwoordRepository antwoordRepository,
      IndividueelBezwaarRepository bezwaarRepository,
      ClusteringNotificatie notificatie,
      @Value("${bezwaarschriften.clustering.max-concurrent:2}") int maxConcurrent) {
    this.taakRepository = taakRepository;
    this.kernbezwaarRepository = kernbezwaarRepository;
    this.antwoordRepository = antwoordRepository;
    this.bezwaarRepository = bezwaarRepository;
    this.notificatie = notificatie;
    this.maxConcurrent = maxConcurrent;
  }

  /**
   * Dient een nieuwe clustering-taak in voor een project.
   * Verwijdert een eventueel bestaande taak.
   *
   * @param projectNaam naam van het project
   * @param deduplicatieVoorClustering of passage-deduplicatie voor clustering uitgevoerd moet worden
   * @return DTO van de aangemaakte taak
   */
  @Transactional
  public ClusteringTaakDto indienen(String projectNaam, boolean deduplicatieVoorClustering) {
    // Verwijder bestaande taak als die er is
    taakRepository.findByProjectNaam(projectNaam)
        .ifPresent(bestaandeTaak -> {
          taakRepository.delete(bestaandeTaak);
          taakRepository.flush(); // forceer DELETE naar DB voor de nieuwe INSERT
        });

    // Maak nieuwe taak aan
    var taak = new ClusteringTaak();
    taak.setProjectNaam(projectNaam);
    taak.setStatus(ClusteringTaakStatus.WACHTEND);
    taak.setDeduplicatieVoorClustering(deduplicatieVoorClustering);
    taak.setAangemaaktOp(Instant.now());
    taak = taakRepository.save(taak);

    int aantalBezwaren = bezwaarRepository.countByProjectNaam(projectNaam);
    var dto = ClusteringTaakDto.van(taak, aantalBezwaren, null);
    notificatie.clusteringTaakGewijzigd(dto);
    return dto;
  }

  /**
   * Pakt wachtende clustering-taken op voor verwerking tot het maximum
   * aantal gelijktijdige taken. Zet de status op BEZIG.
   *
   * @return lijst van opgepakte taken, leeg als geen slots beschikbaar
   */
  @Transactional
  public List<ClusteringTaak> pakOpVoorVerwerking() {
    int aantalBezig = taakRepository.countByStatus(ClusteringTaakStatus.BEZIG);
    int beschikbareSlots = maxConcurrent - aantalBezig;

    if (beschikbareSlots <= 0) {
      LOGGER.debug("Geen clustering-slots beschikbaar (bezig={}, max={})",
          aantalBezig, maxConcurrent);
      return List.of();
    }

    var wachtend = taakRepository.findByStatusOrderByAangemaaktOpAsc(
        ClusteringTaakStatus.WACHTEND);
    var opTePakken = wachtend.stream().limit(beschikbareSlots).toList();

    for (var taak : opTePakken) {
      taak.setStatus(ClusteringTaakStatus.BEZIG);
      taak.setVerwerkingGestartOp(Instant.now());
      taakRepository.save(taak);

      int aantalBezwaren = bezwaarRepository.countByProjectNaam(
          taak.getProjectNaam());
      notificatie.clusteringTaakGewijzigd(
          ClusteringTaakDto.van(taak, aantalBezwaren, null));
      LOGGER.info("Clustering-taak {} opgepakt: project='{}'",
          taak.getId(), taak.getProjectNaam());
    }

    return opTePakken;
  }

  /**
   * Markeert een taak als klaar en berekent het aantal kernbezwaren.
   *
   * @param taakId ID van de taak
   */
  @Transactional
  public void markeerKlaar(Long taakId) {
    var taak = taakRepository.findById(taakId).orElseThrow(
        () -> new IllegalArgumentException("Taak niet gevonden: " + taakId));

    taak.setStatus(ClusteringTaakStatus.KLAAR);
    taak.setVerwerkingVoltooidOp(Instant.now());
    taakRepository.save(taak);

    int aantalBezwaren = bezwaarRepository.countByProjectNaam(
        taak.getProjectNaam());
    int aantalKernbezwaren = kernbezwaarRepository.countByProjectNaam(
        taak.getProjectNaam());
    var dto = ClusteringTaakDto.van(taak, aantalBezwaren, aantalKernbezwaren);
    notificatie.clusteringTaakGewijzigd(dto);
  }

  /**
   * Markeert een taak als fout met een foutmelding.
   *
   * @param taakId ID van de taak
   * @param foutmelding beschrijving van de fout
   */
  @Transactional
  public void markeerFout(Long taakId, String foutmelding) {
    var taak = taakRepository.findById(taakId).orElseThrow(
        () -> new IllegalArgumentException("Taak niet gevonden: " + taakId));

    taak.setStatus(ClusteringTaakStatus.FOUT);
    taak.setFoutmelding(foutmelding);
    taak.setVerwerkingVoltooidOp(Instant.now());
    taakRepository.save(taak);

    int aantalBezwaren = bezwaarRepository.countByProjectNaam(
        taak.getProjectNaam());
    var dto = ClusteringTaakDto.van(taak, aantalBezwaren, null);
    notificatie.clusteringTaakGewijzigd(dto);
  }

  /**
   * Controleert of een taak geannuleerd is (= verwijderd uit de database).
   *
   * @param taakId ID van de taak
   * @return true als de taak niet meer bestaat
   */
  public boolean isGeannuleerd(Long taakId) {
    return !taakRepository.existsById(taakId);
  }

  /**
   * Annuleert een taak als die status WACHTEND of BEZIG heeft.
   * De taak wordt verwijderd uit de database.
   *
   * @param taakId ID van de taak
   * @return true als de taak geannuleerd is, false als de status dat niet toelaat
   */
  @Transactional
  public boolean annuleer(Long taakId) {
    var taak = taakRepository.findById(taakId).orElse(null);
    if (taak == null) {
      return false;
    }

    if (taak.getStatus() != ClusteringTaakStatus.WACHTEND
        && taak.getStatus() != ClusteringTaakStatus.BEZIG) {
      return false;
    }

    taakRepository.delete(taak);
    LOGGER.info("Clustering-taak {} geannuleerd en verwijderd: project='{}'",
        taak.getId(), taak.getProjectNaam());
    return true;
  }

  /**
   * Geeft de clustering-taak voor een project met bijbehorende counts.
   *
   * @param projectNaam naam van het project
   * @return de taak als Optional, leeg als er geen taak is
   */
  public Optional<ClusteringTaakDto> geefTaak(String projectNaam) {
    return taakRepository.findByProjectNaam(projectNaam)
        .map(taak -> {
          int aantalBezwaren = bezwaarRepository.countByProjectNaam(projectNaam);
          Integer aantalKernbezwaren = taak.getStatus() == ClusteringTaakStatus.KLAAR
              ? kernbezwaarRepository.countByProjectNaam(projectNaam)
              : null;
          return ClusteringTaakDto.van(taak, aantalBezwaren, aantalKernbezwaren);
        });
  }

  /**
   * Verwijdert clusteringresultaten voor een project. Als er antwoorden aan
   * kernbezwaren gekoppeld zijn en bevestiging niet gegeven is, wordt eerst om
   * bevestiging gevraagd.
   *
   * @param projectNaam naam van het project
   * @param bevestigd true als de gebruiker bevestigd heeft dat antwoorden verloren mogen gaan
   * @return resultaat met verwijderstatus en eventueel bevestigingsverzoek
   */
  @Transactional
  public VerwijderResultaat verwijderClustering(String projectNaam, boolean bevestigd) {
    var kernbezwaren = kernbezwaarRepository.findByProjectNaam(projectNaam);

    if (kernbezwaren.isEmpty()) {
      // Geen kernbezwaren, verwijder eventuele taak
      taakRepository.deleteByProjectNaam(projectNaam);
      return VerwijderResultaat.succesvolVerwijderd();
    }

    var kernIds = kernbezwaren.stream().map(KernbezwaarEntiteit::getId).toList();

    // Controleer op gekoppelde antwoorden
    if (!bevestigd) {
      long aantalAntwoorden = antwoordRepository.countByKernbezwaarIdIn(kernIds);
      if (aantalAntwoorden > 0) {
        return VerwijderResultaat.bevestigingVereist(aantalAntwoorden);
      }
    }

    // Verwijder kernbezwaren en taak
    kernbezwaarRepository.deleteAll(kernbezwaren);
    taakRepository.deleteByProjectNaam(projectNaam);

    return VerwijderResultaat.succesvolVerwijderd();
  }
}
