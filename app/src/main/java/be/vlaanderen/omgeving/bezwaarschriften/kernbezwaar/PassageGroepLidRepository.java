package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PassageGroepLidRepository extends JpaRepository<PassageGroepLidEntiteit, Long> {

  List<PassageGroepLidEntiteit> findByPassageGroepId(Long passageGroepId);

  List<PassageGroepLidEntiteit> findByPassageGroepIdIn(List<Long> passageGroepIds);

  void deleteByBezwaarIdIn(List<Long> bezwaarIds);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM PassageGroepLidEntiteit l WHERE l.bestandsnaam IN :bestandsnamen "
      + "AND l.passageGroepId IN (SELECT g.id FROM BezwaarGroep g "
      + "JOIN ClusteringTaak t ON g.clusteringTaakId = t.id "
      + "WHERE t.projectNaam = :projectNaam)")
  void deleteByBestandsnaamInAndProjectNaam(
      @Param("bestandsnamen") List<String> bestandsnamen,
      @Param("projectNaam") String projectNaam);
}
