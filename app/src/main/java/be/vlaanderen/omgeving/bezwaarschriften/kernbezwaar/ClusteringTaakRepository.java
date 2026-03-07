package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository voor {@link ClusteringTaak} entiteiten.
 */
public interface ClusteringTaakRepository extends JpaRepository<ClusteringTaak, Long> {

  /**
   * Vindt de clustering-taak voor een project. Er is maximaal één taak per project.
   *
   * @param projectNaam de naam van het project
   * @return de clustering-taak, indien aanwezig
   */
  Optional<ClusteringTaak> findByProjectNaam(String projectNaam);

  /**
   * Vindt alle clustering-taken met een bepaalde status, gesorteerd op aanmaakdatum (oudste eerst).
   *
   * @param status de gewenste status
   * @return gesorteerde lijst van clustering-taken
   */
  List<ClusteringTaak> findByStatusOrderByAangemaaktOpAsc(ClusteringTaakStatus status);

  /**
   * Telt het aantal clustering-taken met een bepaalde status.
   *
   * @param status de te tellen status
   * @return het aantal taken met die status
   */
  int countByStatus(ClusteringTaakStatus status);

  /**
   * Verwijdert alle clustering-taken voor een project.
   *
   * @param projectNaam de naam van het project
   */
  void deleteByProjectNaam(String projectNaam);
}
