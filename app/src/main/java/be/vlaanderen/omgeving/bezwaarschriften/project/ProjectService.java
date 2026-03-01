package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarPoort;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.transaction.Transactional;
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
  private final IngestiePoort ingestiePoort;

  /**
   * Maakt een nieuwe ProjectService aan.
   *
   * @param projectPoort Port voor filesystem toegang
   * @param extractieTaakRepository Repository voor extractie-taken
   * @param ingestiePoort Port voor bestandsingestie
   */
  public ProjectService(ProjectPoort projectPoort,
      ExtractieTaakRepository extractieTaakRepository,
      IngestiePoort ingestiePoort) {
    this.projectPoort = projectPoort;
    this.extractieTaakRepository = extractieTaakRepository;
    this.ingestiePoort = ingestiePoort;
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

  /**
   * Uploadt bezwaarbestanden naar een project.
   *
   * @param projectNaam Naam van het project
   * @param bestanden Map van bestandsnaam naar byte-inhoud
   * @return Upload-resultaat met geslaagde en gefaalde bestanden
   */
  public UploadResultaat uploadBezwaren(String projectNaam,
      Map<String, byte[]> bestanden) {
    var geupload = new ArrayList<String>();
    var fouten = new ArrayList<UploadFout>();

    var bestaandeNamen = projectPoort.geefBestandsnamen(projectNaam);

    for (var entry : bestanden.entrySet()) {
      var bestandsnaam = entry.getKey();
      var inhoud = entry.getValue();

      if (!isTxtBestand(bestandsnaam)) {
        fouten.add(new UploadFout(bestandsnaam, "Niet-ondersteund formaat"));
        continue;
      }

      if (bestaandeNamen.contains(bestandsnaam)) {
        fouten.add(new UploadFout(bestandsnaam, "Bestand bestaat al"));
        continue;
      }

      projectPoort.slaBestandOp(projectNaam, bestandsnaam, inhoud);
      geupload.add(bestandsnaam);
    }

    return new UploadResultaat(geupload, fouten);
  }

  /**
   * Verwijdert een bezwaarbestand en bijhorende extractie-taken.
   *
   * @param projectNaam Naam van het project
   * @param bestandsnaam Naam van het te verwijderen bestand
   * @return true als het bestand is verwijderd
   */
  @Transactional
  public boolean verwijderBezwaar(String projectNaam, String bestandsnaam) {
    extractieTaakRepository.deleteByProjectNaamAndBestandsnaam(projectNaam, bestandsnaam);
    return projectPoort.verwijderBestand(projectNaam, bestandsnaam);
  }

  /**
   * Geeft het volledige pad naar een bezwaarbestand.
   *
   * @param projectNaam Naam van het project
   * @param bestandsnaam Naam van het bestand
   * @return Het pad naar het bestand
   */
  public Path geefBestandsPad(String projectNaam, String bestandsnaam) {
    return projectPoort.geefBestandsPad(projectNaam, bestandsnaam);
  }

  /**
   * Geeft de bezwaarteksten van een project als invoer voor kernbezwaar-groepering.
   * Filtert op .txt-bestanden met status EXTRACTIE_KLAAR.
   *
   * @param projectNaam Naam van het project
   * @return Lijst van bezwaarinvoer voor groepering
   */
  public List<KernbezwaarPoort.BezwaarInvoer> geefBezwaartekstenVoorGroepering(
      String projectNaam) {
    var bezwaren = geefBezwaren(projectNaam);
    return bezwaren.stream()
        .filter(b -> b.status() == BezwaarBestandStatus.EXTRACTIE_KLAAR)
        .filter(b -> isTxtBestand(b.bestandsnaam()))
        .map(b -> {
          var pad = projectPoort.geefBestandsPad(projectNaam, b.bestandsnaam());
          var doc = ingestiePoort.leesBestand(pad);
          return new KernbezwaarPoort.BezwaarInvoer(null, b.bestandsnaam(), doc.tekst());
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

  /**
   * Maakt een nieuw project aan.
   *
   * @param naam Naam van het project
   * @throws IllegalArgumentException Als het project al bestaat
   */
  public void maakProjectAan(String naam) {
    projectPoort.maakProjectAan(naam);
  }

  /**
   * Verwijdert een project, inclusief alle extractie-taken.
   *
   * @param naam Naam van het project
   * @return true als het project verwijderd is
   */
  @Transactional
  public boolean verwijderProject(String naam) {
    extractieTaakRepository.deleteByProjectNaam(naam);
    return projectPoort.verwijderProject(naam);
  }
}
