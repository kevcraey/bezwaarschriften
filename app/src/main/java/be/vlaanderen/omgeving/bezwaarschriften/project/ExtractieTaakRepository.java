package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

  /**
   * Vindt alle extractie-taken voor een project met een bepaalde status.
   *
   * @param projectNaam de naam van het project
   * @param status de gewenste status
   * @return lijst van extractie-taken die aan beide criteria voldoen
   */
  List<ExtractieTaak> findByProjectNaamAndStatus(String projectNaam, ExtractieTaakStatus status);

  /**
   * Verwijdert alle extractie-taken voor een project.
   *
   * @param projectNaam de naam van het project
   */
  void deleteByProjectNaam(String projectNaam);

  /**
   * Verwijdert alle extractie-taken voor meerdere bestanden binnen een project via
   * JPQL bulk delete. Voorkomt JPA first-level cache problemen bij herhaalde
   * derived deletes in dezelfde transactie.
   *
   * @param projectNaam de naam van het project
   * @param bestandsnamen de lijst van bestandsnamen
   */
  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM ExtractieTaak t "
      + "WHERE t.projectNaam = :projectNaam "
      + "AND t.bestandsnaam IN :bestandsnamen")
  void deleteByProjectNaamAndBestandsnaamIn(
      @Param("projectNaam") String projectNaam,
      @Param("bestandsnamen") List<String> bestandsnamen);
}
