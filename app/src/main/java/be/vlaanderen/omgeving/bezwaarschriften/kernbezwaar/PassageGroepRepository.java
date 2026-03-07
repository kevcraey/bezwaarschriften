package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PassageGroepRepository extends JpaRepository<PassageGroepEntiteit, Long> {

  List<PassageGroepEntiteit> findByClusteringTaakId(Long clusteringTaakId);

  void deleteByClusteringTaakId(Long clusteringTaakId);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM PassageGroepEntiteit g WHERE g.id NOT IN "
      + "(SELECT DISTINCT l.passageGroepId FROM PassageGroepLidEntiteit l)")
  void deleteZonderLeden();
}
