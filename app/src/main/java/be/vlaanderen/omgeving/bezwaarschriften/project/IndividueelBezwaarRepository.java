package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IndividueelBezwaarRepository extends JpaRepository<IndividueelBezwaar, Long> {

  List<IndividueelBezwaar> findByDocumentId(Long documentId);

  int countByDocumentId(Long documentId);

  @Query("SELECT b.documentId, COUNT(b) FROM IndividueelBezwaar b "
      + "WHERE b.documentId IN :documentIds GROUP BY b.documentId")
  List<Object[]> countByDocumentIdIn(@Param("documentIds") Collection<Long> documentIds);

  void deleteByDocumentId(Long documentId);

  @Query("SELECT b FROM IndividueelBezwaar b JOIN BezwaarDocument d ON b.documentId = d.id "
      + "WHERE d.projectNaam = :projectNaam")
  List<IndividueelBezwaar> findByProjectNaam(@Param("projectNaam") String projectNaam);

  @Query("SELECT COUNT(b) FROM IndividueelBezwaar b JOIN BezwaarDocument d ON b.documentId = d.id "
      + "WHERE d.projectNaam = :projectNaam")
  int countByProjectNaam(@Param("projectNaam") String projectNaam);

  @Query("SELECT b.id FROM IndividueelBezwaar b "
      + "JOIN BezwaarDocument d ON b.documentId = d.id "
      + "WHERE d.projectNaam = :projectNaam AND d.bestandsnaam IN :bestandsnamen")
  List<Long> findIdsByProjectNaamAndBestandsnamen(
      @Param("projectNaam") String projectNaam,
      @Param("bestandsnamen") Collection<String> bestandsnamen);
}
