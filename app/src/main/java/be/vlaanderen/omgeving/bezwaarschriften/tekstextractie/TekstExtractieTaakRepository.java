package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository voor tekst-extractie taken. */
public interface TekstExtractieTaakRepository extends JpaRepository<TekstExtractieTaak, Long> {

  List<TekstExtractieTaak> findByStatusOrderByAangemaaktOpAsc(TekstExtractieTaakStatus status);

  long countByStatus(TekstExtractieTaakStatus status);

  Optional<TekstExtractieTaak> findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
      String projectNaam, String bestandsnaam);

  List<TekstExtractieTaak> findByProjectNaam(String projectNaam);

  List<TekstExtractieTaak> findByProjectNaamAndStatus(
      String projectNaam, TekstExtractieTaakStatus status);

  @Modifying
  @Query("DELETE FROM TekstExtractieTaak t WHERE t.projectNaam = :projectNaam")
  void deleteByProjectNaam(@Param("projectNaam") String projectNaam);

  @Modifying
  @Query("DELETE FROM TekstExtractieTaak t "
      + "WHERE t.projectNaam = :projectNaam "
      + "AND t.bestandsnaam = :bestandsnaam")
  void deleteByProjectNaamAndBestandsnaam(
      @Param("projectNaam") String projectNaam,
      @Param("bestandsnaam") String bestandsnaam);

  @Modifying
  @Query("DELETE FROM TekstExtractieTaak t "
      + "WHERE t.projectNaam = :projectNaam "
      + "AND t.bestandsnaam IN :bestandsnamen")
  void deleteByProjectNaamAndBestandsnaamIn(
      @Param("projectNaam") String projectNaam,
      @Param("bestandsnamen") List<String> bestandsnamen);
}
