package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository voor tekst-extractie taken. */
public interface TekstExtractieTaakRepository extends JpaRepository<TekstExtractieTaak, Long> {

  List<TekstExtractieTaak> findByStatusOrderByAangemaaktOpAsc(TekstExtractieTaakStatus status);

  long countByStatus(TekstExtractieTaakStatus status);

  Optional<TekstExtractieTaak> findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
      String projectNaam, String bestandsnaam);

  List<TekstExtractieTaak> findByProjectNaam(String projectNaam);

  List<TekstExtractieTaak> findByProjectNaamAndStatus(
      String projectNaam, TekstExtractieTaakStatus status);

  void deleteByProjectNaam(String projectNaam);

  void deleteByProjectNaamAndBestandsnaam(String projectNaam, String bestandsnaam);

  void deleteByProjectNaamAndBestandsnaamIn(String projectNaam, List<String> bestandsnamen);
}
