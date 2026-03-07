package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassageGroepLidRepository extends JpaRepository<PassageGroepLidEntiteit, Long> {

  List<PassageGroepLidEntiteit> findByPassageGroepId(Long passageGroepId);

  List<PassageGroepLidEntiteit> findByPassageGroepIdIn(List<Long> passageGroepIds);

  void deleteByBezwaarIdIn(List<Long> bezwaarIds);
}
