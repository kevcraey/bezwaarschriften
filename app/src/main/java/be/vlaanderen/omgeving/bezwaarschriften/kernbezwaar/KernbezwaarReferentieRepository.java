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
}
