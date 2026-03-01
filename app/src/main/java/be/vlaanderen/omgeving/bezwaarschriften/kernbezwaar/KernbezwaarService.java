package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import be.vlaanderen.omgeving.bezwaarschriften.project.ProjectService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Orchestreert de groepering van individuele bezwaren tot thema's en kernbezwaren.
 */
@Service
public class KernbezwaarService {

  private final KernbezwaarPoort kernbezwaarPoort;
  private final ProjectService projectService;
  private final KernbezwaarAntwoordRepository antwoordRepository;
  private final Map<String, List<Thema>> cache = new ConcurrentHashMap<>();

  public KernbezwaarService(KernbezwaarPoort kernbezwaarPoort,
      ProjectService projectService,
      KernbezwaarAntwoordRepository antwoordRepository) {
    this.kernbezwaarPoort = kernbezwaarPoort;
    this.projectService = projectService;
    this.antwoordRepository = antwoordRepository;
  }

  /**
   * Groepeert de individuele bezwaren van een project tot thema's en kernbezwaren.
   *
   * @param projectNaam Naam van het project
   * @return Lijst van thema's met kernbezwaren
   */
  public List<Thema> groepeer(String projectNaam) {
    var invoer = projectService.geefBezwaartekstenVoorGroepering(projectNaam);
    var themas = kernbezwaarPoort.groepeer(invoer);
    cache.put(projectNaam, themas);
    return themas;
  }

  /**
   * Geeft eerder berekende kernbezwaren voor een project.
   *
   * @param projectNaam Naam van het project
   * @return Optional met de lijst van thema's, of leeg als nog niet gegroepeerd
   */
  public Optional<List<Thema>> geefKernbezwaren(String projectNaam) {
    var themas = cache.get(projectNaam);
    if (themas == null) {
      return Optional.empty();
    }
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
    var entiteit = new KernbezwaarAntwoordEntiteit();
    entiteit.setKernbezwaarId(kernbezwaarId);
    entiteit.setInhoud(inhoud);
    entiteit.setBijgewerktOp(Instant.now());
    antwoordRepository.save(entiteit);
  }
}
