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
  @Query("DELETE FROM PassageGroepLidEntiteit l WHERE l.bestandsnaam IN :bestandsnamen")
  void deleteByBestandsnaamIn(@Param("bestandsnamen") List<String> bestandsnamen);
}
