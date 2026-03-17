package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/** Repository voor pseudonimisering chunk mapping-ID's. */
public interface PseudonimiseringChunkRepository
    extends JpaRepository<PseudonimiseringChunk, Long> {

  List<PseudonimiseringChunk> findByDocumentIdOrderByVolgnummerAsc(Long documentId);

  @Modifying
  @Transactional
  @Query("DELETE FROM PseudonimiseringChunk c WHERE c.documentId = :documentId")
  void deleteByDocumentId(Long documentId);
}
