package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import org.springframework.http.ResponseEntity;
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

  /**
   * Maakt een nieuwe ExtractieController aan.
   *
   * @param extractieTaakService service voor extractie-takenbeheer
   */
  public ExtractieController(ExtractieTaakService extractieTaakService) {
    this.extractieTaakService = extractieTaakService;
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
   * Herplant alle gefaalde extractie-taken voor het opgegeven project.
   *
   * @param naam projectnaam
   * @return het aantal opnieuw ingeplande taken
   */
  @PostMapping("/{naam}/extracties/retry")
  public ResponseEntity<RetryResponse> retry(@PathVariable String naam) {
    int aantal = extractieTaakService.herplanGefaaldeTaken(naam);
    return ResponseEntity.ok(new RetryResponse(aantal));
  }

  /** Response DTO voor het retry-endpoint. */
  record RetryResponse(int aantalOpnieuwIngepland) {}

  /** Request DTO voor het indienen van extractie-taken. */
  record ExtractiesRequest(List<String> bestandsnamen) {}

  /** Response DTO met een lijst van extractie-taken. */
  record ExtractieTakenResponse(List<ExtractieTaakDto> taken) {}
}
