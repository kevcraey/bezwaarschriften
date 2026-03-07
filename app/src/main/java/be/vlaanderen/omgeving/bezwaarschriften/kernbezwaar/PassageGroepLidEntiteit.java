package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "passage_groep_lid")
public class PassageGroepLidEntiteit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "passage_groep_id", nullable = false)
  private Long passageGroepId;

  @Column(name = "bezwaar_id", nullable = false)
  private Long bezwaarId;

  @Column(name = "bestandsnaam", nullable = false)
  private String bestandsnaam;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getPassageGroepId() {
    return passageGroepId;
  }

  public void setPassageGroepId(Long passageGroepId) {
    this.passageGroepId = passageGroepId;
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
}
