package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.lang.invoke.MethodHandles;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service die projecten en bezwaarbestanden beheert.
 *
 * <p>Leidt de bestandsstatus af uit de meest recente extractie-taak in de database.
 */
@Service
public class ProjectService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ProjectPoort projectPoort;
  private final ExtractieTaakRepository extractieTaakRepository;

  /**
   * Maakt een nieuwe ProjectService aan.
   *
   * @param projectPoort Port voor filesystem toegang
   * @param extractieTaakRepository Repository voor extractie-taken
   */
  public ProjectService(ProjectPoort projectPoort,
      ExtractieTaakRepository extractieTaakRepository) {
    this.projectPoort = projectPoort;
    this.extractieTaakRepository = extractieTaakRepository;
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
   * <p>De status wordt afgeleid uit de meest recente extractie-taak in de database.
   * Als er geen taak bestaat, is de status {@link BezwaarBestandStatus#TODO} voor
   * .txt-bestanden en {@link BezwaarBestandStatus#NIET_ONDERSTEUND} voor overige.
   *
   * @param projectNaam Naam van het project
   * @return Lijst van bezwaarbestanden met status
   * @throws ProjectNietGevondenException Als het project niet bestaat
   */
  public List<BezwaarBestand> geefBezwaren(String projectNaam) {
    var bestandsnamen = projectPoort.geefBestandsnamen(projectNaam);
    return bestandsnamen.stream()
        .map(naam -> {
          if (!isTxtBestand(naam)) {
            return new BezwaarBestand(naam, BezwaarBestandStatus.NIET_ONDERSTEUND);
          }
          var laatsteTaak = extractieTaakRepository
              .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(projectNaam, naam)
              .orElse(null);
          if (laatsteTaak == null) {
            return new BezwaarBestand(naam, BezwaarBestandStatus.TODO);
          }
          return new BezwaarBestand(naam,
              vanExtractieTaakStatus(laatsteTaak.getStatus()),
              laatsteTaak.getAantalWoorden(),
              laatsteTaak.getAantalBezwaren());
        })
        .toList();
  }

  private boolean isTxtBestand(String bestandsnaam) {
    return bestandsnaam.toLowerCase().endsWith(".txt");
  }

  private BezwaarBestandStatus vanExtractieTaakStatus(ExtractieTaakStatus status) {
    return switch (status) {
      case WACHTEND -> BezwaarBestandStatus.WACHTEND;
      case BEZIG -> BezwaarBestandStatus.BEZIG;
      case KLAAR -> BezwaarBestandStatus.EXTRACTIE_KLAAR;
      case FOUT -> BezwaarBestandStatus.FOUT;
    };
  }
}
