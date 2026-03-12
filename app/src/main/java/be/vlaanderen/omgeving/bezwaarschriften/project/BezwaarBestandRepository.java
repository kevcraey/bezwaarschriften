package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BezwaarBestandRepository extends JpaRepository<BezwaarBestandEntiteit, Long> {

  List<BezwaarBestandEntiteit> findByProjectNaam(String projectNaam);

  Optional<BezwaarBestandEntiteit> findByProjectNaamAndBestandsnaam(
      String projectNaam, String bestandsnaam);

  @Modifying
  @Query("DELETE FROM BezwaarBestandEntiteit b WHERE b.projectNaam = :projectNaam")
  void deleteByProjectNaam(@Param("projectNaam") String projectNaam);

  @Modifying
  @Query("DELETE FROM BezwaarBestandEntiteit b "
      + "WHERE b.projectNaam = :projectNaam "
      + "AND b.bestandsnaam = :bestandsnaam")
  void deleteByProjectNaamAndBestandsnaam(
      @Param("projectNaam") String projectNaam,
      @Param("bestandsnaam") String bestandsnaam);

  @Modifying
  @Query("DELETE FROM BezwaarBestandEntiteit b "
      + "WHERE b.projectNaam = :projectNaam "
      + "AND b.bestandsnaam IN :bestandsnamen")
  void deleteByProjectNaamAndBestandsnaamIn(
      @Param("projectNaam") String projectNaam,
      @Param("bestandsnamen") List<String> bestandsnamen);
}
