package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarService;
import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.TekstExtractieService;
import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.TekstExtractieTaakRepository;
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
 * <p>Leidt de bestandsstatus af uit de meest recente extractie-taak in de database.
 */
@Service
public class ProjectService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ProjectPoort projectPoort;
  private final ExtractieTaakRepository extractieTaakRepository;
  private final KernbezwaarService kernbezwaarService;
  private final ConsolidatieTaakRepository consolidatieTaakRepository;
  private final TekstExtractieService tekstExtractieService;
  private final TekstExtractieTaakRepository tekstExtractieTaakRepository;
  private final BezwaarBestandRepository bezwaarBestandRepository;

  /**
   * Maakt een nieuwe ProjectService aan.
   *
   * @param projectPoort Port voor filesystem toegang
   * @param extractieTaakRepository Repository voor extractie-taken
   * @param kernbezwaarService Service voor kernbezwaar-opruiming
   * @param consolidatieTaakRepository Repository voor consolidatie-taken
   * @param tekstExtractieService Service voor tekst-extractie
   * @param tekstExtractieTaakRepository Repository voor tekst-extractie taken
   * @param bezwaarBestandRepository Repository voor bezwaar-bestand entiteiten
   */
  public ProjectService(ProjectPoort projectPoort,
      ExtractieTaakRepository extractieTaakRepository,
      KernbezwaarService kernbezwaarService,
      ConsolidatieTaakRepository consolidatieTaakRepository,
      TekstExtractieService tekstExtractieService,
      TekstExtractieTaakRepository tekstExtractieTaakRepository,
      BezwaarBestandRepository bezwaarBestandRepository) {
    this.projectPoort = projectPoort;
    this.extractieTaakRepository = extractieTaakRepository;
    this.kernbezwaarService = kernbezwaarService;
    this.consolidatieTaakRepository = consolidatieTaakRepository;
    this.tekstExtractieService = tekstExtractieService;
    this.tekstExtractieTaakRepository = tekstExtractieTaakRepository;
    this.bezwaarBestandRepository = bezwaarBestandRepository;
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
   * <p>De status wordt gelezen uit de {@code bezwaar_bestand} tabel. Voor legacy-bestanden
   * zonder entiteit wordt de status afgeleid uit het bestandsformaat: .txt-bestanden krijgen
   * {@link BezwaarBestandStatus#TEKST_EXTRACTIE_KLAAR}, overige ondersteunde formaten
   * {@link BezwaarBestandStatus#TODO}. Metadata (aantalWoorden, extractieMethode, etc.)
   * komt nog steeds uit de taak-tabellen.
   *
   * @param projectNaam Naam van het project
   * @return Lijst van bezwaarbestanden met status
   * @throws ProjectNietGevondenException Als het project niet bestaat
   */
  public List<BezwaarBestand> geefBezwaren(String projectNaam) {
    var bestandsnamen = projectPoort.geefBestandsnamen(projectNaam);
    var entiteitenMap = bezwaarBestandRepository.findByProjectNaam(projectNaam)
        .stream()
        .collect(Collectors.toMap(BezwaarBestandEntiteit::getBestandsnaam, e -> e));

    return bestandsnamen.stream()
        .map(naam -> {
          if (!isOndersteundFormaat(naam)) {
            return new BezwaarBestand(naam, BezwaarBestandStatus.NIET_ONDERSTEUND);
          }

          // Status uit de database, of fallback voor legacy-bestanden
          var entiteit = entiteitenMap.get(naam);
          BezwaarBestandStatus status;
          if (entiteit != null) {
            status = entiteit.getStatus();
          } else if (naam.toLowerCase().endsWith(".txt")) {
            status = BezwaarBestandStatus.TEKST_EXTRACTIE_KLAAR;
          } else {
            status = BezwaarBestandStatus.TODO;
          }

          // Metadata uit tekst-extractie taak
          var tekstExtractieTaak = tekstExtractieTaakRepository
              .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(projectNaam, naam)
              .orElse(null);

          String extractieMethode = null;
          String teAangemaaktOp = null;
          String teGestartOp = null;
          Long tekstExtractieTaakId = null;
          String foutmelding = null;

          if (tekstExtractieTaak != null) {
            extractieMethode = tekstExtractieTaak.getExtractieMethode() != null
                ? tekstExtractieTaak.getExtractieMethode().name() : null;
            teAangemaaktOp = tekstExtractieTaak.getAangemaaktOp() != null
                ? tekstExtractieTaak.getAangemaaktOp().toString() : null;
            teGestartOp = tekstExtractieTaak.getVerwerkingGestartOp() != null
                ? tekstExtractieTaak.getVerwerkingGestartOp().toString() : null;
            tekstExtractieTaakId = tekstExtractieTaak.getId();
            foutmelding = tekstExtractieTaak.getFoutmelding();
          }

          // Metadata uit AI-extractie taak
          var extractieTaak = extractieTaakRepository
              .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(projectNaam, naam)
              .orElse(null);

          Integer aantalWoorden = null;
          Integer aantalBezwaren = null;
          boolean heeftPassages = false;
          boolean heeftManueel = false;

          if (extractieTaak != null) {
            aantalWoorden = extractieTaak.getAantalWoorden();
            aantalBezwaren = extractieTaak.getAantalBezwaren();
            heeftPassages = extractieTaak.isHeeftPassagesDieNietInTekstVoorkomen();
            heeftManueel = extractieTaak.isHeeftManueel();
          }

          return new BezwaarBestand(naam, status, aantalWoorden, aantalBezwaren,
              heeftPassages, heeftManueel, extractieMethode,
              teAangemaaktOp, teGestartOp, tekstExtractieTaakId, foutmelding);
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
      bezwaarBestandRepository.save(
          new BezwaarBestandEntiteit(projectNaam, bestandsnaam, BezwaarBestandStatus.TODO));
      geupload.add(bestandsnaam);
    }

    // Start automatisch tekst-extractie voor geüploade bestanden
    for (var bestandsnaam : geupload) {
      tekstExtractieService.indienen(projectNaam, bestandsnaam);
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
    kernbezwaarService.ruimOpNaDocumentVerwijdering(projectNaam, bestandsnaam);
    extractieTaakRepository.deleteByProjectNaamAndBestandsnaam(projectNaam, bestandsnaam);
    tekstExtractieTaakRepository.deleteByProjectNaamAndBestandsnaam(projectNaam, bestandsnaam);
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
    extractieTaakRepository.deleteByProjectNaamAndBestandsnaamIn(
        projectNaam, bestandsnamen);
    tekstExtractieTaakRepository.deleteByProjectNaamAndBestandsnaamIn(
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

  private BezwaarBestandStatus vanExtractieTaakStatus(ExtractieTaakStatus status) {
    return switch (status) {
      case WACHTEND -> BezwaarBestandStatus.BEZWAAR_EXTRACTIE_WACHTEND;
      case BEZIG -> BezwaarBestandStatus.BEZWAAR_EXTRACTIE_BEZIG;
      case KLAAR -> BezwaarBestandStatus.BEZWAAR_EXTRACTIE_KLAAR;
      case FOUT -> BezwaarBestandStatus.BEZWAAR_EXTRACTIE_FOUT;
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
    kernbezwaarService.ruimAllesOpVoorProject(naam);
    consolidatieTaakRepository.deleteByProjectNaam(naam);
    extractieTaakRepository.deleteByProjectNaam(naam);
    tekstExtractieTaakRepository.deleteByProjectNaam(naam);
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
