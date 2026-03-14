package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Repository voor pseudonimisering chunk mapping-ID's. */
public interface PseudonimiseringChunkRepository
    extends JpaRepository<PseudonimiseringChunk, Long> {

  List<PseudonimiseringChunk> findByTaakIdOrderByVolgnummerAsc(Long taakId);

  @Modifying
  @Query("DELETE FROM PseudonimiseringChunk c WHERE c.taak.id = :taakId")
  void deleteByTaakId(Long taakId);
}
