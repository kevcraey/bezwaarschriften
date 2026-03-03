package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThemaRepository extends JpaRepository<ThemaEntiteit, Long> {

  List<ThemaEntiteit> findByProjectNaam(String projectNaam);

  Optional<ThemaEntiteit> findByProjectNaamAndNaam(String projectNaam, String naam);

  void deleteByProjectNaam(String projectNaam);

  void deleteByProjectNaamAndNaam(String projectNaam, String naam);
}
