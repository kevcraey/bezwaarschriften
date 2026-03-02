package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ConsolidatieController {

  private final ConsolidatieTaakService consolidatieTaakService;
  private final ConsolidatieWorker consolidatieWorker;
  private final AntwoordStatusService antwoordStatusService;

  public ConsolidatieController(ConsolidatieTaakService consolidatieTaakService,
      ConsolidatieWorker consolidatieWorker,
      AntwoordStatusService antwoordStatusService) {
    this.consolidatieTaakService = consolidatieTaakService;
    this.consolidatieWorker = consolidatieWorker;
    this.antwoordStatusService = antwoordStatusService;
  }

  @GetMapping("/{naam}/consolidaties")
  public ResponseEntity<ConsolidatieResponse> geefStatus(@PathVariable String naam) {
    var antwoordStatus = antwoordStatusService.berekenAntwoordStatus(naam);
    var taken = consolidatieTaakService.geefTaken(naam);
    var taakPerBestand = taken.stream()
        .collect(Collectors.toMap(ConsolidatieTaakDto::bestandsnaam, t -> t, (a, b) -> b));

    var documenten = antwoordStatus.entrySet().stream()
        .map(entry -> {
          var bestandsnaam = entry.getKey();
          var status = entry.getValue();
          var taak = taakPerBestand.get(bestandsnaam);

          String consolidatieStatus;
          ConsolidatieTaakDto taakDto = null;
          if (taak != null) {
            consolidatieStatus = taak.status();
            taakDto = taak;
          } else if (status.isVolledig()) {
            consolidatieStatus = "volledig";
          } else {
            consolidatieStatus = "onvolledig";
          }

          return new DocumentConsolidatieStatus(
              bestandsnaam, status.aantalMetAntwoord(), status.totaal(),
              consolidatieStatus, taakDto);
        })
        .sorted(Comparator.comparing(DocumentConsolidatieStatus::bestandsnaam))
        .toList();

    return ResponseEntity.ok(new ConsolidatieResponse(documenten));
  }

  @PostMapping("/{naam}/consolidaties")
  public ResponseEntity<ConsolidatieTakenResponse> indienen(
      @PathVariable String naam, @RequestBody ConsolidatiesRequest request) {
    var taken = consolidatieTaakService.indienen(naam, request.bestandsnamen());
    return ResponseEntity.ok(new ConsolidatieTakenResponse(taken));
  }

  @DeleteMapping("/{naam}/consolidaties/{taakId}")
  public ResponseEntity<Void> annuleer(@PathVariable String naam, @PathVariable Long taakId) {
    try {
      consolidatieTaakService.verwijderTaak(naam, taakId);
      consolidatieWorker.annuleerTaak(taakId);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    }
  }

  record ConsolidatieResponse(List<DocumentConsolidatieStatus> documenten) {}

  record DocumentConsolidatieStatus(String bestandsnaam, int antwoordenAantal,
      int antwoordenTotaal, String status, ConsolidatieTaakDto taak) {}

  record ConsolidatieTakenResponse(List<ConsolidatieTaakDto> taken) {}

  record ConsolidatiesRequest(List<String> bestandsnamen) {}
}
