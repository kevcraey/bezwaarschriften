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

  /** In-memory statusregister: projectNaam → bestandsnaam → status. */
  private final Map<String, Map<String, BezwaarBestandStatus>> statusRegister =
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
    return bestandsnamen.stream()
        .map(naam -> new BezwaarBestand(naam, bepaalStatus(projectNaam, naam)))
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
        ingestiePoort.leesBestand(bestandsPad);
        projectStatussen.put(bestand.bestandsnaam(), BezwaarBestandStatus.EXTRACTIE_KLAAR);
        LOGGER.info("Bestand '{}' succesvol verwerkt voor project '{}'",
            bestand.bestandsnaam(), projectNaam);
      } catch (Exception e) {
        projectStatussen.put(bestand.bestandsnaam(), BezwaarBestandStatus.FOUT);
        LOGGER.warn("Fout bij verwerking van '{}' voor project '{}': {}",
            bestand.bestandsnaam(), projectNaam, e.getMessage());
      }
    }

    return geefBezwaren(projectNaam);
  }

  private BezwaarBestandStatus bepaalStatus(String projectNaam, String bestandsnaam) {
    var projectStatussen = statusRegister.get(projectNaam);
    if (projectStatussen != null && projectStatussen.containsKey(bestandsnaam)) {
      return projectStatussen.get(bestandsnaam);
    }
    return isTxtBestand(bestandsnaam)
        ? BezwaarBestandStatus.TODO
        : BezwaarBestandStatus.NIET_ONDERSTEUND;
  }

  private boolean isTxtBestand(String bestandsnaam) {
    return bestandsnaam.toLowerCase().endsWith(".txt");
  }
}
