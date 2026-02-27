package be.vlaanderen.omgeving.bezwaarschriften.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "individueel_bezwaar")
public class IndividueelBezwaar {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tekst", columnDefinition = "text")
  private String tekst;

  @Column(name = "embedding", columnDefinition = "text", nullable = true)
  private String vector;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTekst() {
    return tekst;
  }

  public void setTekst(String tekst) {
    this.tekst = tekst;
  }

  public String getVector() {
    return vector;
  }

  public void setVector(String vector) {
    this.vector = vector;
  }
}
