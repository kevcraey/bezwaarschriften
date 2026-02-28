package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
   * Download een bezwaarbestand.
   *
   * @param naam Projectnaam
   * @param bestandsnaam Naam van het te downloaden bestand
   * @return Het bestand als bijlage
   */
  @GetMapping("/{naam}/bezwaren/{bestandsnaam}/download")
  public ResponseEntity<Resource> downloadBestand(
      @PathVariable String naam,
      @PathVariable String bestandsnaam) throws IOException {
    Path pad = projectService.geefBestandsPad(naam, bestandsnaam);
    Resource resource = new UrlResource(pad.toUri());

    String contentType = Files.probeContentType(pad);
    if (contentType == null) {
      contentType = "application/octet-stream";
    }

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + bestandsnaam + "\"")
        .body(resource);
  }

  private static String statusNaarString(BezwaarBestandStatus status) {
    return switch (status) {
      case TODO -> "todo";
      case WACHTEND -> "wachtend";
      case BEZIG -> "bezig";
      case EXTRACTIE_KLAAR -> "extractie-klaar";
      case FOUT -> "fout";
      case NIET_ONDERSTEUND -> "niet ondersteund";
    };
  }

  /** Response DTO voor projectenlijst. */
  record ProjectenResponse(List<String> projecten) {}

  /** Response DTO voor bezwarenlijst. */
  record BezwarenResponse(List<BezwaarBestandDto> bezwaren) {

    static BezwarenResponse van(List<BezwaarBestand> bezwaren) {
      return new BezwarenResponse(bezwaren.stream()
          .map(b -> new BezwaarBestandDto(b.bestandsnaam(), statusNaarString(b.status()),
              b.aantalWoorden(), b.aantalBezwaren()))
          .toList());
    }
  }

  /** DTO voor een enkel bezwaarbestand in de response. */
  record BezwaarBestandDto(String bestandsnaam, String status, Integer aantalWoorden,
      Integer aantalBezwaren) {}
}
