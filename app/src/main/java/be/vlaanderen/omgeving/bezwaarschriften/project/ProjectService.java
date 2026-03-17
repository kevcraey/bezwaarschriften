package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarService;
import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.TekstExtractieService;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service die projecten en bezwaarbestanden beheert.
 *
 * <p>Leidt de bestandsstatus af uit het {@link BezwaarDocument} aggregaat.
 */
@Service
public class ProjectService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ProjectPoort projectPoort;
  private final BezwaarDocumentRepository bezwaarDocumentRepository;
  private final IndividueelBezwaarRepository bezwaarRepository;
  private final KernbezwaarService kernbezwaarService;
  private final ConsolidatieTaakRepository consolidatieTaakRepository;
  private final TekstExtractieService tekstExtractieService;

  /**
   * Maakt een nieuwe ProjectService aan.
   *
   * @param projectPoort Port voor filesystem toegang
   * @param bezwaarDocumentRepository Repository voor bezwaardocumenten
   * @param bezwaarRepository Repository voor individuele bezwaren
   * @param kernbezwaarService Service voor kernbezwaar-opruiming
   * @param consolidatieTaakRepository Repository voor consolidatie-taken
   * @param tekstExtractieService Service voor tekst-extractie
   */
  public ProjectService(ProjectPoort projectPoort,
      BezwaarDocumentRepository bezwaarDocumentRepository,
      IndividueelBezwaarRepository bezwaarRepository,
      KernbezwaarService kernbezwaarService,
      ConsolidatieTaakRepository consolidatieTaakRepository,
      TekstExtractieService tekstExtractieService) {
    this.projectPoort = projectPoort;
    this.bezwaarDocumentRepository = bezwaarDocumentRepository;
    this.bezwaarRepository = bezwaarRepository;
    this.kernbezwaarService = kernbezwaarService;
    this.consolidatieTaakRepository = consolidatieTaakRepository;
    this.tekstExtractieService = tekstExtractieService;
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
   * <p>De status wordt gelezen uit het {@link BezwaarDocument} aggregaat.
   * Bestanden zonder document-entiteit krijgen status GEEN/GEEN.
   * Niet-ondersteunde formaten krijgen NIET_ONDERSTEUND/GEEN.
   *
   * @param projectNaam Naam van het project
   * @return Lijst van bezwaarbestanden met status
   * @throws ProjectNietGevondenException Als het project niet bestaat
   */
  public List<BezwaarBestand> geefBezwaren(String projectNaam) {
    var bestandsnamen = projectPoort.geefBestandsnamen(projectNaam);
    var documenten = bezwaarDocumentRepository.findByProjectNaam(projectNaam)
        .stream().collect(Collectors.toMap(BezwaarDocument::getBestandsnaam, d -> d));

    return bestandsnamen.stream()
        .map(naam -> {
          if (!isOndersteundFormaat(naam)) {
            return new BezwaarBestand(naam, "NIET_ONDERSTEUND", "GEEN",
                null, null, false, false, null, null);
          }
          var doc = documenten.get(naam);
          if (doc == null) {
            return new BezwaarBestand(naam, "GEEN", "GEEN",
                null, null, false, false, null, null);
          }
          var aantalBezwaren = bezwaarRepository.countByDocumentId(doc.getId());
          return new BezwaarBestand(naam,
              doc.getTekstExtractieStatus().name(),
              doc.getBezwaarExtractieStatus().name(),
              doc.getAantalWoorden(),
              aantalBezwaren,
              doc.isHeeftPassagesDieNietInTekstVoorkomen(),
              doc.isHeeftManueel(),
              doc.getExtractieMethode(),
              doc.getFoutmelding());
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

      if (!isOndersteundFormaat(bestandsnaam)) {
        fouten.add(new UploadFout(bestandsnaam, "Niet-ondersteund formaat"));
        continue;
      }

      if (bestaandeNamen.contains(bestandsnaam)) {
        fouten.add(new UploadFout(bestandsnaam, "Bestand bestaat al"));
        continue;
      }

      projectPoort.slaBestandOp(projectNaam, bestandsnaam, inhoud);
      try {
        var document = new BezwaarDocument();
        document.setProjectNaam(projectNaam);
        document.setBestandsnaam(bestandsnaam);
        bezwaarDocumentRepository.save(document);
      } catch (Exception e) {
        projectPoort.verwijderBestand(projectNaam, bestandsnaam);
        throw e;
      }
      geupload.add(bestandsnaam);
    }

    // Start automatisch tekst-extractie voor geüploade bestanden
    for (var bestandsnaam : geupload) {
      tekstExtractieService.indienen(projectNaam, bestandsnaam);
    }

    return new UploadResultaat(geupload, fouten);
  }

  /**
   * Verwijdert een bezwaarbestand en bijhorende data.
   *
   * @param projectNaam Naam van het project
   * @param bestandsnaam Naam van het te verwijderen bestand
   * @return true als het bestand is verwijderd
   */
  @Transactional
  public boolean verwijderBezwaar(String projectNaam, String bestandsnaam) {
    kernbezwaarService.ruimOpNaDocumentVerwijdering(projectNaam, bestandsnaam);
    bezwaarDocumentRepository.deleteByProjectNaamAndBestandsnaam(projectNaam, bestandsnaam);
    return projectPoort.verwijderBestand(projectNaam, bestandsnaam);
  }

  /**
   * Verwijdert meerdere bezwaarbestanden en bijhorende data in één transactie.
   * Ruimt kernbezwaar-referenties op voor alle bestanden tegelijk, waarna
   * orphaned kernbezwaren, thema's en clustering-taken verwijderd worden.
   *
   * @param projectNaam Naam van het project
   * @param bestandsnamen Lijst van te verwijderen bestandsnamen
   * @return Aantal succesvol verwijderde bestanden
   */
  @Transactional
  public int verwijderBezwaren(String projectNaam, List<String> bestandsnamen) {
    bezwaarDocumentRepository.deleteByProjectNaamAndBestandsnaamIn(
        projectNaam, bestandsnamen);
    int aantalVerwijderd = 0;
    for (String bestandsnaam : bestandsnamen) {
      if (projectPoort.verwijderBestand(projectNaam, bestandsnaam)) {
        aantalVerwijderd++;
      }
    }
    kernbezwaarService.ruimOpNaBestandenVerwijdering(projectNaam, bestandsnamen);
    return aantalVerwijderd;
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

  private boolean isOndersteundFormaat(String bestandsnaam) {
    var lower = bestandsnaam.toLowerCase();
    return lower.endsWith(".txt") || lower.endsWith(".pdf");
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
   * Verwijdert een project, inclusief alle bijhorende data.
   *
   * @param naam Naam van het project
   * @return true als het project verwijderd is
   */
  @Transactional
  public boolean verwijderProject(String naam) {
    kernbezwaarService.ruimAllesOpVoorProject(naam);
    consolidatieTaakRepository.deleteByProjectNaam(naam);
    bezwaarDocumentRepository.deleteByProjectNaam(naam);
    return projectPoort.verwijderProject(naam);
  }

  /**
   * Projectoverzicht met documentaantal.
   */
  public record ProjectOverzicht(String naam, int aantalDocumenten) {}

  /**
   * Geeft alle projecten met het aantal documenten per project.
   *
   * @return Lijst van project-overzichten
   */
  public List<ProjectOverzicht> geefProjectenMetAantalDocumenten() {
    return projectPoort.geefProjecten().stream()
        .map(naam -> new ProjectOverzicht(naam, projectPoort.geefBestandsnamen(naam).size()))
        .toList();
  }
}
