package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GeextraheerdBezwaarRepository
    extends JpaRepository<GeextraheerdBezwaarEntiteit, Long> {

  List<GeextraheerdBezwaarEntiteit> findByTaakId(Long taakId);

  List<GeextraheerdBezwaarEntiteit> findByProjectNaam(String projectNaam);

  int countByProjectNaam(String projectNaam);

  List<GeextraheerdBezwaarEntiteit> findByProjectNaamAndBestandsnaam(
      String projectNaam, String bestandsnaam);

  void deleteByProjectNaamAndBestandsnaam(
      String projectNaam, String bestandsnaam);
}
