package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller voor kernbezwaar-clustering.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class KernbezwaarController {

  private final KernbezwaarService kernbezwaarService;
  private final KernbezwaarReferentieRepository referentieRepository;
  private final ConsolidatieTaakService consolidatieTaakService;

  public KernbezwaarController(KernbezwaarService kernbezwaarService,
      KernbezwaarReferentieRepository referentieRepository,
      ConsolidatieTaakService consolidatieTaakService) {
    this.kernbezwaarService = kernbezwaarService;
    this.referentieRepository = referentieRepository;
    this.consolidatieTaakService = consolidatieTaakService;
  }

  /**
   * Triggert de clustering van individuele bezwaren tot thema's en kernbezwaren.
   */
  @PostMapping("/{naam}/kernbezwaren/groepeer")
  public ResponseEntity<ThemasResponse> groepeer(@PathVariable String naam) {
    var themas = kernbezwaarService.groepeer(naam);
    return ResponseEntity.ok(new ThemasResponse(themas));
  }

  /**
   * Geeft eerder berekende kernbezwaren voor een project.
   */
  @GetMapping("/{naam}/kernbezwaren")
  public ResponseEntity<ThemasResponse> geefKernbezwaren(@PathVariable String naam) {
    return kernbezwaarService.geefKernbezwaren(naam)
        .map(themas -> ResponseEntity.ok(new ThemasResponse(themas)))
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Slaat het antwoord (weerwoord) op voor een specifiek kernbezwaar.
   *
   * <p>Als er al afgeronde consolidatie-taken bestaan voor documenten die verwijzen naar
   * dit kernbezwaar, wordt een 409 Conflict geretourneerd met de getroffen bestandsnamen.
   * Met {@code bevestigd=true} worden die taken verwijderd en het antwoord opgeslagen.
   */
  @PutMapping("/{naam}/kernbezwaren/{id}/antwoord")
  public ResponseEntity<?> slaAntwoordOp(
      @PathVariable String naam,
      @PathVariable Long id,
      @RequestBody AntwoordRequest request,
      @RequestParam(defaultValue = "false") boolean bevestigd) {
    var bestandsnamen = referentieRepository.findBestandsnamenByKernbezwaarId(id);
    var getroffen = consolidatieTaakService.vindKlareBestandsnamen(naam, bestandsnamen);

    if (!getroffen.isEmpty() && !bevestigd) {
      return ResponseEntity.status(409).body(new ConflictResponse(getroffen));
    }
    if (!getroffen.isEmpty()) {
      consolidatieTaakService.verwijderKlareTaken(naam, bestandsnamen);
    }

    kernbezwaarService.slaAntwoordOp(id, request.inhoud());
    return ResponseEntity.ok().build();
  }

  /** Response DTO met thema's en kernbezwaren. */
  record ThemasResponse(List<Thema> themas) {}

  /** Request DTO voor het opslaan van een antwoord. */
  record AntwoordRequest(String inhoud) {}

  /** Response bij 409 Conflict: lijst van getroffen bestandsnamen. */
  record ConflictResponse(List<String> getroffenDocumenten) {}
}
