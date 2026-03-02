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

  /**
   * Geeft de extractie-details voor een specifiek bestand binnen een project.
   *
   * @param naam projectnaam
   * @param bestandsnaam naam van het bestand
   * @return extractie-details of 404 als geen afgeronde taak bestaat
   */
  @GetMapping("/{naam}/extracties/{bestandsnaam}/details")
  public ResponseEntity<ExtractieDetailDto> geefDetails(
      @PathVariable String naam, @PathVariable String bestandsnaam) {
    var detail = extractieTaakService.geefExtractieDetails(naam, bestandsnaam);
    if (detail == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(detail);
  }

  /**
   * Voegt een manueel bezwaar toe aan een afgeronde extractietaak.
   *
   * @param naam projectnaam
   * @param bestandsnaam naam van het bestand
   * @param request verzoek met samenvatting en passage
   * @return het aangemaakte bezwaar (201 Created), of 400 bij ongeldige invoer
   */
  @PostMapping("/{naam}/extracties/{bestandsnaam}/bezwaren")
  public ResponseEntity<?> voegBezwaarToe(
      @PathVariable String naam, @PathVariable String bestandsnaam,
      @RequestBody ManueelBezwaarRequest request) {
    try {
      var detail = extractieTaakService.voegManueelBezwaarToe(
          naam, bestandsnaam, request.samenvatting(), request.passage());
      return ResponseEntity.status(201).body(detail);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new FoutResponse(e.getMessage()));
    }
  }

  /**
   * Verwijdert een manueel toegevoegd bezwaar.
   *
   * @param naam projectnaam
   * @param bestandsnaam naam van het bestand
   * @param bezwaarId id van het te verwijderen bezwaar
   * @return 204 No Content bij succes, 403 als bezwaar niet manueel is, 404 als niet gevonden
   */
  @DeleteMapping("/{naam}/extracties/{bestandsnaam}/bezwaren/{bezwaarId}")
  public ResponseEntity<Void> verwijderBezwaar(
      @PathVariable String naam, @PathVariable String bestandsnaam,
      @PathVariable Long bezwaarId) {
    try {
      extractieTaakService.verwijderManueelBezwaar(naam, bestandsnaam, bezwaarId);
      return ResponseEntity.noContent().build();
    } catch (IllegalStateException e) {
      return ResponseEntity.status(403).build();
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

  /** Request DTO voor het handmatig toevoegen van een bezwaar. */
  record ManueelBezwaarRequest(String samenvatting, String passage) {}

  /** Response DTO voor foutmeldingen. */
  record FoutResponse(String fout) {}
}
