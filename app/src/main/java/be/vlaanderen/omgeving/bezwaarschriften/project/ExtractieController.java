package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller voor het indienen en opvragen van extractie-taken per project.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ExtractieController {

  private final ExtractieTaakService extractieTaakService;
  private final ExtractieWorker extractieWorker;

  /**
   * Maakt een nieuwe ExtractieController aan.
   *
   * @param extractieTaakService service voor extractie-takenbeheer
   * @param extractieWorker worker voor het annuleren van lopende taken
   */
  public ExtractieController(ExtractieTaakService extractieTaakService,
      ExtractieWorker extractieWorker) {
    this.extractieTaakService = extractieTaakService;
    this.extractieWorker = extractieWorker;
  }

  /**
   * Dient extractie-taken in voor de opgegeven bestanden.
   *
   * @param naam projectnaam
   * @param request verzoek met bestandsnamen
   * @return aangemaakte extractie-taken
   */
  @PostMapping("/{naam}/extracties")
  public ResponseEntity<ExtractieTakenResponse> indienen(
      @PathVariable String naam, @RequestBody ExtractiesRequest request) {
    var taken = extractieTaakService.indienen(naam, request.bestandsnamen());
    return ResponseEntity.ok(new ExtractieTakenResponse(taken));
  }

  /**
   * Geeft alle extractie-taken voor een project.
   *
   * @param naam projectnaam
   * @return extractie-taken van het project
   */
  @GetMapping("/{naam}/extracties")
  public ResponseEntity<ExtractieTakenResponse> geefTaken(@PathVariable String naam) {
    var taken = extractieTaakService.geefTaken(naam);
    return ResponseEntity.ok(new ExtractieTakenResponse(taken));
  }

  /**
   * Verwerkt alle onafgeronde extractie-taken voor het opgegeven project.
   * Herstart gefaalde taken en plant nieuwe taken in voor TODO-documenten.
   *
   * @param naam projectnaam
   * @return het aantal ingeplande taken
   */
  @PostMapping("/{naam}/extracties/verwerken")
  public ResponseEntity<VerwerkenResponse> verwerken(@PathVariable String naam) {
    int aantal = extractieTaakService.verwerkOnafgeronde(naam);
    return ResponseEntity.ok(new VerwerkenResponse(aantal));
  }

  /**
   * Annuleert een extractie-taak. Verwijdert de taak uit de database
   * en annuleert eventuele lopende verwerking.
   *
   * @param naam projectnaam
   * @param taakId id van de te annuleren taak
   * @return 204 No Content bij succes, 404 als taak niet gevonden
   */
  @DeleteMapping("/{naam}/extracties/{taakId}")
  public ResponseEntity<Void> annuleer(@PathVariable String naam, @PathVariable Long taakId) {
    try {
      extractieTaakService.verwijderTaak(naam, taakId);
      extractieWorker.annuleerTaak(taakId);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    }
  }

  /** Response DTO voor het verwerken-endpoint. */
  record VerwerkenResponse(int aantalIngepland) {}

  /** Request DTO voor het indienen van extractie-taken. */
  record ExtractiesRequest(List<String> bestandsnamen) {}

  /** Response DTO met een lijst van extractie-taken. */
  record ExtractieTakenResponse(List<ExtractieTaakDto> taken) {}
}
