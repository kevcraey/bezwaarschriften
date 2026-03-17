package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/** Koppelt een pseudonimisering mapping-ID aan een chunk-volgnummer binnen een document. */
@Entity
@Table(name = "pseudonimisering_chunk")
public class PseudonimiseringChunk {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "document_id", nullable = false)
  private Long documentId;

  @Column(name = "volgnummer", nullable = false)
  private int volgnummer;

  @Column(name = "mapping_id", nullable = false)
  private String mappingId;

  protected PseudonimiseringChunk() {}

  public PseudonimiseringChunk(Long documentId, int volgnummer, String mappingId) {
    this.documentId = documentId;
    this.volgnummer = volgnummer;
    this.mappingId = mappingId;
  }

  public Long getId() {
    return id;
  }

  public Long getDocumentId() {
    return documentId;
  }

  public int getVolgnummer() {
    return volgnummer;
  }

  public String getMappingId() {
    return mappingId;
  }
}
