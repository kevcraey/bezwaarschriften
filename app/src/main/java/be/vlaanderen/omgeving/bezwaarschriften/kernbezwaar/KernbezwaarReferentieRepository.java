package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KernbezwaarReferentieRepository extends JpaRepository<KernbezwaarReferentieEntiteit, Long> {

  List<KernbezwaarReferentieEntiteit> findByKernbezwaarIdIn(List<Long> kernbezwaarIds);

  @Query("SELECT DISTINCT r.bestandsnaam FROM KernbezwaarReferentieEntiteit r "
      + "WHERE r.kernbezwaarId = :kernbezwaarId")
  List<String> findBestandsnamenByKernbezwaarId(@Param("kernbezwaarId") Long kernbezwaarId);

  @Query("SELECT r FROM KernbezwaarReferentieEntiteit r "
      + "JOIN KernbezwaarEntiteit k ON r.kernbezwaarId = k.id "
      + "WHERE k.projectNaam = :projectNaam")
  List<KernbezwaarReferentieEntiteit> findByProjectNaam(@Param("projectNaam") String projectNaam);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM KernbezwaarReferentieEntiteit r "
      + "WHERE r.bestandsnaam = :bestandsnaam "
      + "AND r.kernbezwaarId IN ("
      + "  SELECT k.id FROM KernbezwaarEntiteit k "
      + "  WHERE k.projectNaam = :projectNaam)")
  void deleteByBestandsnaamAndProjectNaam(
      @Param("bestandsnaam") String bestandsnaam,
      @Param("projectNaam") String projectNaam);

  @Query(value = "SELECT kr.kernbezwaar_id, CAST(AVG(gb.embedding_passage) AS text) "
      + "FROM kernbezwaar_referentie kr "
      + "JOIN geextraheerd_bezwaar gb ON kr.bezwaar_id = gb.id "
      + "WHERE kr.kernbezwaar_id IN (:kernIds) "
      + "GROUP BY kr.kernbezwaar_id", nativeQuery = true)
  List<Object[]> berekenCentroidsOpPassage(@Param("kernIds") List<Long> kernIds);

  @Query(value = "SELECT kr.kernbezwaar_id, CAST(AVG(gb.embedding_samenvatting) AS text) "
      + "FROM kernbezwaar_referentie kr "
      + "JOIN geextraheerd_bezwaar gb ON kr.bezwaar_id = gb.id "
      + "WHERE kr.kernbezwaar_id IN (:kernIds) "
      + "GROUP BY kr.kernbezwaar_id", nativeQuery = true)
  List<Object[]> berekenCentroidsOpSamenvatting(@Param("kernIds") List<Long> kernIds);
}
