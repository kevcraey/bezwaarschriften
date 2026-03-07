package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
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

  @Column(name = "passage_groep_id", nullable = false)
  private Long passageGroepId;

  @Enumerated(EnumType.STRING)
  @Column(name = "toewijzingsmethode", nullable = false, length = 20)
  private ToewijzingsMethode toewijzingsmethode = ToewijzingsMethode.HDBSCAN;

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

  public Long getPassageGroepId() {
    return passageGroepId;
  }

  public void setPassageGroepId(Long passageGroepId) {
    this.passageGroepId = passageGroepId;
  }

  public ToewijzingsMethode getToewijzingsmethode() {
    return toewijzingsmethode;
  }

  public void setToewijzingsmethode(ToewijzingsMethode toewijzingsmethode) {
    this.toewijzingsmethode = toewijzingsmethode;
  }
}
