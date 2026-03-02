package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.HashMap;
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
  private final ProjectService projectService;
  private final ExtractiePassageRepository passageRepository;
  private final GeextraheerdBezwaarRepository bezwaarRepository;
  private final int maxConcurrent;
  private final int maxPogingen;

  /**
   * Maakt een nieuwe ExtractieTaakService aan.
   *
   * @param repository repository voor extractie-taken
   * @param notificatie notificatie-interface voor statuswijzigingen
   * @param projectService service voor projecten en bezwaarbestanden
   * @param passageRepository repository voor extractie-passages
   * @param bezwaarRepository repository voor geextraheerde bezwaren
   * @param maxConcurrent maximum aantal gelijktijdig verwerkbare taken
   * @param maxPogingen maximum aantal pogingen per taak
   */
  public ExtractieTaakService(
      ExtractieTaakRepository repository,
      ExtractieNotificatie notificatie,
      ProjectService projectService,
      ExtractiePassageRepository passageRepository,
      GeextraheerdBezwaarRepository bezwaarRepository,
      @Value("${bezwaarschriften.extractie.max-concurrent:3}") int maxConcurrent,
      @Value("${bezwaarschriften.extractie.max-pogingen:3}") int maxPogingen) {
    this.repository = repository;
    this.notificatie = notificatie;
    this.projectService = projectService;
    this.passageRepository = passageRepository;
    this.bezwaarRepository = bezwaarRepository;
    this.maxConcurrent = maxConcurrent;
    this.maxPogingen = maxPogingen;
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
          taak.setMaxPogingen(maxPogingen);
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
   * Markeert een taak als succesvol afgerond (terugwaarts-compatibele variant zonder details).
   *
   * @param taakId id van de taak
   * @param aantalWoorden aantal woorden in het verwerkte bestand
   * @param aantalBezwaren aantal geextraheerde bezwaren
   */
  @Transactional
  public void markeerKlaar(Long taakId, int aantalWoorden, int aantalBezwaren) {
    markeerKlaar(taakId, new ExtractieResultaat(aantalWoorden, aantalBezwaren));
  }

  /**
   * Markeert een taak als succesvol afgerond en slaat passages en bezwaren op.
   *
   * @param taakId id van de taak
   * @param resultaat het volledige extractie-resultaat met passages en bezwaren
   */
  @Transactional
  public void markeerKlaar(Long taakId, ExtractieResultaat resultaat) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    taak.setStatus(ExtractieTaakStatus.KLAAR);
    taak.setAantalWoorden(resultaat.aantalWoorden());
    taak.setAantalBezwaren(resultaat.aantalBezwaren());
    taak.setAfgerondOp(Instant.now());
    repository.save(taak);

    for (var passage : resultaat.passages()) {
      var entiteit = new ExtractiePassageEntiteit();
      entiteit.setTaakId(taakId);
      entiteit.setPassageNr(passage.id());
      entiteit.setTekst(passage.tekst());
      passageRepository.save(entiteit);
    }

    for (var bezwaar : resultaat.bezwaren()) {
      var entiteit = new GeextraheerdBezwaarEntiteit();
      entiteit.setTaakId(taakId);
      entiteit.setPassageNr(bezwaar.passageId());
      entiteit.setSamenvatting(bezwaar.samenvatting());
      entiteit.setCategorie(bezwaar.categorie());
      bezwaarRepository.save(entiteit);
    }

    notificatie.taakGewijzigd(ExtractieTaakDto.van(taak));
    LOGGER.info("Taak {} afgerond: {} woorden, {} bezwaren, {} passages opgeslagen",
        taakId, resultaat.aantalWoorden(), resultaat.aantalBezwaren(),
        resultaat.passages().size());
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

  /**
   * Verwerkt onafgeronde items voor een project: herstelt gefaalde taken en maakt
   * nieuwe taken aan voor documenten die nog niet verwerkt zijn.
   *
   * <p>Gefaalde taken worden teruggezet naar WACHTEND met 1 extra poging.
   * Documenten met status TODO krijgen een nieuwe extractie-taak.
   *
   * @param projectNaam naam van het project
   * @return het totaal aantal herstartte en nieuwe taken
   */
  @Transactional
  public int verwerkOnafgeronde(String projectNaam) {
    // Herstel gefaalde taken
    var gefaaldeTaken = repository.findByProjectNaamAndStatus(projectNaam,
        ExtractieTaakStatus.FOUT);
    for (var taak : gefaaldeTaken) {
      taak.setMaxPogingen(taak.getMaxPogingen() + 1);
      taak.setStatus(ExtractieTaakStatus.WACHTEND);
      taak.setFoutmelding(null);
      taak.setVerwerkingGestartOp(null);
      taak.setAfgerondOp(null);
      repository.save(taak);
      notificatie.taakGewijzigd(ExtractieTaakDto.van(taak));
    }

    // Maak taken aan voor TODO-documenten
    var todoDocumenten = projectService.geefBezwaren(projectNaam).stream()
        .filter(b -> b.status() == BezwaarBestandStatus.TODO)
        .toList();
    for (var doc : todoDocumenten) {
      var taak = new ExtractieTaak();
      taak.setProjectNaam(projectNaam);
      taak.setBestandsnaam(doc.bestandsnaam());
      taak.setStatus(ExtractieTaakStatus.WACHTEND);
      taak.setAantalPogingen(0);
      taak.setMaxPogingen(maxPogingen);
      taak.setAangemaaktOp(Instant.now());
      var opgeslagen = repository.save(taak);
      notificatie.taakGewijzigd(ExtractieTaakDto.van(opgeslagen));
      LOGGER.info("Nieuwe extractie-taak voor TODO-document: project='{}', bestand='{}'",
          projectNaam, doc.bestandsnaam());
    }

    int totaal = gefaaldeTaken.size() + todoDocumenten.size();
    LOGGER.info("Verwerkt {} onafgeronde items voor project '{}' ({} fout, {} todo)",
        totaal, projectNaam, gefaaldeTaken.size(), todoDocumenten.size());
    return totaal;
  }

  /**
   * Verwijdert een extractie-taak uit de database.
   *
   * @param projectNaam naam van het project (voor validatie)
   * @param taakId id van de te verwijderen taak
   * @throws IllegalArgumentException als de taak niet bestaat of niet bij het project hoort
   */
  @Transactional
  public void verwijderTaak(String projectNaam, Long taakId) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    if (!taak.getProjectNaam().equals(projectNaam)) {
      throw new IllegalArgumentException(
          "Taak " + taakId + " behoort niet tot project: " + projectNaam);
    }
    repository.delete(taak);
    LOGGER.info("Taak {} verwijderd uit project '{}'", taakId, projectNaam);
  }

  /**
   * Geeft de extractie-details voor een bestand binnen een project.
   *
   * <p>Zoekt de meest recente afgeronde taak voor het bestand en combineert
   * de geextraheerde bezwaren met hun bijbehorende passages.
   *
   * @param projectNaam naam van het project
   * @param bestandsnaam naam van het bestand
   * @return extractie-details, of null als geen afgeronde taak bestaat
   */
  public ExtractieDetailDto geefExtractieDetails(String projectNaam, String bestandsnaam) {
    var taak = repository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(projectNaam, bestandsnaam)
        .orElse(null);
    if (taak == null || taak.getStatus() != ExtractieTaakStatus.KLAAR) {
      return null;
    }

    var passages = passageRepository.findByTaakId(taak.getId());
    var bezwaren = bezwaarRepository.findByTaakId(taak.getId());

    var passageMap = new HashMap<Integer, String>();
    for (var p : passages) {
      passageMap.put(p.getPassageNr(), p.getTekst());
    }

    var details = bezwaren.stream()
        .map(b -> new ExtractieDetailDto.BezwaarDetail(
            b.getSamenvatting(),
            passageMap.getOrDefault(b.getPassageNr(), ""),
            b.isPassageGevonden()))
        .toList();

    return new ExtractieDetailDto(bestandsnaam, details.size(), details);
  }
}
