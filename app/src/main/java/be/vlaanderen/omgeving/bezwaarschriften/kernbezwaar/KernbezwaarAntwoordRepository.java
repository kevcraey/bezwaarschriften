package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KernbezwaarAntwoordRepository
    extends JpaRepository<KernbezwaarAntwoordEntiteit, Long> {

  List<KernbezwaarAntwoordEntiteit> findByKernbezwaarIdIn(List<Long> kernbezwaarIds);

  @Query("SELECT a.kernbezwaarId FROM KernbezwaarAntwoordEntiteit a WHERE a.kernbezwaarId IN :ids")
  List<Long> findKernbezwaarIdsMetAntwoord(@Param("ids") List<Long> kernbezwaarIds);

  long countByKernbezwaarIdIn(List<Long> kernbezwaarIds);
}
