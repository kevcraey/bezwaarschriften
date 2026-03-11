package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    var overzichten = projectService.geefProjectenMetAantalDocumenten();
    var dtos = overzichten.stream()
        .map(o -> new ProjectDto(o.naam(), o.aantalDocumenten()))
        .toList();
    return ResponseEntity.ok(new ProjectenResponse(dtos));
  }

  /**
   * Maakt een nieuw project aan.
   *
   * @param request Het verzoek met de projectnaam
   * @return 201 Created
   */
  @PostMapping
  public ResponseEntity<Void> maakProjectAan(@RequestBody ProjectAanmaakRequest request) {
    projectService.maakProjectAan(request.naam());
    return ResponseEntity.status(201).build();
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
   * Verwijdert een project en alle bijhorende data.
   *
   * @param naam Projectnaam
   * @return 204 No Content bij succes, 404 als project niet gevonden
   */
  @DeleteMapping("/{naam}")
  public ResponseEntity<Void> verwijderProject(@PathVariable String naam) {
    boolean verwijderd = projectService.verwijderProject(naam);
    return verwijderd ? ResponseEntity.noContent().build()
        : ResponseEntity.notFound().build();
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

  /**
   * Uploadt bezwaarbestanden naar een project.
   *
   * @param naam Projectnaam
   * @param bestanden Ge-uploade bestanden
   * @return Upload-resultaat met geslaagde en gefaalde bestanden
   */
  @PostMapping("/{naam}/bezwaren/upload")
  public ResponseEntity<UploadResponse> uploadBezwaren(
      @PathVariable String naam,
      @RequestParam("bestanden") MultipartFile[] bestanden) {
    if (bestanden.length > 1000) {
      return ResponseEntity.badRequest().body(
          new UploadResponse(List.of(),
              List.of(new UploadFoutDto("", "Maximum 1000 bestanden per upload"))));
    }

    var bestandenMap = new LinkedHashMap<String, byte[]>();
    var fouten = new ArrayList<UploadFoutDto>();

    for (var bestand : bestanden) {
      try {
        bestandenMap.put(bestand.getOriginalFilename(), bestand.getBytes());
      } catch (IOException e) {
        fouten.add(new UploadFoutDto(bestand.getOriginalFilename(),
            "Kon bestand niet lezen"));
      }
    }

    var resultaat = projectService.uploadBezwaren(naam, bestandenMap);
    resultaat.fouten().forEach(f ->
        fouten.add(new UploadFoutDto(f.bestandsnaam(), f.reden())));

    return ResponseEntity.ok(new UploadResponse(resultaat.geupload(), fouten));
  }

  /**
   * Verwijdert meerdere bezwaarbestanden in bulk.
   *
   * @param naam Projectnaam
   * @param request Lijst van te verwijderen bestandsnamen
   * @return Aantal verwijderde bestanden
   */
  @DeleteMapping("/{naam}/bezwaren")
  public ResponseEntity<?> verwijderBezwaren(
      @PathVariable String naam,
      @RequestBody VerwijderBezwarenRequest request) {
    if (request.bestandsnamen() == null || request.bestandsnamen().isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    int aantalVerwijderd = projectService.verwijderBezwaren(naam, request.bestandsnamen());
    return ResponseEntity.ok(new VerwijderBezwarenResponse(aantalVerwijderd));
  }

  /**
   * Verwijdert een bezwaarbestand en bijhorende extractie-taken.
   *
   * @param naam Projectnaam
   * @param bestandsnaam Bestandsnaam
   * @return 204 No Content bij succes, 404 als bestand niet gevonden
   */
  @DeleteMapping("/{naam}/bezwaren/{bestandsnaam}")
  public ResponseEntity<Void> verwijderBezwaar(
      @PathVariable String naam,
      @PathVariable String bestandsnaam) {
    boolean verwijderd = projectService.verwijderBezwaar(naam, bestandsnaam);
    return verwijderd ? ResponseEntity.noContent().build()
        : ResponseEntity.notFound().build();
  }

  private static String statusNaarString(BezwaarBestandStatus status) {
    return switch (status) {
      case TODO -> "todo";
      case TEKST_EXTRACTIE_WACHTEND -> "tekst-extractie-wachtend";
      case TEKST_EXTRACTIE_BEZIG -> "tekst-extractie-bezig";
      case TEKST_EXTRACTIE_KLAAR -> "tekst-extractie-klaar";
      case TEKST_EXTRACTIE_MISLUKT -> "tekst-extractie-mislukt";
      case TEKST_EXTRACTIE_OCR_NIET_BESCHIKBAAR -> "tekst-extractie-ocr-niet-beschikbaar";
      case BEZWAAR_EXTRACTIE_WACHTEND -> "bezwaar-extractie-wachtend";
      case BEZWAAR_EXTRACTIE_BEZIG -> "bezwaar-extractie-bezig";
      case BEZWAAR_EXTRACTIE_KLAAR -> "bezwaar-extractie-klaar";
      case BEZWAAR_EXTRACTIE_FOUT -> "bezwaar-extractie-fout";
      case NIET_ONDERSTEUND -> "niet ondersteund";
    };
  }

  /** DTO voor een enkel project in de response. */
  record ProjectDto(String naam, int aantalDocumenten) {}

  /** Response DTO voor projectenlijst. */
  record ProjectenResponse(List<ProjectDto> projecten) {}

  /** Request DTO voor project aanmaken. */
  record ProjectAanmaakRequest(String naam) {}

  /** Response DTO voor bezwarenlijst. */
  record BezwarenResponse(List<BezwaarBestandDto> bezwaren) {

    static BezwarenResponse van(List<BezwaarBestand> bezwaren) {
      return new BezwarenResponse(bezwaren.stream()
          .map(b -> new BezwaarBestandDto(b.bestandsnaam(), statusNaarString(b.status()),
              b.aantalWoorden(), b.aantalBezwaren(), b.heeftPassagesDieNietInTekstVoorkomen(),
              b.extractieMethode(),
              b.tekstExtractieAangemaaktOp(), b.tekstExtractieGestartOp(),
              b.tekstExtractieTaakId(), b.tekstExtractieFoutmelding()))
          .toList());
    }
  }

  /** DTO voor een enkel bezwaarbestand in de response. */
  record BezwaarBestandDto(String bestandsnaam, String status, Integer aantalWoorden,
      Integer aantalBezwaren, boolean heeftPassagesDieNietInTekstVoorkomen,
      String extractieMethode,
      String tekstExtractieAangemaaktOp, String tekstExtractieGestartOp,
      Long tekstExtractieTaakId, String tekstExtractieFoutmelding) {}

  /** Response DTO voor upload-resultaat. */
  record UploadResponse(List<String> geupload, List<UploadFoutDto> fouten) {}

  /** DTO voor een uploadfout per bestand. */
  record UploadFoutDto(String bestandsnaam, String reden) {}

  /** Request DTO voor bulk verwijdering van bezwaarbestanden. */
  record VerwijderBezwarenRequest(List<String> bestandsnamen) {}

  /** Response DTO voor bulk verwijdering van bezwaarbestanden. */
  record VerwijderBezwarenResponse(int aantalVerwijderd) {}
}
