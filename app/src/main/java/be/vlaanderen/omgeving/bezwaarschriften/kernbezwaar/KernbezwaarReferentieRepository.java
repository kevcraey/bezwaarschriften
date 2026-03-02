package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KernbezwaarReferentieRepository extends JpaRepository<KernbezwaarReferentieEntiteit, Long> {

  List<KernbezwaarReferentieEntiteit> findByKernbezwaarIdIn(List<Long> kernbezwaarIds);

  @Query("SELECT r FROM KernbezwaarReferentieEntiteit r "
      + "JOIN KernbezwaarEntiteit k ON r.kernbezwaarId = k.id "
      + "JOIN ThemaEntiteit t ON k.themaId = t.id "
      + "WHERE t.projectNaam = :projectNaam")
  List<KernbezwaarReferentieEntiteit> findByProjectNaam(@Param("projectNaam") String projectNaam);
}
