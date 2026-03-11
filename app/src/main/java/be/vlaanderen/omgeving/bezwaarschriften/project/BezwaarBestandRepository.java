package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface BezwaarBestandRepository extends JpaRepository<BezwaarBestandEntiteit, Long> {

  List<BezwaarBestandEntiteit> findByProjectNaam(String projectNaam);

  Optional<BezwaarBestandEntiteit> findByProjectNaamAndBestandsnaam(
      String projectNaam, String bestandsnaam);

  @Modifying
  void deleteByProjectNaam(String projectNaam);

  @Modifying
  void deleteByProjectNaamAndBestandsnaam(String projectNaam, String bestandsnaam);

  @Modifying
  void deleteByProjectNaamAndBestandsnaamIn(String projectNaam, List<String> bestandsnamen);
}
