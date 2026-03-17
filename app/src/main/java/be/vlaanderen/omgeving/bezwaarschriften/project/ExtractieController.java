package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.TekstExtractieService;
import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.TekstExtractieTaakDto;
import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.TekstExtractieWorker;
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
  private final TekstExtractieService tekstExtractieService;
  private final TekstExtractieWorker tekstExtractieWorker;

  /**
   * Maakt een nieuwe ExtractieController aan.
   *
   * @param extractieTaakService service voor bezwaar-extractie beheer
   * @param tekstExtractieService service voor tekst-extractie taken
   * @param tekstExtractieWorker worker voor tekst-extractie taken
   */
  public ExtractieController(ExtractieTaakService extractieTaakService,
      TekstExtractieService tekstExtractieService,
      TekstExtractieWorker tekstExtractieWorker) {
    this.extractieTaakService = extractieTaakService;
    this.tekstExtractieService = tekstExtractieService;
    this.tekstExtractieWorker = tekstExtractieWorker;
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
   * Annuleert een tekst-extractie taak.
   *
   * @param naam projectnaam
   * @param taakId id van de te annuleren taak
   * @return 204 No Content bij succes, 404 als taak niet gevonden
   */
  @DeleteMapping("/{naam}/tekst-extracties/{taakId}")
  public ResponseEntity<Void> annuleerTekstExtractie(
      @PathVariable String naam, @PathVariable Long taakId) {
    try {
      tekstExtractieService.verwijderTaak(naam, taakId);
      tekstExtractieWorker.annuleerTaak(taakId);
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
   * Verwijdert een bezwaar (manueel of AI-gegenereerd).
   *
   * @param naam projectnaam
   * @param bestandsnaam naam van het bestand
   * @param bezwaarId id van het te verwijderen bezwaar
   * @return 204 No Content bij succes, 404 als niet gevonden
   */
  @DeleteMapping("/{naam}/extracties/{bestandsnaam}/bezwaren/{bezwaarId}")
  public ResponseEntity<Void> verwijderBezwaar(
      @PathVariable String naam, @PathVariable String bestandsnaam,
      @PathVariable Long bezwaarId) {
    try {
      extractieTaakService.verwijderBezwaar(naam, bestandsnaam, bezwaarId);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * Geeft de geextraheerde tekst voor een bestand.
   *
   * @param naam projectnaam
   * @param bestandsnaam naam van het bestand
   * @return tekst of 404 als niet beschikbaar
   */
  @GetMapping("/{naam}/tekst-extracties/{bestandsnaam}/tekst")
  public ResponseEntity<GeextraheerdetekstResponse> geefGeextraheerdetekst(
      @PathVariable String naam, @PathVariable String bestandsnaam) {
    var tekst = tekstExtractieService.geefGeextraheerdetekst(naam, bestandsnaam);
    if (tekst == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(new GeextraheerdetekstResponse(bestandsnaam, tekst));
  }

  /**
   * Herstart mislukte tekst-extractie taken voor de opgegeven bestanden.
   *
   * @param naam projectnaam
   * @param request verzoek met bestandsnamen
   * @return geherstarte tekst-extractie taken
   */
  @PostMapping("/{naam}/tekst-extracties/herstarten")
  public ResponseEntity<?> herstartTekstExtracties(
      @PathVariable String naam, @RequestBody ExtractiesRequest request) {
    try {
      var taken = request.bestandsnamen().stream()
          .map(bestandsnaam -> tekstExtractieService.herstartTekstExtractie(naam, bestandsnaam))
          .toList();
      return ResponseEntity.ok(new TekstExtractieTakenResponse(taken));
    } catch (IllegalArgumentException | IllegalStateException e) {
      return ResponseEntity.badRequest().body(new FoutResponse(e.getMessage()));
    }
  }

  /** Response DTO met een lijst van tekst-extractie taken. */
  record TekstExtractieTakenResponse(List<TekstExtractieTaakDto> taken) {}

  /** Response DTO voor geextraheerde tekst. */
  record GeextraheerdetekstResponse(String bestandsnaam, String tekst) {}

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
