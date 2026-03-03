package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
  private final GeextraheerdBezwaarRepository bezwaarRepository;

  public ClusteringTaakController(ClusteringTaakService taakService,
      ClusteringWorker clusteringWorker,
      GeextraheerdBezwaarRepository bezwaarRepository) {
    this.taakService = taakService;
    this.clusteringWorker = clusteringWorker;
    this.bezwaarRepository = bezwaarRepository;
  }

  /**
   * Geeft alle categorien met hun clustering-status.
   * Categorien zonder een taak krijgen status "todo".
   */
  @GetMapping("/{naam}/clustering-taken")
  public ResponseEntity<CategorieOverzichtResponse> geefOverzicht(
      @PathVariable String naam) {
    var alleCategorien = bezwaarRepository.findDistinctCategorienByProjectNaam(naam);
    var taken = taakService.geefTaken(naam);

    var taakPerCategorie = taken.stream()
        .collect(Collectors.toMap(ClusteringTaakDto::categorie, dto -> dto));

    var items = new ArrayList<CategorieStatusDto>();
    for (var categorie : alleCategorien) {
      var taak = taakPerCategorie.get(categorie);
      if (taak != null) {
        items.add(new CategorieStatusDto(
            taak.categorie(), taak.status(), taak.id(),
            taak.aantalBezwaren(), taak.aantalKernbezwaren(),
            taak.foutmelding()));
      } else {
        int aantalBezwaren = bezwaarRepository.countByProjectNaamAndCategorie(
            naam, categorie);
        items.add(new CategorieStatusDto(
            categorie, "todo", null,
            aantalBezwaren, null, null));
      }
    }

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
    var alleCategorien = bezwaarRepository.findDistinctCategorienByProjectNaam(naam);
    var bestaandeTaken = taakService.geefTaken(naam);

    var actieveStatussen = Set.of("klaar", "bezig", "wachtend");
    var actieveCategorieen = bestaandeTaken.stream()
        .filter(t -> actieveStatussen.contains(t.status()))
        .map(ClusteringTaakDto::categorie)
        .collect(Collectors.toSet());

    var ingediend = new ArrayList<ClusteringTaakDto>();
    for (var categorie : alleCategorien) {
      if (!actieveCategorieen.contains(categorie)) {
        ingediend.add(taakService.indienen(naam, categorie));
      }
    }

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
  record CategorieOverzichtResponse(List<CategorieStatusDto> categorieen) {}

  /** Status per categorie in het overzicht. */
  record CategorieStatusDto(
      String categorie,
      String status,
      Long taakId,
      int aantalBezwaren,
      Integer aantalKernbezwaren,
      String foutmelding) {}

  /** Response bij 409 Conflict: bevestiging vereist om antwoorden te verwijderen. */
  record BevestigingResponse(long aantalAntwoorden) {}
}
