package be.vlaanderen.omgeving.bezwaarschriften.project;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "individueel_bezwaar")
public class IndividueelBezwaar {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "document_id", nullable = false)
  private Long documentId;

  @Column(name = "samenvatting", columnDefinition = "text", nullable = false)
  private String samenvatting;

  @Column(name = "passage_tekst", columnDefinition = "text")
  private String passageTekst;

  @Column(name = "passage_gevonden", nullable = false)
  private boolean passageGevonden = true;

  @Column(name = "manueel", nullable = false)
  private boolean manueel = false;

  @Type(type = "be.vlaanderen.omgeving.bezwaarschriften.config.VectorType")
  @Column(name = "embedding_passage", columnDefinition = "vector(1024)")
  private float[] embeddingPassage;

  @Type(type = "be.vlaanderen.omgeving.bezwaarschriften.config.VectorType")
  @Column(name = "embedding_samenvatting", columnDefinition = "vector(1024)")
  private float[] embeddingSamenvatting;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getDocumentId() {
    return documentId;
  }

  public void setDocumentId(Long documentId) {
    this.documentId = documentId;
  }

  public String getSamenvatting() {
    return samenvatting;
  }

  public void setSamenvatting(String samenvatting) {
    this.samenvatting = samenvatting;
  }

  public String getPassageTekst() {
    return passageTekst;
  }

  public void setPassageTekst(String passageTekst) {
    this.passageTekst = passageTekst;
  }

  public boolean isPassageGevonden() {
    return passageGevonden;
  }

  public void setPassageGevonden(boolean passageGevonden) {
    this.passageGevonden = passageGevonden;
  }

  public boolean isManueel() {
    return manueel;
  }

  public void setManueel(boolean manueel) {
    this.manueel = manueel;
  }

  public float[] getEmbeddingPassage() {
    return embeddingPassage;
  }

  public void setEmbeddingPassage(float[] embeddingPassage) {
    this.embeddingPassage = embeddingPassage;
  }

  public float[] getEmbeddingSamenvatting() {
    return embeddingSamenvatting;
  }

  public void setEmbeddingSamenvatting(float[] embeddingSamenvatting) {
    this.embeddingSamenvatting = embeddingSamenvatting;
  }
}
