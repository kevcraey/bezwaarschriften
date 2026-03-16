package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarBestandRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarBestandStatus;
import be.vlaanderen.omgeving.bezwaarschriften.project.ProjectPoort;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.time.Instant;
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
 */
@Service
public class TekstExtractieService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final TekstExtractieTaakRepository repository;
  private final PdfTekstExtractor pdfExtractor;
  private final TekstKwaliteitsControle kwaliteitsControle;
  private final ProjectPoort projectPoort;
  private final PseudonimiseringPoort pseudonimiseringPoort;
  private final BezwaarBestandRepository bezwaarBestandRepository;
  private final TekstExtractieNotificatie notificatie;
  private final PseudonimiseringChunker chunker;
  private final PseudonimiseringChunkRepository chunkRepository;
  private final int maxConcurrent;

  /**
   * Maakt een nieuwe TekstExtractieService aan.
   *
   * @param repository repository voor tekst-extractie taken
   * @param pdfExtractor extractor voor PDF-bestanden
   * @param kwaliteitsControle kwaliteitscontrole voor geextraheerde tekst
   * @param projectPoort poort voor projectbestanden
   * @param pseudonimiseringPoort poort voor pseudonimisering van tekst
   * @param bezwaarBestandRepository repository voor bezwaarbestand-entiteiten
   * @param notificatie notificatie-interface voor statuswijzigingen
   * @param chunker chunker voor opsplitsing van grote teksten
   * @param chunkRepository repository voor pseudonimisering chunk mapping-ID's
   * @param maxConcurrent maximum aantal gelijktijdig verwerkbare taken
   */
  public TekstExtractieService(
      TekstExtractieTaakRepository repository,
      PdfTekstExtractor pdfExtractor,
      TekstKwaliteitsControle kwaliteitsControle,
      ProjectPoort projectPoort,
      PseudonimiseringPoort pseudonimiseringPoort,
      BezwaarBestandRepository bezwaarBestandRepository,
      TekstExtractieNotificatie notificatie,
      PseudonimiseringChunker chunker,
      PseudonimiseringChunkRepository chunkRepository,
      @Value("${bezwaarschriften.tekst-extractie.max-concurrent:2}") int maxConcurrent) {
    this.repository = repository;
    this.pdfExtractor = pdfExtractor;
    this.kwaliteitsControle = kwaliteitsControle;
    this.projectPoort = projectPoort;
    this.pseudonimiseringPoort = pseudonimiseringPoort;
    this.bezwaarBestandRepository = bezwaarBestandRepository;
    this.notificatie = notificatie;
    this.chunker = chunker;
    this.chunkRepository = chunkRepository;
    this.maxConcurrent = maxConcurrent;
  }

  /**
   * Dient een nieuwe tekst-extractie taak in met status WACHTEND.
   *
   * @param projectNaam naam van het project
   * @param bestandsnaam naam van het bestand
   * @return de aangemaakte taak
   */
  @Transactional
  public TekstExtractieTaak indienen(String projectNaam, String bestandsnaam) {
    var taak = new TekstExtractieTaak();
    taak.setProjectNaam(projectNaam);
    taak.setBestandsnaam(bestandsnaam);
    taak.setStatus(TekstExtractieTaakStatus.WACHTEND);
    taak.setAangemaaktOp(Instant.now());
    var opgeslagen = repository.save(taak);
    werkBestandStatusBij(projectNaam, bestandsnaam,
        BezwaarBestandStatus.TEKST_EXTRACTIE_WACHTEND);
    notificatie.tekstExtractieTaakGewijzigd(TekstExtractieTaakDto.van(opgeslagen));
    LOGGER.info("Tekst-extractie taak ingediend: project='{}', bestand='{}'",
        projectNaam, bestandsnaam);
    return opgeslagen;
  }

  /**
   * Pakt wachtende taken op voor verwerking tot het maximum aantal gelijktijdige taken.
   *
   * <p>Berekent het aantal beschikbare slots op basis van het aantal taken met status BEZIG
   * en het geconfigureerde maximum. Zet de status van opgepakte taken op BEZIG.
   *
   * @return lijst van opgepakte taken, leeg als geen slots beschikbaar
   */
  @Transactional
  public List<TekstExtractieTaak> pakOpVoorVerwerking() {
    long aantalBezig = repository.countByStatus(TekstExtractieTaakStatus.BEZIG);
    int beschikbareSlots = maxConcurrent - (int) aantalBezig;

    if (beschikbareSlots <= 0) {
      LOGGER.debug("Geen beschikbare slots (bezig={}, max={})", aantalBezig, maxConcurrent);
      return List.of();
    }

    var wachtend = repository
        .findByStatusOrderByAangemaaktOpAsc(TekstExtractieTaakStatus.WACHTEND);
    var opTePakken = wachtend.stream().limit(beschikbareSlots).toList();

    for (var taak : opTePakken) {
      taak.setStatus(TekstExtractieTaakStatus.BEZIG);
      taak.setVerwerkingGestartOp(Instant.now());
      repository.save(taak);
      werkBestandStatusBij(taak.getProjectNaam(), taak.getBestandsnaam(),
          BezwaarBestandStatus.TEKST_EXTRACTIE_BEZIG);
      notificatie.tekstExtractieTaakGewijzigd(TekstExtractieTaakDto.van(taak));
      LOGGER.info("Tekst-extractie taak {} opgepakt: project='{}', bestand='{}'",
          taak.getId(), taak.getProjectNaam(), taak.getBestandsnaam());
    }

    return opTePakken;
  }

  /**
   * Verwerkt een tekst-extractie taak.
   *
   * <p>Voor PDF-bestanden wordt {@link PdfTekstExtractor} gebruikt.
   * Voor txt-bestanden wordt de tekst direct ingelezen met kwaliteitscontrole.
   * De geextraheerde tekst wordt opgeslagen via {@link ProjectPoort#slaTekstOp}.
   *
   * @param taak de te verwerken taak
   */
  public void verwerkTaak(TekstExtractieTaak taak) {
    try {
      var pad = projectPoort.geefBestandsPad(taak.getProjectNaam(), taak.getBestandsnaam());
      var bestandsnaam = taak.getBestandsnaam().toLowerCase();

      if (bestandsnaam.endsWith(".pdf")) {
        var resultaat = pdfExtractor.extraheer(pad);
        pseudonimiseerEnSlaOp(taak, resultaat.tekst(), resultaat.methode());
      } else if (bestandsnaam.endsWith(".txt")) {
        var tekst = Files.readString(pad);
        var controle = kwaliteitsControle.controleer(tekst);
        if (!controle.isValide()) {
          markeerMislukt(taak.getId(),
              "Kwaliteitscontrole mislukt: " + controle.reden());
          return;
        }
        pseudonimiseerEnSlaOp(taak, tekst, ExtractieMethode.DIGITAAL);
      } else {
        markeerMislukt(taak.getId(),
            "Niet-ondersteund bestandstype: " + taak.getBestandsnaam());
      }
    } catch (PseudonimiseringException e) {
      LOGGER.error("Pseudonimisering mislukt voor taak {}: {}", taak.getId(), e.getMessage(), e);
      markeerMislukt(taak.getId(), e.getMessage());
    } catch (OcrNietBeschikbaarException e) {
      LOGGER.warn("OCR niet beschikbaar voor taak {}: {}", taak.getId(), e.getMessage());
      markeerOcrNietBeschikbaar(taak.getId(), e.getMessage());
    } catch (IOException e) {
      LOGGER.error("Fout bij verwerking van taak {}: {}", taak.getId(), e.getMessage(), e);
      markeerMislukt(taak.getId(), e.getMessage());
    } catch (Exception e) {
      LOGGER.error("Onverwachte fout bij verwerking van taak {}: {}",
          taak.getId(), e.getMessage(), e);
      markeerMislukt(taak.getId(), e.getMessage());
    }
  }

  private void pseudonimiseerEnSlaOp(TekstExtractieTaak taak, String tekst,
      ExtractieMethode methode) {
    // Ruim eventuele eerdere chunks op (idempotent bij retry)
    chunkRepository.deleteByTaakId(taak.getId());

    var chunks = chunker.chunk(tekst);
    var gepseudonimiseerdeChunks = new ArrayList<String>();

    for (int i = 0; i < chunks.size(); i++) {
      var resultaat = pseudonimiseringPoort.pseudonimiseer(chunks.get(i));
      gepseudonimiseerdeChunks.add(resultaat.gepseudonimiseerdeTekst());
      chunkRepository.save(new PseudonimiseringChunk(taak, i, resultaat.mappingId()));
    }

    var samengevoegd = String.join("\n\n", gepseudonimiseerdeChunks);
    projectPoort.slaTekstOp(taak.getProjectNaam(), taak.getBestandsnaam(), samengevoegd);
    markeerKlaar(taak.getId(), methode);
  }

  /**
   * Markeert een taak als succesvol afgerond.
   *
   * @param taakId id van de taak
   * @param methode de gebruikte extractiemethode
   */
  @Transactional
  public void markeerKlaar(Long taakId, ExtractieMethode methode) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    taak.setStatus(TekstExtractieTaakStatus.KLAAR);
    taak.setExtractieMethode(methode);
    taak.setAfgerondOp(Instant.now());
    repository.save(taak);
    werkBestandStatusBij(taak.getProjectNaam(), taak.getBestandsnaam(),
        BezwaarBestandStatus.TEKST_EXTRACTIE_KLAAR);
    notificatie.tekstExtractieTaakGewijzigd(TekstExtractieTaakDto.van(taak));
    LOGGER.info("Tekst-extractie taak {} afgerond (methode={})", taakId, methode);
  }

  /**
   * Markeert een taak als mislukt.
   *
   * @param taakId id van de taak
   * @param foutmelding beschrijving van de fout
   */
  @Transactional
  public void markeerMislukt(Long taakId, String foutmelding) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    taak.setStatus(TekstExtractieTaakStatus.MISLUKT);
    taak.setFoutmelding(foutmelding);
    taak.setAfgerondOp(Instant.now());
    repository.save(taak);
    werkBestandStatusBij(taak.getProjectNaam(), taak.getBestandsnaam(),
        BezwaarBestandStatus.TEKST_EXTRACTIE_MISLUKT);
    notificatie.tekstExtractieTaakGewijzigd(TekstExtractieTaakDto.van(taak));
    LOGGER.error("Tekst-extractie taak {} mislukt: {}", taakId, foutmelding);
  }

  /**
   * Markeert een taak als OCR niet beschikbaar.
   *
   * @param taakId id van de taak
   * @param foutmelding beschrijving van de fout
   */
  @Transactional
  public void markeerOcrNietBeschikbaar(Long taakId, String foutmelding) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    taak.setStatus(TekstExtractieTaakStatus.OCR_NIET_BESCHIKBAAR);
    taak.setFoutmelding(foutmelding);
    taak.setAfgerondOp(Instant.now());
    repository.save(taak);
    werkBestandStatusBij(taak.getProjectNaam(), taak.getBestandsnaam(),
        BezwaarBestandStatus.TEKST_EXTRACTIE_OCR_NIET_BESCHIKBAAR);
    notificatie.tekstExtractieTaakGewijzigd(TekstExtractieTaakDto.van(taak));
    LOGGER.warn("Tekst-extractie taak {} - OCR niet beschikbaar: {}", taakId, foutmelding);
  }

  /**
   * Controleert of de tekst-extractie voor een bestand afgerond is.
   *
   * @param projectNaam naam van het project
   * @param bestandsnaam naam van het bestand
   * @return true als de meest recente taak status KLAAR heeft
   */
  public boolean isTekstExtractieKlaar(String projectNaam, String bestandsnaam) {
    return repository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(projectNaam, bestandsnaam)
        .map(taak -> taak.getStatus() == TekstExtractieTaakStatus.KLAAR)
        .orElse(false);
  }

  /**
   * Geeft de geextraheerde tekst voor een bestand.
   *
   * @return de tekst, of null als niet beschikbaar
   */
  public String geefGeextraheerdetekst(String projectNaam, String bestandsnaam) {
    var taak = repository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(projectNaam, bestandsnaam)
        .orElse(null);
    if (taak == null || taak.getStatus() != TekstExtractieTaakStatus.KLAAR) {
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
   * Herstart een mislukte tekst-extractie taak door de status terug te zetten naar WACHTEND.
   *
   * @param projectNaam naam van het project
   * @param bestandsnaam naam van het bestand
   * @return het bijgewerkte DTO
   * @throws IllegalArgumentException als geen taak gevonden wordt
   * @throws IllegalStateException als de taak niet in een herstartbare status staat
   */
  @Transactional
  public TekstExtractieTaakDto herstartTekstExtractie(String projectNaam, String bestandsnaam) {
    var taak = repository
        .findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(projectNaam, bestandsnaam)
        .orElseThrow(() -> new IllegalArgumentException(
            "Geen tekst-extractie taak gevonden voor: " + bestandsnaam));

    if (taak.getStatus() != TekstExtractieTaakStatus.MISLUKT
        && taak.getStatus() != TekstExtractieTaakStatus.OCR_NIET_BESCHIKBAAR) {
      throw new IllegalStateException(
          "Taak kan niet herstart worden vanuit status: " + taak.getStatus());
    }

    taak.setStatus(TekstExtractieTaakStatus.WACHTEND);
    taak.setFoutmelding(null);
    taak.setVerwerkingGestartOp(null);
    taak.setAfgerondOp(null);
    repository.save(taak);
    werkBestandStatusBij(projectNaam, bestandsnaam,
        BezwaarBestandStatus.TEKST_EXTRACTIE_WACHTEND);
    var dto = TekstExtractieTaakDto.van(taak);
    notificatie.tekstExtractieTaakGewijzigd(dto);
    LOGGER.info("Tekst-extractie taak {} herstart voor bestand '{}' in project '{}'",
        taak.getId(), bestandsnaam, projectNaam);
    return dto;
  }

  /**
   * Verwijdert een tekst-extractie taak na validatie van het project.
   *
   * @param projectNaam naam van het project waartoe de taak moet behoren
   * @param taakId id van de te verwijderen taak
   * @throws IllegalArgumentException als de taak niet bestaat of niet tot het project behoort
   */
  @Transactional
  public void verwijderTaak(String projectNaam, Long taakId) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    if (!taak.getProjectNaam().equals(projectNaam)) {
      throw new IllegalArgumentException("Taak behoort niet tot project: " + projectNaam);
    }
    repository.delete(taak);
    LOGGER.info("Tekst-extractie taak {} verwijderd voor project '{}'", taakId, projectNaam);
  }

  private void werkBestandStatusBij(String projectNaam, String bestandsnaam,
      BezwaarBestandStatus status) {
    bezwaarBestandRepository.findByProjectNaamAndBestandsnaam(projectNaam, bestandsnaam)
        .ifPresent(entiteit -> {
          entiteit.setStatus(status);
          bezwaarBestandRepository.save(entiteit);
        });
  }
}
