package be.vlaanderen.omgeving.bezwaarschriften.project;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "extractie_passage")
public class ExtractiePassageEntiteit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "taak_id", nullable = false)
  private Long taakId;

  @Column(name = "passage_nr", nullable = false)
  private int passageNr;

  @Column(name = "tekst", columnDefinition = "text", nullable = false)
  private String tekst;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getTaakId() {
    return taakId;
  }

  public void setTaakId(Long taakId) {
    this.taakId = taakId;
  }

  public int getPassageNr() {
    return passageNr;
  }

  public void setPassageNr(int passageNr) {
    this.passageNr = passageNr;
  }

  public String getTekst() {
    return tekst;
  }

  public void setTekst(String tekst) {
    this.tekst = tekst;
  }
}
