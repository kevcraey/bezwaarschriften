package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "bezwaar_groep_lid")
public class BezwaarGroepLid {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "bezwaar_groep_id", nullable = false)
  private Long bezwaarGroepId;

  @Column(name = "bezwaar_id", nullable = false)
  private Long bezwaarId;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getBezwaarGroepId() {
    return bezwaarGroepId;
  }

  public void setBezwaarGroepId(Long bezwaarGroepId) {
    this.bezwaarGroepId = bezwaarGroepId;
  }

  public Long getBezwaarId() {
    return bezwaarId;
  }

  public void setBezwaarId(Long bezwaarId) {
    this.bezwaarId = bezwaarId;
  }
}
