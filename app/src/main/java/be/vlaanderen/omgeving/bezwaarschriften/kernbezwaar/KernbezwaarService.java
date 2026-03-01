package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import be.vlaanderen.omgeving.bezwaarschriften.project.ProjectService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Orchestreert de groepering van individuele bezwaren tot thema's en kernbezwaren.
 */
@Service
public class KernbezwaarService {

  private final KernbezwaarPoort kernbezwaarPoort;
  private final ProjectService projectService;
  private final Map<String, List<Thema>> cache = new ConcurrentHashMap<>();

  public KernbezwaarService(KernbezwaarPoort kernbezwaarPoort,
      ProjectService projectService) {
    this.kernbezwaarPoort = kernbezwaarPoort;
    this.projectService = projectService;
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
    return Optional.ofNullable(cache.get(projectNaam));
  }
}
