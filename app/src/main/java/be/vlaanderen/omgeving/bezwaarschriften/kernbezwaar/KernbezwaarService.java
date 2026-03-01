package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import be.vlaanderen.omgeving.bezwaarschriften.project.ProjectService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestreert de groepering van individuele bezwaren tot thema's en kernbezwaren.
 */
@Service
public class KernbezwaarService {

  private final KernbezwaarPoort kernbezwaarPoort;
  private final ProjectService projectService;
  private final KernbezwaarAntwoordRepository antwoordRepository;
  private final ThemaRepository themaRepository;
  private final KernbezwaarRepository kernbezwaarRepository;
  private final KernbezwaarReferentieRepository referentieRepository;

  public KernbezwaarService(KernbezwaarPoort kernbezwaarPoort,
      ProjectService projectService,
      KernbezwaarAntwoordRepository antwoordRepository,
      ThemaRepository themaRepository,
      KernbezwaarRepository kernbezwaarRepository,
      KernbezwaarReferentieRepository referentieRepository) {
    this.kernbezwaarPoort = kernbezwaarPoort;
    this.projectService = projectService;
    this.antwoordRepository = antwoordRepository;
    this.themaRepository = themaRepository;
    this.kernbezwaarRepository = kernbezwaarRepository;
    this.referentieRepository = referentieRepository;
  }

  /**
   * Groepeert de individuele bezwaren van een project tot thema's en kernbezwaren.
   *
   * @param projectNaam Naam van het project
   * @return Lijst van thema's met kernbezwaren
   */
  @Transactional
  public List<Thema> groepeer(String projectNaam) {
    var invoer = projectService.geefBezwaartekstenVoorGroepering(projectNaam);
    var themas = kernbezwaarPoort.groepeer(invoer);

    // Verwijder bestaande data (cascade ruimt kernbezwaren, referenties en antwoorden op)
    themaRepository.deleteByProjectNaam(projectNaam);

    // Sla nieuwe thema's op en bouw domain records met DB-IDs
    var resultaat = new ArrayList<Thema>();
    for (var thema : themas) {
      var themaEntiteit = new ThemaEntiteit();
      themaEntiteit.setProjectNaam(projectNaam);
      themaEntiteit.setNaam(thema.naam());
      themaEntiteit = themaRepository.save(themaEntiteit);

      var kernbezwaren = new ArrayList<Kernbezwaar>();
      for (var kern : thema.kernbezwaren()) {
        var kernEntiteit = new KernbezwaarEntiteit();
        kernEntiteit.setThemaId(themaEntiteit.getId());
        kernEntiteit.setSamenvatting(kern.samenvatting());
        kernEntiteit = kernbezwaarRepository.save(kernEntiteit);

        var referenties = new ArrayList<IndividueelBezwaarReferentie>();
        for (var ref : kern.individueleBezwaren()) {
          var refEntiteit = new KernbezwaarReferentieEntiteit();
          refEntiteit.setKernbezwaarId(kernEntiteit.getId());
          refEntiteit.setBezwaarId(ref.bezwaarId());
          refEntiteit.setBestandsnaam(ref.bestandsnaam());
          refEntiteit.setPassage(ref.passage());
          referentieRepository.save(refEntiteit);
          referenties.add(new IndividueelBezwaarReferentie(
              ref.bezwaarId(), ref.bestandsnaam(), ref.passage()));
        }

        kernbezwaren.add(new Kernbezwaar(
            kernEntiteit.getId(), kern.samenvatting(), referenties, null));
      }
      resultaat.add(new Thema(thema.naam(), kernbezwaren));
    }
    return resultaat;
  }

  /**
   * Geeft eerder berekende kernbezwaren voor een project.
   *
   * @param projectNaam Naam van het project
   * @return Optional met de lijst van thema's, of leeg als nog niet gegroepeerd
   */
  public Optional<List<Thema>> geefKernbezwaren(String projectNaam) {
    var themaEntiteiten = themaRepository.findByProjectNaam(projectNaam);
    if (themaEntiteiten.isEmpty()) {
      return Optional.empty();
    }

    var themaIds = themaEntiteiten.stream().map(ThemaEntiteit::getId).toList();
    var kernEntiteiten = kernbezwaarRepository.findByThemaIdIn(themaIds);
    var kernIds = kernEntiteiten.stream().map(KernbezwaarEntiteit::getId).toList();
    var refEntiteiten = referentieRepository.findByKernbezwaarIdIn(kernIds);

    // Groepeer referenties per kernbezwaar
    var refPerKern = refEntiteiten.stream()
        .collect(Collectors.groupingBy(KernbezwaarReferentieEntiteit::getKernbezwaarId));

    // Groepeer kernbezwaren per thema
    var kernPerThema = kernEntiteiten.stream()
        .collect(Collectors.groupingBy(KernbezwaarEntiteit::getThemaId));

    // Assembleer domain records
    var themas = themaEntiteiten.stream()
        .map(te -> {
          var kernen = kernPerThema.getOrDefault(te.getId(), List.of()).stream()
              .map(ke -> {
                var refs = refPerKern.getOrDefault(ke.getId(), List.of()).stream()
                    .map(re -> new IndividueelBezwaarReferentie(
                        re.getBezwaarId(), re.getBestandsnaam(), re.getPassage()))
                    .toList();
                return new Kernbezwaar(ke.getId(), ke.getSamenvatting(), refs, null);
              })
              .toList();
          return new Thema(te.getNaam(), kernen);
        })
        .toList();

    return Optional.of(verrijkMetAntwoorden(themas));
  }

  private List<Thema> verrijkMetAntwoorden(List<Thema> themas) {
    var alleIds = themas.stream()
        .flatMap(t -> t.kernbezwaren().stream())
        .map(Kernbezwaar::id)
        .toList();
    var antwoorden = antwoordRepository.findByKernbezwaarIdIn(alleIds);
    var antwoordMap = antwoorden.stream()
        .collect(Collectors.toMap(
            KernbezwaarAntwoordEntiteit::getKernbezwaarId,
            KernbezwaarAntwoordEntiteit::getInhoud));
    return themas.stream()
        .map(thema -> new Thema(thema.naam(),
            thema.kernbezwaren().stream()
                .map(kern -> new Kernbezwaar(kern.id(), kern.samenvatting(),
                    kern.individueleBezwaren(),
                    antwoordMap.get(kern.id())))
                .toList()))
        .toList();
  }

  /**
   * Slaat een antwoord op voor een kernbezwaar.
   *
   * @param kernbezwaarId ID van het kernbezwaar
   * @param inhoud HTML-inhoud van het antwoord
   */
  public void slaAntwoordOp(Long kernbezwaarId, String inhoud) {
    if (inhoud == null || inhoud.isBlank()) {
      if (antwoordRepository.existsById(kernbezwaarId)) {
        antwoordRepository.deleteById(kernbezwaarId);
      }
      return;
    }
    var entiteit = new KernbezwaarAntwoordEntiteit();
    entiteit.setKernbezwaarId(kernbezwaarId);
    entiteit.setInhoud(inhoud);
    entiteit.setBijgewerktOp(Instant.now());
    antwoordRepository.save(entiteit);
  }
}
