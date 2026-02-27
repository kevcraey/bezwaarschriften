package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service die projecten en bezwaarverwerking orkestreert.
 *
 * <p>Beheert in-memory verwerkingsstatussen per sessie.
 */
@Service
public class ProjectService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ProjectPoort projectPoort;
  private final IngestiePoort ingestiePoort;
  private final Path inputFolder;

  /** In-memory statusregister: projectNaam → bestandsnaam → verwerkingsresultaat. */
  private final Map<String, Map<String, VerwerkingsResultaat>> statusRegister =
      new ConcurrentHashMap<>();

  /**
   * Maakt een nieuwe ProjectService aan.
   *
   * @param projectPoort Port voor filesystem toegang
   * @param ingestiePoort Port voor bestandsingestie
   * @param inputFolderString Root input folder als string-pad
   */
  public ProjectService(
      ProjectPoort projectPoort,
      IngestiePoort ingestiePoort,
      @Value("${bezwaarschriften.input.folder}") String inputFolderString) {
    this.projectPoort = projectPoort;
    this.ingestiePoort = ingestiePoort;
    this.inputFolder = Path.of(inputFolderString);
  }

  /**
   * Geeft de lijst van beschikbare projecten terug.
   *
   * @return Lijst van projectnamen
   */
  public List<String> geefProjecten() {
    return projectPoort.geefProjecten();
  }

  /**
   * Geeft de bezwaarbestanden van een project terug met hun huidige status.
   *
   * @param projectNaam Naam van het project
   * @return Lijst van bezwaarbestanden met status
   * @throws ProjectNietGevondenException Als het project niet bestaat
   */
  public List<BezwaarBestand> geefBezwaren(String projectNaam) {
    var bestandsnamen = projectPoort.geefBestandsnamen(projectNaam);
    var projectResultaten = statusRegister.get(projectNaam);
    return bestandsnamen.stream()
        .map(naam -> {
          if (projectResultaten != null && projectResultaten.containsKey(naam)) {
            var resultaat = projectResultaten.get(naam);
            return new BezwaarBestand(naam, resultaat.status(), resultaat.aantalWoorden());
          }
          return new BezwaarBestand(naam, isTxtBestand(naam)
              ? BezwaarBestandStatus.TODO
              : BezwaarBestandStatus.NIET_ONDERSTEUND);
        })
        .toList();
  }

  /**
   * Start de batchverwerking voor alle openstaande .txt-bestanden van een project.
   *
   * @param projectNaam Naam van het project
   * @return Bijgewerkte lijst van bezwaarbestanden met status
   * @throws ProjectNietGevondenException Als het project niet bestaat
   */
  public List<BezwaarBestand> verwerk(String projectNaam) {
    var bezwaren = geefBezwaren(projectNaam);
    var projectStatussen = statusRegister.computeIfAbsent(projectNaam,
        k -> new ConcurrentHashMap<>());

    for (var bestand : bezwaren) {
      if (bestand.status() != BezwaarBestandStatus.TODO) {
        continue;
      }
      var bestandsPad = inputFolder.resolve(projectNaam).resolve("bezwaren")
          .resolve(bestand.bestandsnaam());
      try {
        var brondocument = ingestiePoort.leesBestand(bestandsPad);
        var aantalWoorden = telWoorden(brondocument.tekst());
        projectStatussen.put(bestand.bestandsnaam(),
            new VerwerkingsResultaat(BezwaarBestandStatus.EXTRACTIE_KLAAR, aantalWoorden));
        LOGGER.info("Bestand '{}' succesvol verwerkt voor project '{}' ({} woorden)",
            bestand.bestandsnaam(), projectNaam, aantalWoorden);
      } catch (Exception e) {
        projectStatussen.put(bestand.bestandsnaam(),
            new VerwerkingsResultaat(BezwaarBestandStatus.FOUT, null));
        LOGGER.warn("Fout bij verwerking van '{}' voor project '{}': {}",
            bestand.bestandsnaam(), projectNaam, e.getMessage());
      }
    }

    return geefBezwaren(projectNaam);
  }

  private boolean isTxtBestand(String bestandsnaam) {
    return bestandsnaam.toLowerCase().endsWith(".txt");
  }

  private int telWoorden(String tekst) {
    if (tekst == null || tekst.isBlank()) {
      return 0;
    }
    return tekst.strip().split("\\s+").length;
  }

  private record VerwerkingsResultaat(BezwaarBestandStatus status, Integer aantalWoorden) { }
}
