package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller voor het beheren van clustering-taken per categorie.
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
   * Geeft alle categorien met hun clustering-status.
   * Categorien zonder een taak krijgen status "todo".
   */
  @GetMapping("/{naam}/clustering-taken")
  public ResponseEntity<CategorieOverzichtResponse> geefOverzicht(
      @PathVariable String naam) {
    var items = taakService.geefCategorieOverzicht(naam);
    return ResponseEntity.ok(new CategorieOverzichtResponse(items));
  }

  /**
   * Start clustering voor een enkele categorie.
   */
  @PostMapping("/{naam}/clustering-taken/{categorie}")
  public ResponseEntity<ClusteringTaakDto> startCategorie(
      @PathVariable String naam,
      @PathVariable String categorie) {
    var dto = taakService.indienen(naam, categorie);
    return ResponseEntity.accepted().body(dto);
  }

  /**
   * Start clustering voor alle categorien die nog niet klaar, bezig of wachtend zijn.
   */
  @PostMapping("/{naam}/clustering-taken")
  public ResponseEntity<List<ClusteringTaakDto>> startAlleCategorieen(
      @PathVariable String naam) {
    var ingediend = taakService.indienenAlleNietActieve(naam);
    return ResponseEntity.accepted().body(ingediend);
  }

  /**
   * Annuleert een actieve taak of verwijdert een voltooide clustering.
   * Bij gekoppelde antwoorden en ontbrekende bevestiging wordt 409 geretourneerd.
   */
  @DeleteMapping("/{naam}/clustering-taken/{categorie}")
  public ResponseEntity<?> verwijderCategorie(
      @PathVariable String naam,
      @PathVariable String categorie,
      @RequestParam(defaultValue = "false") boolean bevestigd) {
    // Probeer eerst de actieve taak te annuleren
    var taakOpt = taakService.geefTaken(naam).stream()
        .filter(t -> t.categorie().equals(categorie))
        .findFirst();

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
    var resultaat = taakService.verwijderClustering(naam, categorie, bevestigd);
    if (resultaat.bevestigingNodig()) {
      return ResponseEntity.status(409).body(
          new BevestigingResponse(resultaat.aantalAntwoorden()));
    }

    return ResponseEntity.ok().build();
  }

  /** Response DTO voor het categorieoverzicht. */
  record CategorieOverzichtResponse(
      List<ClusteringTaakService.CategorieStatus> categorieen) {}

  /** Response bij 409 Conflict: bevestiging vereist om antwoorden te verwijderen. */
  record BevestigingResponse(long aantalAntwoorden) {}
}
