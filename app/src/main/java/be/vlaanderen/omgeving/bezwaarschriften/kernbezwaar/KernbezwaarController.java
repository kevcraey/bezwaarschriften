package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakService;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocument;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocumentRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.IndividueelBezwaar;
import be.vlaanderen.omgeving.bezwaarschriften.project.IndividueelBezwaarRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller voor kernbezwaar-operaties.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class KernbezwaarController {

  private final KernbezwaarService kernbezwaarService;
  private final KernbezwaarReferentieRepository referentieRepository;
  private final ConsolidatieTaakService consolidatieTaakService;
  private final BezwaarGroepLidRepository bezwaarGroepLidRepository;
  private final IndividueelBezwaarRepository bezwaarRepository;
  private final BezwaarDocumentRepository documentRepository;

  public KernbezwaarController(KernbezwaarService kernbezwaarService,
      KernbezwaarReferentieRepository referentieRepository,
      ConsolidatieTaakService consolidatieTaakService,
      BezwaarGroepLidRepository bezwaarGroepLidRepository,
      IndividueelBezwaarRepository bezwaarRepository,
      BezwaarDocumentRepository documentRepository) {
    this.kernbezwaarService = kernbezwaarService;
    this.referentieRepository = referentieRepository;
    this.consolidatieTaakService = consolidatieTaakService;
    this.bezwaarGroepLidRepository = bezwaarGroepLidRepository;
    this.bezwaarRepository = bezwaarRepository;
    this.documentRepository = documentRepository;
  }

  /**
   * Geeft eerder berekende kernbezwaren voor een project.
   */
  @GetMapping("/{naam}/kernbezwaren")
  public ResponseEntity<KernbezwarenResponse> geefKernbezwaren(
      @PathVariable String naam) {
    return kernbezwaarService.geefKernbezwaren(naam)
        .map(kernen -> ResponseEntity.ok(new KernbezwarenResponse(kernen)))
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
    // Haal bestandsnamen op via bezwaar_groep_lid -> bezwaar -> document
    var refs = referentieRepository.findByKernbezwaarIdIn(List.of(id));
    var groepIds = refs.stream()
        .map(KernbezwaarReferentieEntiteit::getPassageGroepId).toList();
    var leden = bezwaarGroepLidRepository.findByBezwaarGroepIdIn(groepIds);
    var bezwaarIds = leden.stream()
        .map(BezwaarGroepLid::getBezwaarId).distinct().toList();
    var bezwaren = bezwaarRepository.findAllById(bezwaarIds);
    var documentIds = bezwaren.stream()
        .map(IndividueelBezwaar::getDocumentId).distinct().toList();
    var bestandsnamen = documentRepository.findAllById(documentIds).stream()
        .map(BezwaarDocument::getBestandsnaam).distinct().toList();
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

  /**
   * Geeft top-5 kernbezwaar-suggesties voor een noise-bezwaar.
   */
  @GetMapping("/{naam}/noise/{bezwaarId}/suggesties")
  public ResponseEntity<List<SuggestieResponse>> geefSuggesties(
      @PathVariable String naam,
      @PathVariable Long bezwaarId) {
    var suggesties = kernbezwaarService.geefSuggesties(naam, bezwaarId);
    var response = suggesties.stream()
        .map(s -> new SuggestieResponse(s.kernbezwaarId(),
            (int) Math.round(s.score() * 100),
            kernbezwaarService.geefSamenvatting(s.kernbezwaarId())))
        .toList();
    return ResponseEntity.ok(response);
  }

  /**
   * Wijst een referentie handmatig toe aan een kernbezwaar.
   */
  @PutMapping("/{naam}/referenties/{referentieId}/toewijzing")
  public ResponseEntity<Void> wijsToe(
      @PathVariable String naam,
      @PathVariable Long referentieId,
      @RequestBody ToewijzingRequest request) {
    kernbezwaarService.wijsToeAanKernbezwaar(referentieId, request.kernbezwaarId());
    return ResponseEntity.ok().build();
  }

  /** Response DTO met kernbezwaren (flat, zonder thema-laag). */
  record KernbezwarenResponse(List<Kernbezwaar> kernbezwaren) {}

  /** Request DTO voor het opslaan van een antwoord. */
  record AntwoordRequest(String inhoud) {}

  /** Response bij 409 Conflict: lijst van getroffen bestandsnamen. */
  record ConflictResponse(List<String> getroffenDocumenten) {}

  /** Response DTO voor een kernbezwaar-suggestie. */
  record SuggestieResponse(Long kernbezwaarId, int scorePercentage, String samenvatting) {}

  /** Request DTO voor handmatige toewijzing. */
  record ToewijzingRequest(Long kernbezwaarId) {}
}
