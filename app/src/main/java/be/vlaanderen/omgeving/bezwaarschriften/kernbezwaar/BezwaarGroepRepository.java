package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BezwaarGroepRepository extends JpaRepository<BezwaarGroep, Long> {

  List<BezwaarGroep> findByClusteringTaakId(Long clusteringTaakId);

  void deleteByClusteringTaakId(Long clusteringTaakId);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM BezwaarGroep g WHERE g.id NOT IN "
      + "(SELECT DISTINCT l.bezwaarGroepId FROM BezwaarGroepLid l)")
  void deleteZonderLeden();
}
