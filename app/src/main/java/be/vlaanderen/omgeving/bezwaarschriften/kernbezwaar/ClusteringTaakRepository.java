package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository voor {@link ClusteringTaak} entiteiten.
 */
public interface ClusteringTaakRepository extends JpaRepository<ClusteringTaak, Long> {

  /**
   * Vindt alle clustering-taken voor een opgegeven projectnaam.
   *
   * @param projectNaam de naam van het project
   * @return lijst van clustering-taken
   */
  List<ClusteringTaak> findByProjectNaam(String projectNaam);

  /**
   * Vindt de clustering-taak voor een specifieke categorie binnen een project.
   *
   * @param projectNaam de naam van het project
   * @param categorie de categorie
   * @return de clustering-taak, indien aanwezig
   */
  Optional<ClusteringTaak> findByProjectNaamAndCategorie(String projectNaam, String categorie);

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

  /**
   * Verwijdert clustering-taken waarvan het corresponderende thema niet meer bestaat.
   * Dit voorkomt dat stale clustering-taken met status "KLAAR" achterblijven
   * na het verwijderen van documenten.
   *
   * @param projectNaam de naam van het project
   */
  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM ClusteringTaak ct "
      + "WHERE ct.projectNaam = :projectNaam "
      + "AND ct.categorie NOT IN ("
      + "  SELECT t.naam FROM ThemaEntiteit t "
      + "  WHERE t.projectNaam = :projectNaam)")
  void deleteZonderThema(@Param("projectNaam") String projectNaam);
}
