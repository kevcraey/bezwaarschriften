package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BezwaarGroepLidRepository extends JpaRepository<BezwaarGroepLid, Long> {

  List<BezwaarGroepLid> findByBezwaarGroepId(Long bezwaarGroepId);

  List<BezwaarGroepLid> findByBezwaarGroepIdIn(List<Long> bezwaarGroepIds);

  void deleteByBezwaarIdIn(List<Long> bezwaarIds);
}
