package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.clustering.EmbeddingPoort;
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.BezwaarGroepLidRepository;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service voor het beheren van bezwaar-extractie.
 *
 * <p>Ondersteunt het indienen, oppakken, afronden en foutafhandeling van bezwaar-extractie
 * op basis van {@link BezwaarDocument} als aggregate root.
 */
@Service
public class ExtractieTaakService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final BezwaarDocumentRepository documentRepository;
  private final ExtractieNotificatie notificatie;
  private final IndividueelBezwaarRepository bezwaarRepository;
  private final ProjectPoort projectPoort;
  private final IngestiePoort ingestiePoort;
  private final PassageValidator passageValidator;
  private final EmbeddingPoort embeddingPoort;
  private final BezwaarGroepLidRepository bezwaarGroepLidRepository;

  /**
   * Maakt een nieuwe ExtractieTaakService aan.
   *
   * @param documentRepository repository voor bezwaardocumenten
   * @param notificatie notificatie-interface voor statuswijzigingen
   * @param bezwaarRepository repository voor individuele bezwaren
   * @param projectPoort poort voor projectbestanden
   * @param ingestiePoort poort voor het inlezen van brondocumenten
   * @param passageValidator validator voor passage-verificatie
   * @param embeddingPoort poort voor het genereren van embeddings
   * @param bezwaarGroepLidRepository repository voor passage-groep-leden (FK cleanup)
   */
  public ExtractieTaakService(
      BezwaarDocumentRepository documentRepository,
      ExtractieNotificatie notificatie,
      IndividueelBezwaarRepository bezwaarRepository,
      ProjectPoort projectPoort,
      IngestiePoort ingestiePoort,
      PassageValidator passageValidator,
      EmbeddingPoort embeddingPoort,
      BezwaarGroepLidRepository bezwaarGroepLidRepository) {
    this.documentRepository = documentRepository;
    this.notificatie = notificatie;
    this.bezwaarRepository = bezwaarRepository;
    this.projectPoort = projectPoort;
    this.ingestiePoort = ingestiePoort;
    this.passageValidator = passageValidator;
    this.embeddingPoort = embeddingPoort;
    this.bezwaarGroepLidRepository = bezwaarGroepLidRepository;
  }

  /**
   * Dient bezwaar-extractie in voor de opgegeven bestanden.
   *
   * <p>Valideert dat tekst-extractie klaar is, ruimt bestaande bezwaren op,
   * en zet de bezwaarExtractieStatus op BEZIG.
   *
   * @param projectNaam naam van het project
   * @param bestandsnamen lijst van bestandsnamen waarvoor extractie wordt ingediend
   * @return lijst van taak-DTOs
   */
  @Transactional
  public List<ExtractieTaakDto> indienen(String projectNaam, List<String> bestandsnamen) {
    return bestandsnamen.stream()
        .map(bestandsnaam -> {
          var doc = documentRepository.findByProjectNaamAndBestandsnaam(projectNaam, bestandsnaam)
              .orElseThrow(() -> new IllegalArgumentException(
                  "Document niet gevonden: " + bestandsnaam));

          if (doc.getTekstExtractieStatus() != TekstExtractieStatus.KLAAR) {
            throw new IllegalStateException(
                "Tekst-extractie niet voltooid voor bestand: " + bestandsnaam);
          }

          doc.setBezwaarExtractieStatus(BezwaarExtractieStatus.BEZIG);
          doc.setFoutmelding(null);

          // Ruim bestaande bezwaren op (eerst FK-relaties, dan bezwaren)
          var bestaandeBezwaren = bezwaarRepository.findByDocumentId(doc.getId());
          if (!bestaandeBezwaren.isEmpty()) {
            var bezwaarIds = bestaandeBezwaren.stream()
                .map(IndividueelBezwaar::getId)
                .toList();
            bezwaarGroepLidRepository.deleteByBezwaarIdIn(bezwaarIds);
            bezwaarRepository.deleteByDocumentId(doc.getId());
          }

          var opgeslagen = documentRepository.save(doc);
          var dto = ExtractieTaakDto.van(opgeslagen);
          notificatie.taakGewijzigd(dto);
          LOGGER.info("Bezwaar-extractie ingediend: project='{}', bestand='{}'",
              projectNaam, bestandsnaam);
          return dto;
        })
        .toList();
  }

  /**
   * Geeft alle documenten met actieve bezwaar-extractie-status voor een project.
   *
   * @param projectNaam naam van het project
   * @return lijst van taak-DTOs
   */
  public List<ExtractieTaakDto> geefTaken(String projectNaam) {
    return documentRepository.findByProjectNaam(projectNaam).stream()
        .filter(doc -> doc.getBezwaarExtractieStatus() != BezwaarExtractieStatus.GEEN)
        .map(ExtractieTaakDto::van)
        .toList();
  }

  /**
   * Pakt documenten op met status BEZIG voor verwerking.
   *
   * @return lijst van op te pakken documenten
   */
  @Transactional
  public List<BezwaarDocument> pakOpVoorVerwerking() {
    return documentRepository.findByBezwaarExtractieStatus(BezwaarExtractieStatus.BEZIG);
  }

  /**
   * Markeert een document als succesvol afgerond (terugwaarts-compatibele variant zonder details).
   *
   * @param documentId id van het document
   * @param aantalWoorden aantal woorden in het verwerkte bestand
   * @param aantalBezwaren aantal geextraheerde bezwaren
   */
  @Transactional
  public void markeerKlaar(Long documentId, int aantalWoorden, int aantalBezwaren) {
    markeerKlaar(documentId, new ExtractieResultaat(aantalWoorden, aantalBezwaren));
  }

  /**
   * Markeert een document als succesvol afgerond en slaat bezwaren op.
   *
   * <p>Voert passage-validatie uit door de passages te vergelijken met het originele
   * brondocument. Bij niet-gevonden passages wordt
   * {@code heeftPassagesDieNietInTekstVoorkomen} gezet.
   *
   * @param documentId id van het document
   * @param resultaat het volledige extractie-resultaat met passages en bezwaren
   */
  @Transactional
  public void markeerKlaar(Long documentId, ExtractieResultaat resultaat) {
    var doc = documentRepository.findById(documentId)
        .orElseThrow(() -> new IllegalArgumentException("Document niet gevonden: " + documentId));

    doc.setBezwaarExtractieStatus(BezwaarExtractieStatus.KLAAR);
    doc.setAantalWoorden(resultaat.aantalWoorden());

    // Bouw passage-map op voor validatie
    var passageMap = new HashMap<Integer, String>();
    for (var passage : resultaat.passages()) {
      passageMap.put(passage.id(), passage.tekst());
    }

    // Maak IndividueelBezwaar entiteiten aan
    var bezwaarEntiteiten = new ArrayList<IndividueelBezwaar>();
    for (var bezwaar : resultaat.bezwaren()) {
      var entiteit = new IndividueelBezwaar();
      entiteit.setDocumentId(documentId);
      entiteit.setSamenvatting(bezwaar.samenvatting());
      entiteit.setPassageTekst(passageMap.get(bezwaar.passageId()));
      entiteit.setManueel(false);
      bezwaarEntiteiten.add(entiteit);
    }

    // Passage-validatie: gebruik tekst-bestand i.p.v. origineel
    try {
      var pad = projectPoort.geefTekstBestandsPad(doc.getProjectNaam(), doc.getBestandsnaam());
      var brondocument = ingestiePoort.leesBestand(pad);
      var validatie = passageValidator.valideer(bezwaarEntiteiten, brondocument.tekst());
      if (validatie.aantalNietGevonden() > 0) {
        doc.setHeeftPassagesDieNietInTekstVoorkomen(true);
        LOGGER.info("Document {}: {} van {} passages niet gevonden in brondocument",
            documentId, validatie.aantalNietGevonden(), bezwaarEntiteiten.size());
      }
    } catch (Exception e) {
      LOGGER.warn("Passage-validatie overgeslagen voor document {} ({}): {}",
          documentId, doc.getBestandsnaam(), e.getMessage());
    }

    for (var entiteit : bezwaarEntiteiten) {
      bezwaarRepository.save(entiteit);
    }

    // Genereer embeddings voor alle bezwaren (batch): passage + samenvatting
    if (!bezwaarEntiteiten.isEmpty()) {
      var passageTeksten = bezwaarEntiteiten.stream()
          .map(b -> {
            var tekst = b.getPassageTekst();
            return tekst != null ? tekst : b.getSamenvatting();
          })
          .toList();
      var samenvattingen = bezwaarEntiteiten.stream()
          .map(IndividueelBezwaar::getSamenvatting)
          .toList();
      var passageEmbeddings = embeddingPoort.genereerEmbeddings(passageTeksten);
      var samenvattingEmbeddings = embeddingPoort.genereerEmbeddings(samenvattingen);
      for (int i = 0; i < bezwaarEntiteiten.size(); i++) {
        bezwaarEntiteiten.get(i).setEmbeddingPassage(passageEmbeddings.get(i));
        bezwaarEntiteiten.get(i).setEmbeddingSamenvatting(samenvattingEmbeddings.get(i));
        bezwaarRepository.save(bezwaarEntiteiten.get(i));
      }
    }

    doc.setHeeftManueel(false);
    documentRepository.save(doc);
    notificatie.taakGewijzigd(ExtractieTaakDto.van(doc));
    LOGGER.info("Document {} afgerond: {} woorden, {} bezwaren opgeslagen",
        documentId, resultaat.aantalWoorden(), bezwaarEntiteiten.size());
  }

  /**
   * Markeert een document als mislukt.
   *
   * @param documentId id van het document
   * @param foutmelding omschrijving van de fout
   */
  @Transactional
  public void markeerFout(Long documentId, String foutmelding) {
    var doc = documentRepository.findById(documentId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Document niet gevonden: " + documentId));
    doc.setBezwaarExtractieStatus(BezwaarExtractieStatus.FOUT);
    doc.setFoutmelding(foutmelding);
    documentRepository.save(doc);
    notificatie.taakGewijzigd(ExtractieTaakDto.van(doc));
    LOGGER.error("Document {} mislukt: {}", documentId, foutmelding);
  }

  /**
   * Geeft de extractie-details voor een bestand binnen een project.
   *
   * <p>Zoekt het document en combineert de geextraheerde bezwaren met hun passage-tekst.
   *
   * @param projectNaam naam van het project
   * @param bestandsnaam naam van het bestand
   * @return extractie-details, of null als geen afgerond document bestaat
   */
  public ExtractieDetailDto geefExtractieDetails(String projectNaam, String bestandsnaam) {
    var doc = documentRepository.findByProjectNaamAndBestandsnaam(projectNaam, bestandsnaam)
        .orElse(null);
    if (doc == null || doc.getBezwaarExtractieStatus() != BezwaarExtractieStatus.KLAAR) {
      return null;
    }

    var bezwaren = bezwaarRepository.findByDocumentId(doc.getId());

    var details = bezwaren.stream()
        .map(b -> new ExtractieDetailDto.BezwaarDetail(
            b.getId(),
            b.getSamenvatting(),
            b.getPassageTekst() != null ? b.getPassageTekst() : "",
            b.isPassageGevonden(),
            b.isManueel()))
        .toList();

    return new ExtractieDetailDto(bestandsnaam, details.size(), details);
  }

  /**
   * Voegt een manueel bezwaar toe aan een afgerond document.
   *
   * <p>Valideert dat de passage exact voorkomt in het originele document (na normalisatie).
   *
   * @param projectNaam naam van het project
   * @param bestandsnaam naam van het bestand
   * @param samenvatting samenvatting van het bezwaar
   * @param passageTekst exacte passage uit het originele document
   * @return het aangemaakte bezwaar als BezwaarDetail
   * @throws IllegalArgumentException bij ongeldige invoer of niet-gevonden passage
   */
  @Transactional
  public ExtractieDetailDto.BezwaarDetail voegManueelBezwaarToe(
      String projectNaam, String bestandsnaam, String samenvatting, String passageTekst) {

    if (samenvatting == null || samenvatting.isBlank()) {
      throw new IllegalArgumentException("Samenvatting mag niet leeg zijn");
    }
    if (passageTekst == null || passageTekst.isBlank()) {
      throw new IllegalArgumentException("Passage mag niet leeg zijn");
    }

    var doc = documentRepository.findByProjectNaamAndBestandsnaam(projectNaam, bestandsnaam)
        .orElseThrow(() -> new IllegalArgumentException(
            "Document niet gevonden voor: " + bestandsnaam));

    if (doc.getBezwaarExtractieStatus() != BezwaarExtractieStatus.KLAAR) {
      throw new IllegalArgumentException(
          "Bezwaar-extractie is niet afgerond: " + doc.getBezwaarExtractieStatus());
    }

    // Passage-validatie: exacte match na normalisatie (gebruik tekst-bestand)
    var pad = projectPoort.geefTekstBestandsPad(projectNaam, bestandsnaam);
    var brondocument = ingestiePoort.leesBestand(pad);
    var genormaliseerdeDocument = passageValidator.normaliseer(brondocument.tekst());
    var genormaliseerdePassage = passageValidator.normaliseer(passageTekst);

    if (!genormaliseerdeDocument.contains(genormaliseerdePassage)) {
      throw new IllegalArgumentException("Passage komt niet voor in het originele document");
    }

    // Sla bezwaar op
    var bezwaarEntiteit = new IndividueelBezwaar();
    bezwaarEntiteit.setDocumentId(doc.getId());
    bezwaarEntiteit.setSamenvatting(samenvatting);
    bezwaarEntiteit.setPassageTekst(passageTekst);
    bezwaarEntiteit.setPassageGevonden(true);
    bezwaarEntiteit.setManueel(true);

    // Werk document bij
    doc.setHeeftManueel(true);
    documentRepository.save(doc);
    notificatie.taakGewijzigd(ExtractieTaakDto.van(doc));

    var opgeslagen = bezwaarRepository.save(bezwaarEntiteit);

    var embeddings = embeddingPoort.genereerEmbeddings(List.of(passageTekst, samenvatting));
    opgeslagen.setEmbeddingPassage(embeddings.get(0));
    opgeslagen.setEmbeddingSamenvatting(embeddings.get(1));
    bezwaarRepository.save(opgeslagen);

    return new ExtractieDetailDto.BezwaarDetail(
        opgeslagen.getId(), samenvatting, passageTekst, true, true);
  }

  /**
   * Verwijdert een bezwaar (manueel of AI).
   *
   * @param projectNaam naam van het project
   * @param bestandsnaam naam van het bestand
   * @param bezwaarId id van het te verwijderen bezwaar
   * @throws IllegalArgumentException als het bezwaar niet gevonden wordt
   */
  @Transactional
  public void verwijderBezwaar(String projectNaam, String bestandsnaam, Long bezwaarId) {
    var bezwaar = bezwaarRepository.findById(bezwaarId)
        .orElseThrow(() -> new IllegalArgumentException("Bezwaar niet gevonden: " + bezwaarId));

    var doc = documentRepository.findById(bezwaar.getDocumentId())
        .orElseThrow(() -> new IllegalArgumentException("Document niet gevonden"));

    if (!doc.getProjectNaam().equals(projectNaam)) {
      throw new IllegalArgumentException(
          "Bezwaar " + bezwaarId + " behoort niet tot project: " + projectNaam);
    }

    boolean wasAiBezwaar = !bezwaar.isManueel();

    // Verwijder FK-relaties, dan bezwaar
    bezwaarGroepLidRepository.deleteByBezwaarIdIn(List.of(bezwaarId));
    bezwaarRepository.delete(bezwaar);

    var overigeBezwaren = bezwaarRepository.findByDocumentId(doc.getId());

    if (wasAiBezwaar) {
      doc.setHeeftManueel(true);
    } else {
      boolean nogManueel = overigeBezwaren.stream().anyMatch(IndividueelBezwaar::isManueel);
      doc.setHeeftManueel(nogManueel);
    }

    boolean nogNietGevonden = overigeBezwaren.stream().anyMatch(b -> !b.isPassageGevonden());
    doc.setHeeftPassagesDieNietInTekstVoorkomen(nogNietGevonden);

    documentRepository.save(doc);
    notificatie.taakGewijzigd(ExtractieTaakDto.van(doc));
  }

  /**
   * Verwerkt onafgeronde items voor een project: herstelt documenten met fout-status.
   *
   * @param projectNaam naam van het project
   * @return het totaal aantal herstartte documenten
   */
  @Transactional
  public int verwerkOnafgeronde(String projectNaam) {
    var documenten = documentRepository.findByProjectNaam(projectNaam);
    var foutDocumenten = documenten.stream()
        .filter(d -> d.getBezwaarExtractieStatus() == BezwaarExtractieStatus.FOUT)
        .toList();

    for (var doc : foutDocumenten) {
      doc.setBezwaarExtractieStatus(BezwaarExtractieStatus.BEZIG);
      doc.setFoutmelding(null);
      documentRepository.save(doc);
      notificatie.taakGewijzigd(ExtractieTaakDto.van(doc));
    }

    int totaal = foutDocumenten.size();
    LOGGER.info("Verwerkt {} onafgeronde documenten voor project '{}'",
        totaal, projectNaam);
    return totaal;
  }
}
