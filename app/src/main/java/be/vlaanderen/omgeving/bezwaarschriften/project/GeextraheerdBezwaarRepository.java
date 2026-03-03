package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GeextraheerdBezwaarRepository
    extends JpaRepository<GeextraheerdBezwaarEntiteit, Long> {

  List<GeextraheerdBezwaarEntiteit> findByTaakId(Long taakId);

  @Query("SELECT b FROM GeextraheerdBezwaarEntiteit b "
      + "WHERE b.taakId IN ("
      + "  SELECT t.id FROM ExtractieTaak t "
      + "  WHERE t.projectNaam = :projectNaam AND t.status = 'KLAAR')")
  List<GeextraheerdBezwaarEntiteit> findByProjectNaam(
      @Param("projectNaam") String projectNaam);

  @Query("SELECT COUNT(b) FROM GeextraheerdBezwaarEntiteit b "
      + "WHERE b.categorie = :categorie AND b.taakId IN ("
      + "  SELECT t.id FROM ExtractieTaak t "
      + "  WHERE t.projectNaam = :projectNaam AND t.status = 'KLAAR')")
  int countByProjectNaamAndCategorie(
      @Param("projectNaam") String projectNaam,
      @Param("categorie") String categorie);
}
