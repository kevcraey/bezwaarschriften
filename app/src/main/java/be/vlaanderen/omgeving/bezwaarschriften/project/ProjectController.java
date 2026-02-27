package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller voor project- en bezwaarbeheer.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

  private final ProjectService projectService;

  /**
   * Maakt een nieuwe ProjectController aan.
   *
   * @param projectService Service voor projectbeheer
   */
  public ProjectController(ProjectService projectService) {
    this.projectService = projectService;
  }

  /**
   * Geeft de lijst van beschikbare projecten terug.
   *
   * @return Projectenlijst
   */
  @GetMapping
  public ResponseEntity<ProjectenResponse> geefProjecten() {
    var projecten = projectService.geefProjecten();
    return ResponseEntity.ok(new ProjectenResponse(projecten));
  }

  /**
   * Geeft de bezwaarbestanden van een project terug met hun status.
   *
   * @param naam Projectnaam
   * @return Bezwarenlijst met statussen
   */
  @GetMapping("/{naam}/bezwaren")
  public ResponseEntity<BezwarenResponse> geefBezwaren(@PathVariable String naam) {
    var bezwaren = projectService.geefBezwaren(naam);
    return ResponseEntity.ok(BezwarenResponse.van(bezwaren));
  }

  /**
   * Start de batchverwerking voor alle openstaande bezwaren van een project.
   *
   * @param naam Projectnaam
   * @return Bijgewerkte bezwarenlijst met statussen
   */
  @PostMapping("/{naam}/verwerk")
  public ResponseEntity<BezwarenResponse> verwerk(@PathVariable String naam) {
    var bezwaren = projectService.verwerk(naam);
    return ResponseEntity.ok(BezwarenResponse.van(bezwaren));
  }

  /** Response DTO voor projectenlijst. */
  record ProjectenResponse(List<String> projecten) {}

  /** Response DTO voor bezwarenlijst. */
  record BezwarenResponse(List<BezwaarBestandDto> bezwaren) {

    static BezwarenResponse van(List<BezwaarBestand> bezwaren) {
      return new BezwarenResponse(bezwaren.stream()
          .map(b -> new BezwaarBestandDto(b.bestandsnaam(), statusNaarString(b.status())))
          .toList());
    }

    private static String statusNaarString(BezwaarBestandStatus status) {
      return switch (status) {
        case TODO -> "todo";
        case EXTRACTIE_KLAAR -> "extractie-klaar";
        case FOUT -> "fout";
        case NIET_ONDERSTEUND -> "niet ondersteund";
      };
    }
  }

  /** DTO voor een enkel bezwaarbestand in de response. */
  record BezwaarBestandDto(String bestandsnaam, String status) {}
}
