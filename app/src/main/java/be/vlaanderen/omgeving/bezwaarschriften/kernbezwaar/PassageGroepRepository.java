package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassageGroepRepository extends JpaRepository<PassageGroepEntiteit, Long> {

  List<PassageGroepEntiteit> findByClusteringTaakId(Long clusteringTaakId);

  void deleteByClusteringTaakId(Long clusteringTaakId);
}
