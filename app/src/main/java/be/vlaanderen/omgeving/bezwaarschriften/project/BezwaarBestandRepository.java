package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BezwaarBestandRepository extends JpaRepository<BezwaarBestandEntiteit, Long> {

  List<BezwaarBestandEntiteit> findByProjectNaam(String projectNaam);

  Optional<BezwaarBestandEntiteit> findByProjectNaamAndBestandsnaam(
      String projectNaam, String bestandsnaam);

  void deleteByProjectNaam(String projectNaam);

  void deleteByProjectNaamAndBestandsnaam(String projectNaam, String bestandsnaam);

  void deleteByProjectNaamAndBestandsnaamIn(String projectNaam, List<String> bestandsnamen);
}
