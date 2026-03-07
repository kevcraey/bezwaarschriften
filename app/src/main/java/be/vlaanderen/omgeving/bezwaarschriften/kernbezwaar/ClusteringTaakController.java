package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller voor het beheren van de clustering-taak per project.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ClusteringTaakController {

  private final ClusteringTaakService taakService;
  private final ClusteringWorker clusteringWorker;

  public ClusteringTaakController(ClusteringTaakService taakService,
      ClusteringWorker clusteringWorker) {
    this.taakService = taakService;
    this.clusteringWorker = clusteringWorker;
  }

  /**
   * Geeft de clustering-taak voor een project.
   */
  @GetMapping("/{naam}/clustering-taken")
  public ResponseEntity<ClusteringTaakDto> geefTaak(@PathVariable String naam) {
    return taakService.geefTaak(naam)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.noContent().build());
  }

  /**
   * Start clustering voor een project.
   */
  @PostMapping("/{naam}/clustering-taken")
  public ResponseEntity<ClusteringTaakDto> startClustering(
      @PathVariable String naam,
      @RequestParam(defaultValue = "true") boolean deduplicatieVoorClustering) {
    var dto = taakService.indienen(naam, deduplicatieVoorClustering);
    return ResponseEntity.accepted().body(dto);
  }

  /**
   * Verwijdert clusteringresultaten voor een project.
   * Annuleert eerst een actieve taak als die er is.
   * Bij gekoppelde antwoorden en ontbrekende bevestiging wordt 409 geretourneerd.
   */
  @DeleteMapping("/{naam}/clustering-taken")
  public ResponseEntity<?> verwijderClustering(
      @PathVariable String naam,
      @RequestParam(defaultValue = "false") boolean bevestigd) {
    // Probeer eerst de actieve taak te annuleren
    var taakOpt = taakService.geefTaak(naam);
    if (taakOpt.isPresent()) {
      var taak = taakOpt.get();
      var status = taak.status();

      if ("wachtend".equals(status) || "bezig".equals(status)) {
        taakService.annuleer(taak.id());
        if ("bezig".equals(status)) {
          clusteringWorker.annuleerTaak(taak.id());
        }
        return ResponseEntity.ok().build();
      }
    }

    // Verwijder voltooide clustering
    var resultaat = taakService.verwijderClustering(naam, bevestigd);
    if (resultaat.bevestigingNodig()) {
      return ResponseEntity.status(409).body(
          new BevestigingResponse(resultaat.aantalAntwoorden()));
    }

    return ResponseEntity.ok().build();
  }

  /** Response bij 409 Conflict: bevestiging vereist om antwoorden te verwijderen. */
  record BevestigingResponse(long aantalAntwoorden) {}
}
