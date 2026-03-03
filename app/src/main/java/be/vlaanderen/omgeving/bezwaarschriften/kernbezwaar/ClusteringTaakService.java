package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarRepository;
import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
  private final ThemaRepository themaRepository;
  private final KernbezwaarRepository kernbezwaarRepository;
  private final KernbezwaarAntwoordRepository antwoordRepository;
  private final GeextraheerdBezwaarRepository bezwaarRepository;
  private final ClusteringNotificatie notificatie;
  private final int maxConcurrent;

  public ClusteringTaakService(ClusteringTaakRepository taakRepository,
      ThemaRepository themaRepository,
      KernbezwaarRepository kernbezwaarRepository,
      KernbezwaarAntwoordRepository antwoordRepository,
      GeextraheerdBezwaarRepository bezwaarRepository,
      ClusteringNotificatie notificatie,
      @Value("${bezwaarschriften.clustering.max-concurrent:2}") int maxConcurrent) {
    this.taakRepository = taakRepository;
    this.themaRepository = themaRepository;
    this.kernbezwaarRepository = kernbezwaarRepository;
    this.antwoordRepository = antwoordRepository;
    this.bezwaarRepository = bezwaarRepository;
    this.notificatie = notificatie;
    this.maxConcurrent = maxConcurrent;
  }

  /**
   * Dient een nieuwe clustering-taak in voor een categorie binnen een project.
   * Verwijdert een eventueel bestaande taak en bijbehorend thema.
   *
   * @param projectNaam naam van het project
   * @param categorie naam van de categorie
   * @return DTO van de aangemaakte taak
   */
  @Transactional
  public ClusteringTaakDto indienen(String projectNaam, String categorie) {
    // Verwijder bestaande taak en thema als die er zijn
    taakRepository.findByProjectNaamAndCategorie(projectNaam, categorie)
        .ifPresent(bestaandeTaak -> {
          taakRepository.delete(bestaandeTaak);
          themaRepository.deleteByProjectNaamAndNaam(projectNaam, categorie);
        });

    // Maak nieuwe taak aan
    var taak = new ClusteringTaak();
    taak.setProjectNaam(projectNaam);
    taak.setCategorie(categorie);
    taak.setStatus(ClusteringTaakStatus.WACHTEND);
    taak.setAangemaaktOp(Instant.now());
    taak = taakRepository.save(taak);

    int aantalBezwaren = bezwaarRepository.countByProjectNaamAndCategorie(
        projectNaam, categorie);
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

      int aantalBezwaren = bezwaarRepository.countByProjectNaamAndCategorie(
          taak.getProjectNaam(), taak.getCategorie());
      notificatie.clusteringTaakGewijzigd(
          ClusteringTaakDto.van(taak, aantalBezwaren, null));
      LOGGER.info("Clustering-taak {} opgepakt: project='{}', categorie='{}'",
          taak.getId(), taak.getProjectNaam(), taak.getCategorie());
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

    int aantalBezwaren = bezwaarRepository.countByProjectNaamAndCategorie(
        taak.getProjectNaam(), taak.getCategorie());
    Integer aantalKernbezwaren = telKernbezwaren(
        taak.getProjectNaam(), taak.getCategorie());
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

    int aantalBezwaren = bezwaarRepository.countByProjectNaamAndCategorie(
        taak.getProjectNaam(), taak.getCategorie());
    var dto = ClusteringTaakDto.van(taak, aantalBezwaren, null);
    notificatie.clusteringTaakGewijzigd(dto);
  }

  /**
   * Controleert of een taak geannuleerd is.
   *
   * @param taakId ID van de taak
   * @return true als de taak status GEANNULEERD heeft
   */
  public boolean isGeannuleerd(Long taakId) {
    return taakRepository.findById(taakId)
        .map(taak -> taak.getStatus() == ClusteringTaakStatus.GEANNULEERD)
        .orElse(false);
  }

  /**
   * Annuleert een taak als die status WACHTEND of BEZIG heeft.
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

    taak.setStatus(ClusteringTaakStatus.GEANNULEERD);
    taakRepository.save(taak);

    int aantalBezwaren = bezwaarRepository.countByProjectNaamAndCategorie(
        taak.getProjectNaam(), taak.getCategorie());
    var dto = ClusteringTaakDto.van(taak, aantalBezwaren, null);
    notificatie.clusteringTaakGewijzigd(dto);
    return true;
  }

  /**
   * Geeft alle clustering-taken voor een project met bijbehorende counts.
   *
   * @param projectNaam naam van het project
   * @return lijst van DTOs
   */
  public List<ClusteringTaakDto> geefTaken(String projectNaam) {
    return taakRepository.findByProjectNaam(projectNaam).stream()
        .map(taak -> {
          int aantalBezwaren = bezwaarRepository.countByProjectNaamAndCategorie(
              projectNaam, taak.getCategorie());
          Integer aantalKernbezwaren = taak.getStatus() == ClusteringTaakStatus.KLAAR
              ? telKernbezwaren(projectNaam, taak.getCategorie())
              : null;
          return ClusteringTaakDto.van(taak, aantalBezwaren, aantalKernbezwaren);
        })
        .toList();
  }

  /**
   * Verwijdert clusteringresultaten voor een categorie. Als er antwoorden aan
   * kernbezwaren gekoppeld zijn en bevestiging niet gegeven is, wordt eerst om
   * bevestiging gevraagd.
   *
   * @param projectNaam naam van het project
   * @param categorie naam van de categorie
   * @param bevestigd true als de gebruiker bevestigd heeft dat antwoorden verloren mogen gaan
   * @return resultaat met verwijderstatus en eventueel bevestigingsverzoek
   */
  @Transactional
  public VerwijderResultaat verwijderClustering(String projectNaam, String categorie,
      boolean bevestigd) {
    var themaOpt = themaRepository.findByProjectNaamAndNaam(projectNaam, categorie);
    if (themaOpt.isEmpty()) {
      // Verwijder eventueel een taak zonder thema
      taakRepository.findByProjectNaamAndCategorie(projectNaam, categorie)
          .ifPresent(taakRepository::delete);
      return VerwijderResultaat.succesvolVerwijderd();
    }

    var thema = themaOpt.get();
    var kernbezwaren = kernbezwaarRepository.findByThemaId(thema.getId());
    var kernIds = kernbezwaren.stream().map(KernbezwaarEntiteit::getId).toList();

    // Controleer op gekoppelde antwoorden
    if (!kernIds.isEmpty() && !bevestigd) {
      long aantalAntwoorden = antwoordRepository.countByKernbezwaarIdIn(kernIds);
      if (aantalAntwoorden > 0) {
        return VerwijderResultaat.bevestigingVereist(aantalAntwoorden);
      }
    }

    // Verwijder thema (cascade ruimt kernbezwaren, referenties en antwoorden op)
    themaRepository.deleteByProjectNaamAndNaam(projectNaam, categorie);

    // Verwijder bijbehorende taak
    taakRepository.findByProjectNaamAndCategorie(projectNaam, categorie)
        .ifPresent(taakRepository::delete);

    return VerwijderResultaat.succesvolVerwijderd();
  }

  /**
   * Geeft een overzicht van alle categorien met hun clustering-status.
   * Categorien zonder taak krijgen status "todo".
   *
   * @param projectNaam naam van het project
   * @return lijst van status-items per categorie
   */
  public List<CategorieStatus> geefCategorieOverzicht(String projectNaam) {
    var alleCategorien = bezwaarRepository
        .findDistinctCategorienByProjectNaam(projectNaam);
    var taken = geefTaken(projectNaam);
    var taakPerCategorie = taken.stream()
        .collect(Collectors.toMap(ClusteringTaakDto::categorie, dto -> dto));

    var items = new ArrayList<CategorieStatus>();
    for (var categorie : alleCategorien) {
      var taak = taakPerCategorie.get(categorie);
      if (taak != null) {
        items.add(new CategorieStatus(
            taak.categorie(), taak.status(), taak.id(),
            taak.aantalBezwaren(), taak.aantalKernbezwaren(),
            taak.foutmelding()));
      } else {
        int aantalBezwaren = bezwaarRepository
            .countByProjectNaamAndCategorie(projectNaam, categorie);
        items.add(new CategorieStatus(
            categorie, "todo", null,
            aantalBezwaren, null, null));
      }
    }
    return items;
  }

  /**
   * Dient clustering-taken in voor alle categorien die nog niet actief zijn
   * (niet klaar, bezig of wachtend).
   *
   * @param projectNaam naam van het project
   * @return lijst van ingediende DTOs
   */
  public List<ClusteringTaakDto> indienenAlleNietActieve(String projectNaam) {
    var alleCategorien = bezwaarRepository
        .findDistinctCategorienByProjectNaam(projectNaam);
    var bestaandeTaken = geefTaken(projectNaam);

    var actieveStatussen = Set.of("klaar", "bezig", "wachtend");
    var actieveCategorieen = bestaandeTaken.stream()
        .filter(t -> actieveStatussen.contains(t.status()))
        .map(ClusteringTaakDto::categorie)
        .collect(Collectors.toSet());

    var ingediend = new ArrayList<ClusteringTaakDto>();
    for (var categorie : alleCategorien) {
      if (!actieveCategorieen.contains(categorie)) {
        ingediend.add(indienen(projectNaam, categorie));
      }
    }
    return ingediend;
  }

  /** Status per categorie in het overzicht. */
  public record CategorieStatus(
      String categorie,
      String status,
      Long taakId,
      int aantalBezwaren,
      Integer aantalKernbezwaren,
      String foutmelding) {}

  private Integer telKernbezwaren(String projectNaam, String categorie) {
    return themaRepository.findByProjectNaamAndNaam(projectNaam, categorie)
        .map(thema -> kernbezwaarRepository.countByThemaId(thema.getId()))
        .orElse(null);
  }
}
