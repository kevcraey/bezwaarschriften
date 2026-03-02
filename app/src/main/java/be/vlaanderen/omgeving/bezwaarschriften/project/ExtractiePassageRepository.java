package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExtractiePassageRepository
    extends JpaRepository<ExtractiePassageEntiteit, Long> {

  List<ExtractiePassageEntiteit> findByTaakId(Long taakId);
}
