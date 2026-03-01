package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KernbezwaarReferentieRepository extends JpaRepository<KernbezwaarReferentieEntiteit, Long> {

  List<KernbezwaarReferentieEntiteit> findByKernbezwaarIdIn(List<Long> kernbezwaarIds);
}
