package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IndividueelBezwaarRepository extends JpaRepository<IndividueelBezwaar, Long> {

  List<IndividueelBezwaar> findByDocumentId(Long documentId);

  int countByDocumentId(Long documentId);

  void deleteByDocumentId(Long documentId);

  @Query("SELECT b FROM IndividueelBezwaar b JOIN BezwaarDocument d ON b.documentId = d.id "
      + "WHERE d.projectNaam = :projectNaam")
  List<IndividueelBezwaar> findByProjectNaam(@Param("projectNaam") String projectNaam);

  @Query("SELECT COUNT(b) FROM IndividueelBezwaar b JOIN BezwaarDocument d ON b.documentId = d.id "
      + "WHERE d.projectNaam = :projectNaam")
  int countByProjectNaam(@Param("projectNaam") String projectNaam);
}
