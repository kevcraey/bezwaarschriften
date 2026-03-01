package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KernbezwaarRepository extends JpaRepository<KernbezwaarEntiteit, Long> {

  List<KernbezwaarEntiteit> findByThemaIdIn(List<Long> themaIds);
}
