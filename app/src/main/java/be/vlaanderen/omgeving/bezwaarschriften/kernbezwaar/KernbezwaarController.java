package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller voor kernbezwaar-groepering.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class KernbezwaarController {

  private final KernbezwaarService kernbezwaarService;

  public KernbezwaarController(KernbezwaarService kernbezwaarService) {
    this.kernbezwaarService = kernbezwaarService;
  }

  /**
   * Triggert de groepering van individuele bezwaren tot thema's en kernbezwaren.
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
   */
  @PutMapping("/{naam}/kernbezwaren/{id}/antwoord")
  public ResponseEntity<Void> slaAntwoordOp(
      @PathVariable String naam,
      @PathVariable Long id,
      @RequestBody AntwoordRequest request) {
    kernbezwaarService.slaAntwoordOp(id, request.inhoud());
    return ResponseEntity.ok().build();
  }

  /** Response DTO met thema's en kernbezwaren. */
  record ThemasResponse(List<Thema> themas) {}

  /** Request DTO voor het opslaan van een antwoord. */
  record AntwoordRequest(String inhoud) {}
}
