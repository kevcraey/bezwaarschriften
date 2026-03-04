package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ThemaRepository extends JpaRepository<ThemaEntiteit, Long> {

  List<ThemaEntiteit> findByProjectNaam(String projectNaam);

  Optional<ThemaEntiteit> findByProjectNaamAndNaam(String projectNaam, String naam);

  void deleteByProjectNaam(String projectNaam);

  void deleteByProjectNaamAndNaam(String projectNaam, String naam);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM ThemaEntiteit t "
      + "WHERE t.projectNaam = :projectNaam "
      + "AND t.id NOT IN ("
      + "  SELECT DISTINCT k.themaId FROM KernbezwaarEntiteit k)")
  void deleteZonderKernbezwaren(@Param("projectNaam") String projectNaam);
}
