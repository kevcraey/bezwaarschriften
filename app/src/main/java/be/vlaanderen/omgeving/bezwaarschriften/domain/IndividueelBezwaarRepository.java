package be.vlaanderen.omgeving.bezwaarschriften.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IndividueelBezwaarRepository extends JpaRepository<IndividueelBezwaar, Long> {
}
