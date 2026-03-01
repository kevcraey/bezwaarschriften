package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "kernbezwaar_antwoord")
public class KernbezwaarAntwoordEntiteit {

  @Id
  @Column(name = "kernbezwaar_id")
  private Long kernbezwaarId;

  @Column(name = "inhoud", columnDefinition = "text", nullable = false)
  private String inhoud;

  @Column(name = "bijgewerkt_op", nullable = false)
  private Instant bijgewerktOp;

  public Long getKernbezwaarId() {
    return kernbezwaarId;
  }

  public void setKernbezwaarId(Long kernbezwaarId) {
    this.kernbezwaarId = kernbezwaarId;
  }

  public String getInhoud() {
    return inhoud;
  }

  public void setInhoud(String inhoud) {
    this.inhoud = inhoud;
  }

  public Instant getBijgewerktOp() {
    return bijgewerktOp;
  }

  public void setBijgewerktOp(Instant bijgewerktOp) {
    this.bijgewerktOp = bijgewerktOp;
  }
}
