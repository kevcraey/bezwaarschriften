package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThemaRepository extends JpaRepository<ThemaEntiteit, Long> {

  List<ThemaEntiteit> findByProjectNaam(String projectNaam);

  void deleteByProjectNaam(String projectNaam);
}
