package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "kernbezwaar_referentie")
public class KernbezwaarReferentieEntiteit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "kernbezwaar_id", nullable = false)
  private Long kernbezwaarId;

  @Column(name = "bezwaar_id")
  private Long bezwaarId;

  @Column(name = "bestandsnaam", nullable = false)
  private String bestandsnaam;

  @Column(name = "passage", columnDefinition = "text", nullable = false)
  private String passage;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getKernbezwaarId() {
    return kernbezwaarId;
  }

  public void setKernbezwaarId(Long kernbezwaarId) {
    this.kernbezwaarId = kernbezwaarId;
  }

  public Long getBezwaarId() {
    return bezwaarId;
  }

  public void setBezwaarId(Long bezwaarId) {
    this.bezwaarId = bezwaarId;
  }

  public String getBestandsnaam() {
    return bestandsnaam;
  }

  public void setBestandsnaam(String bestandsnaam) {
    this.bestandsnaam = bestandsnaam;
  }

  public String getPassage() {
    return passage;
  }

  public void setPassage(String passage) {
    this.passage = passage;
  }
}
