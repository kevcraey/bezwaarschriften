package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConsolidatieTaakRepository extends JpaRepository<ConsolidatieTaak, Long> {

  List<ConsolidatieTaak> findByDocumentIdIn(Collection<Long> documentIds);

  long countByStatus(ConsolidatieTaakStatus status);

  List<ConsolidatieTaak> findByStatusOrderByAangemaaktOpAsc(ConsolidatieTaakStatus status);

  List<ConsolidatieTaak> findByDocumentIdAndStatus(Long documentId,
      ConsolidatieTaakStatus status);

  List<ConsolidatieTaak> findByDocumentIdInAndStatus(Collection<Long> documentIds,
      ConsolidatieTaakStatus status);

  @Query("SELECT t FROM ConsolidatieTaak t "
      + "JOIN be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocument d "
      + "ON t.documentId = d.id "
      + "WHERE d.projectNaam = :projectNaam")
  List<ConsolidatieTaak> findByProjectNaam(@Param("projectNaam") String projectNaam);

  @Modifying
  @Query("DELETE FROM ConsolidatieTaak t WHERE t.documentId IN "
      + "(SELECT d.id FROM be.vlaanderen.omgeving.bezwaarschriften.project.BezwaarDocument d "
      + "WHERE d.projectNaam = :projectNaam)")
  void deleteByProjectNaam(@Param("projectNaam") String projectNaam);
}
