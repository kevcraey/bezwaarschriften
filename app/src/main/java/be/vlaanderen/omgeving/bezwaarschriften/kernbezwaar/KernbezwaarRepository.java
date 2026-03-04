package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KernbezwaarRepository extends JpaRepository<KernbezwaarEntiteit, Long> {

  List<KernbezwaarEntiteit> findByThemaIdIn(List<Long> themaIds);

  List<KernbezwaarEntiteit> findByThemaId(Long themaId);

  int countByThemaId(Long themaId);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM KernbezwaarEntiteit k "
      + "WHERE k.themaId IN ("
      + "  SELECT t.id FROM ThemaEntiteit t WHERE t.projectNaam = :projectNaam) "
      + "AND k.id NOT IN ("
      + "  SELECT DISTINCT r.kernbezwaarId FROM KernbezwaarReferentieEntiteit r)")
  void deleteZonderReferenties(@Param("projectNaam") String projectNaam);
}
