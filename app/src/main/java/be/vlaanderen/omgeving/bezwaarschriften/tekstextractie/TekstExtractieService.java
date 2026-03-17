package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.PassageGroepLidRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocument;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocumentRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarExtractieStatus;
import be.vlaanderen.omgeving.bezwaarschriften.project.IndividueelBezwaar;
import be.vlaanderen.omgeving.bezwaarschriften.project.IndividueelBezwaarRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.ProjectPoort;
import be.vlaanderen.omgeving.bezwaarschriften.project.TekstExtractieStatus;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service die de volledige tekst-extractie flow orkestreert.
 *
 * <p>Beheert het indienen, oppakken en verwerken van tekst-extractie taken.
 * PDF-bestanden worden geextraheerd via {@link PdfTekstExtractor},
 * txt-bestanden worden direct ingelezen met kwaliteitscontrole.
 *
 * <p>Werkt rechtstreeks op {@link BezwaarDocument} als aggregate root.
 */
@Service
public class TekstExtractieService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final BezwaarDocumentRepository documentRepository;
  private final IndividueelBezwaarRepository bezwaarRepository;
  private final PassageGroepLidRepository passageGroepLidRepository;
  private final PdfTekstExtractor pdfExtractor;
  private final TekstKwaliteitsControle kwaliteitsControle;
  private final ProjectPoort projectPoort;
  private final PseudonimiseringPoort pseudonimiseringPoort;
  private final TekstExtractieNotificatie notificatie;
  private final PseudonimiseringChunker chunker;
  private final PseudonimiseringChunkRepository chunkRepository;
  private final int maxConcurrent;

  /**
   * Maakt een nieuwe TekstExtractieService aan.
   *
   * @param documentRepository repository voor bezwaardocumenten
   * @param bezwaarRepository repository voor individuele bezwaren
   * @param passageGroepLidRepository repository voor passage-groepleden (FK cleanup)
   * @param pdfExtractor extractor voor PDF-bestanden
   * @param kwaliteitsControle kwaliteitscontrole voor geextraheerde tekst
   * @param projectPoort poort voor projectbestanden
   * @param pseudonimiseringPoort poort voor pseudonimisering van tekst
   * @param notificatie notificatie-interface voor statuswijzigingen
   * @param chunker chunker voor opsplitsing van grote teksten
   * @param chunkRepository repository voor pseudonimisering chunk mapping-ID's
   * @param maxConcurrent maximum aantal gelijktijdig verwerkbare taken
   */
  public TekstExtractieService(
      BezwaarDocumentRepository documentRepository,
      IndividueelBezwaarRepository bezwaarRepository,
      PassageGroepLidRepository passageGroepLidRepository,
      PdfTekstExtractor pdfExtractor,
      TekstKwaliteitsControle kwaliteitsControle,
      ProjectPoort projectPoort,
      PseudonimiseringPoort pseudonimiseringPoort,
      TekstExtractieNotificatie notificatie,
      PseudonimiseringChunker chunker,
      PseudonimiseringChunkRepository chunkRepository,
      @Value("${bezwaarschriften.tekst-extractie.max-concurrent:2}") int maxConcurrent) {
    this.documentRepository = documentRepository;
    this.bezwaarRepository = bezwaarRepository;
    this.passageGroepLidRepository = passageGroepLidRepository;
    this.pdfExtractor = pdfExtractor;
    this.kwaliteitsControle = kwaliteitsControle;
    this.projectPoort = projectPoort;
    this.pseudonimiseringPoort = pseudonimiseringPoort;
    this.notificatie = notificatie;
    this.chunker = chunker;
    this.chunkRepository = chunkRepository;
    this.maxConcurrent = maxConcurrent;
  }

  /**
   * Dient een tekst-extractie in voor een bezwaardocument.
   *
   * <p>Vindt of maakt een {@link BezwaarDocument} en zet de tekst-extractie status op BEZIG.
   * Bij een her-extractie (document heeft al bezwaren) worden eerst de bestaande bezwaren
   * en bijbehorende passage-groepleden opgeruimd.
   *
   * @param projectNaam naam van het project
   * @param bestandsnaam naam van het bestand
   * @return het bijgewerkte document
   */
  @Transactional
  public BezwaarDocument indienen(String projectNaam, String bestandsnaam) {
    var document = documentRepository.findByProjectNaamAndBestandsnaam(projectNaam, bestandsnaam)
        .orElseGet(() -> {
          var nieuw = new BezwaarDocument();
          nieuw.setProjectNaam(projectNaam);
          nieuw.setBestandsnaam(bestandsnaam);
          return nieuw;
        });

    // Bij her-extractie: ruim bestaande bezwaren op
    if (document.getId() != null) {
      var bezwaren = bezwaarRepository.findByDocumentId(document.getId());
      if (!bezwaren.isEmpty()) {
        var bezwaarIds = bezwaren.stream().map(IndividueelBezwaar::getId).toList();
        passageGroepLidRepository.deleteByBezwaarIdIn(bezwaarIds);
        bezwaarRepository.deleteByDocumentId(document.getId());
        document.setBezwaarExtractieStatus(BezwaarExtractieStatus.GEEN);
        LOGGER.info("Her-extractie: {} bezwaren opgeruimd voor document {}/{}",
            bezwaarIds.size(), projectNaam, bestandsnaam);
      }
    }

    document.setTekstExtractieStatus(TekstExtractieStatus.BEZIG);
    document.setFoutmelding(null);
    var opgeslagen = documentRepository.save(document);
    notificatie.tekstExtractieTaakGewijzigd(TekstExtractieTaakDto.van(opgeslagen));
    LOGGER.info("Tekst-extractie ingediend: project='{}', bestand='{}'",
        projectNaam, bestandsnaam);
    return opgeslagen;
  }

  /**
   * Geeft documenten die klaar zijn voor tekst-extractie verwerking.
   *
   * <p>Beperkt het aantal tot het maximum aantal gelijktijdige verwerkingen.
   *
   * @return lijst van documenten met status BEZIG, leeg als maximum bereikt
   */
  @Transactional(readOnly = true)
  public List<BezwaarDocument> pakOpVoorVerwerking() {
    var bezig = documentRepository.findByTekstExtractieStatus(TekstExtractieStatus.BEZIG);

    if (bezig.isEmpty()) {
      return List.of();
    }

    return bezig.stream().limit(maxConcurrent).toList();
  }

  /**
   * Verwerkt een tekst-extractie voor een document.
   *
   * <p>Voor PDF-bestanden wordt {@link PdfTekstExtractor} gebruikt.
   * Voor txt-bestanden wordt de tekst direct ingelezen met kwaliteitscontrole.
   * De geextraheerde tekst wordt gepseudonimiseerd en opgeslagen via {@link ProjectPoort#slaTekstOp}.
   *
   * @param document het te verwerken document
   */
  public void verwerkTaak(BezwaarDocument document) {
    try {
      var pad = projectPoort.geefBestandsPad(document.getProjectNaam(),
          document.getBestandsnaam());
      var bestandsnaam = document.getBestandsnaam().toLowerCase();

      if (bestandsnaam.endsWith(".pdf")) {
        var resultaat = pdfExtractor.extraheer(pad);
        pseudonimiseerEnSlaOp(document, resultaat.tekst(), resultaat.methode());
      } else if (bestandsnaam.endsWith(".txt")) {
        var tekst = Files.readString(pad);
        var controle = kwaliteitsControle.controleer(tekst);
        if (!controle.isValide()) {
          markeerFout(document.getId(),
              "Kwaliteitscontrole mislukt: " + controle.reden());
          return;
        }
        pseudonimiseerEnSlaOp(document, tekst, ExtractieMethode.DIGITAAL);
      } else {
        markeerFout(document.getId(),
            "Niet-ondersteund bestandstype: " + document.getBestandsnaam());
      }
    } catch (PseudonimiseringException e) {
      LOGGER.error("Pseudonimisering mislukt voor document {}: {}",
          document.getId(), e.getMessage(), e);
      markeerFout(document.getId(), e.getMessage());
    } catch (OcrNietBeschikbaarException e) {
      LOGGER.warn("OCR niet beschikbaar voor document {}: {}",
          document.getId(), e.getMessage());
      markeerFout(document.getId(), e.getMessage());
    } catch (IOException e) {
      LOGGER.error("Fout bij verwerking van document {}: {}",
          document.getId(), e.getMessage(), e);
      markeerFout(document.getId(), e.getMessage());
    } catch (Exception e) {
      LOGGER.error("Onverwachte fout bij verwerking van document {}: {}",
          document.getId(), e.getMessage(), e);
      markeerFout(document.getId(), e.getMessage());
    }
  }

  private void pseudonimiseerEnSlaOp(BezwaarDocument document, String tekst,
      ExtractieMethode methode) {
    // Ruim eventuele eerdere chunks op (idempotent bij retry)
    chunkRepository.deleteByDocumentId(document.getId());

    var chunks = chunker.chunk(tekst);
    var gepseudonimiseerdeChunks = new ArrayList<String>();

    for (int i = 0; i < chunks.size(); i++) {
      var resultaat = pseudonimiseringPoort.pseudonimiseer(chunks.get(i));
      gepseudonimiseerdeChunks.add(resultaat.gepseudonimiseerdeTekst());
      chunkRepository.save(
          new PseudonimiseringChunk(document.getId(), i, resultaat.mappingId()));
    }

    var samengevoegd = String.join("\n\n", gepseudonimiseerdeChunks);
    projectPoort.slaTekstOp(document.getProjectNaam(), document.getBestandsnaam(),
        samengevoegd);
    markeerKlaar(document.getId(), methode);
  }

  /**
   * Markeert een document als succesvol geextraheerd.
   *
   * @param documentId id van het document
   * @param methode de gebruikte extractiemethode
   */
  @Transactional
  public void markeerKlaar(Long documentId, ExtractieMethode methode) {
    var document = documentRepository.findById(documentId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Document niet gevonden: " + documentId));
    document.setTekstExtractieStatus(TekstExtractieStatus.KLAAR);
    document.setExtractieMethode(methode.name());
    documentRepository.save(document);
    notificatie.tekstExtractieTaakGewijzigd(TekstExtractieTaakDto.van(document));
    LOGGER.info("Tekst-extractie document {} afgerond (methode={})", documentId, methode);
  }

  /**
   * Markeert een document als mislukt.
   *
   * @param documentId id van het document
   * @param foutmelding beschrijving van de fout
   */
  @Transactional
  public void markeerFout(Long documentId, String foutmelding) {
    var document = documentRepository.findById(documentId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Document niet gevonden: " + documentId));
    document.setTekstExtractieStatus(TekstExtractieStatus.FOUT);
    document.setFoutmelding(foutmelding);
    documentRepository.save(document);
    notificatie.tekstExtractieTaakGewijzigd(TekstExtractieTaakDto.van(document));
    LOGGER.error("Tekst-extractie document {} mislukt: {}", documentId, foutmelding);
  }

  /**
   * Controleert of de tekst-extractie voor een bestand afgerond is.
   *
   * @param projectNaam naam van het project
   * @param bestandsnaam naam van het bestand
   * @return true als het document status KLAAR heeft
   */
  public boolean isTekstExtractieKlaar(String projectNaam, String bestandsnaam) {
    return documentRepository
        .findByProjectNaamAndBestandsnaam(projectNaam, bestandsnaam)
        .map(doc -> doc.getTekstExtractieStatus() == TekstExtractieStatus.KLAAR)
        .orElse(false);
  }

  /**
   * Geeft de geextraheerde tekst voor een bestand.
   *
   * @return de tekst, of null als niet beschikbaar
   */
  public String geefGeextraheerdetekst(String projectNaam, String bestandsnaam) {
    var document = documentRepository
        .findByProjectNaamAndBestandsnaam(projectNaam, bestandsnaam)
        .orElse(null);
    if (document == null
        || document.getTekstExtractieStatus() != TekstExtractieStatus.KLAAR) {
      return null;
    }
    try {
      var pad = projectPoort.geefTekstBestandsPad(projectNaam, bestandsnaam);
      return Files.readString(pad);
    } catch (Exception e) {
      LOGGER.error("Kan tekst niet lezen voor {}/{}: {}",
          projectNaam, bestandsnaam, e.getMessage());
      return null;
    }
  }

  /**
   * Herstart een mislukte tekst-extractie door de status terug te zetten naar BEZIG.
   *
   * @param projectNaam naam van het project
   * @param bestandsnaam naam van het bestand
   * @return het bijgewerkte DTO
   * @throws IllegalArgumentException als geen document gevonden wordt
   * @throws IllegalStateException als het document niet in een herstartbare status staat
   */
  @Transactional
  public TekstExtractieTaakDto herstartTekstExtractie(String projectNaam, String bestandsnaam) {
    var document = documentRepository
        .findByProjectNaamAndBestandsnaam(projectNaam, bestandsnaam)
        .orElseThrow(() -> new IllegalArgumentException(
            "Geen document gevonden voor: " + bestandsnaam));

    if (document.getTekstExtractieStatus() != TekstExtractieStatus.FOUT) {
      throw new IllegalStateException(
          "Document kan niet herstart worden vanuit status: "
              + document.getTekstExtractieStatus());
    }

    document.setTekstExtractieStatus(TekstExtractieStatus.BEZIG);
    document.setFoutmelding(null);
    documentRepository.save(document);
    var dto = TekstExtractieTaakDto.van(document);
    notificatie.tekstExtractieTaakGewijzigd(dto);
    LOGGER.info("Tekst-extractie herstart voor document {}/{}", projectNaam, bestandsnaam);
    return dto;
  }

  /**
   * Verwijdert een tekst-extractie taak (reset de status van het document).
   *
   * @param projectNaam naam van het project waartoe het document moet behoren
   * @param documentId id van het document
   * @throws IllegalArgumentException als het document niet bestaat of niet tot het project behoort
   */
  @Transactional
  public void verwijderTaak(String projectNaam, Long documentId) {
    var document = documentRepository.findById(documentId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Document niet gevonden: " + documentId));
    if (!document.getProjectNaam().equals(projectNaam)) {
      throw new IllegalArgumentException(
          "Document behoort niet tot project: " + projectNaam);
    }
    document.setTekstExtractieStatus(TekstExtractieStatus.GEEN);
    document.setFoutmelding(null);
    document.setExtractieMethode(null);
    documentRepository.save(document);
    LOGGER.info("Tekst-extractie gereset voor document {} in project '{}'",
        documentId, projectNaam);
  }
}
