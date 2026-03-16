package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/** Koppelt een pseudonimisering mapping-ID aan een chunk-volgnummer binnen een extractie-taak. */
@Entity
@Table(name = "pseudonimisering_chunk")
public class PseudonimiseringChunk {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "taak_id", nullable = false)
  private TekstExtractieTaak taak;

  @Column(name = "volgnummer", nullable = false)
  private int volgnummer;

  @Column(name = "mapping_id", nullable = false)
  private String mappingId;

  protected PseudonimiseringChunk() {}

  public PseudonimiseringChunk(TekstExtractieTaak taak, int volgnummer, String mappingId) {
    this.taak = taak;
    this.volgnummer = volgnummer;
    this.mappingId = mappingId;
  }

  public Long getId() {
    return id;
  }

  public TekstExtractieTaak getTaak() {
    return taak;
  }

  public int getVolgnummer() {
    return volgnummer;
  }

  public String getMappingId() {
    return mappingId;
  }
}
