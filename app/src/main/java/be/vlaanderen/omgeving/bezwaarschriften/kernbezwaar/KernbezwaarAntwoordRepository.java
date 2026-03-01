package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KernbezwaarAntwoordRepository
    extends JpaRepository<KernbezwaarAntwoordEntiteit, Long> {

  List<KernbezwaarAntwoordEntiteit> findByKernbezwaarIdIn(List<Long> kernbezwaarIds);
}
