package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository voor {@link ExtractieTaak} entiteiten.
 */
@Repository
public interface ExtractieTaakRepository extends JpaRepository<ExtractieTaak, Long> {

  /**
   * Vindt alle extractie-taken voor een opgegeven projectnaam.
   *
   * @param projectNaam de naam van het project
   * @return lijst van extractie-taken
   */
  List<ExtractieTaak> findByProjectNaam(String projectNaam);

  /**
   * Telt het aantal extractie-taken met een bepaalde status.
   *
   * @param status de te tellen status
   * @return het aantal taken met die status
   */
  long countByStatus(ExtractieTaakStatus status);

  /**
   * Vindt alle extractie-taken met een bepaalde status, gesorteerd op aanmaakdatum (oudste eerst).
   *
   * @param status de gewenste status
   * @return gesorteerde lijst van extractie-taken
   */
  List<ExtractieTaak> findByStatusOrderByAangemaaktOpAsc(ExtractieTaakStatus status);

  /**
   * Vindt de meest recente extractie-taak voor een bepaald bestand binnen een project.
   *
   * @param projectNaam de naam van het project
   * @param bestandsnaam de naam van het bestand
   * @return de meest recente taak, indien aanwezig
   */
  Optional<ExtractieTaak> findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
      String projectNaam, String bestandsnaam);

  /**
   * Verwijdert alle extractie-taken voor een bepaald bestand binnen een project.
   *
   * @param projectNaam de naam van het project
   * @param bestandsnaam de naam van het bestand
   */
  void deleteByProjectNaamAndBestandsnaam(String projectNaam, String bestandsnaam);
}
