package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository voor {@link BezwaarDocument} entiteiten.
 */
@Repository
public interface BezwaarDocumentRepository extends JpaRepository<BezwaarDocument, Long> {

  List<BezwaarDocument> findByProjectNaam(String projectNaam);

  Optional<BezwaarDocument> findByProjectNaamAndBestandsnaam(String projectNaam,
      String bestandsnaam);

  int countByProjectNaam(String projectNaam);

  List<BezwaarDocument> findByTekstExtractieStatus(TekstExtractieStatus status);

  List<BezwaarDocument> findByBezwaarExtractieStatus(BezwaarExtractieStatus status);

  int countByTekstExtractieStatus(TekstExtractieStatus status);

  int countByBezwaarExtractieStatus(BezwaarExtractieStatus status);

  void deleteByProjectNaam(String projectNaam);

  void deleteByProjectNaamAndBestandsnaam(String projectNaam, String bestandsnaam);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM BezwaarDocument d "
      + "WHERE d.projectNaam = :projectNaam "
      + "AND d.bestandsnaam IN :bestandsnamen")
  void deleteByProjectNaamAndBestandsnaamIn(
      @Param("projectNaam") String projectNaam,
      @Param("bestandsnamen") List<String> bestandsnamen);
}
