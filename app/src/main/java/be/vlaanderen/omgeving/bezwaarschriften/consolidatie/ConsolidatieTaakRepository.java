package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConsolidatieTaakRepository extends JpaRepository<ConsolidatieTaak, Long> {

  List<ConsolidatieTaak> findByProjectNaam(String projectNaam);

  long countByStatus(ConsolidatieTaakStatus status);

  List<ConsolidatieTaak> findByStatusOrderByAangemaaktOpAsc(ConsolidatieTaakStatus status);

  Optional<ConsolidatieTaak> findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
      String projectNaam, String bestandsnaam);

  List<ConsolidatieTaak> findByProjectNaamAndStatus(
      String projectNaam, ConsolidatieTaakStatus status);

  List<ConsolidatieTaak> findByProjectNaamAndBestandsnaamInAndStatus(
      String projectNaam, Collection<String> bestandsnamen, ConsolidatieTaakStatus status);

  void deleteByProjectNaam(String projectNaam);
}
