package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "kernbezwaar")
public class KernbezwaarEntiteit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "thema_id", nullable = false)
  private Long themaId;

  @Column(name = "samenvatting", columnDefinition = "text", nullable = false)
  private String samenvatting;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getThemaId() {
    return themaId;
  }

  public void setThemaId(Long themaId) {
    this.themaId = themaId;
  }

  public String getSamenvatting() {
    return samenvatting;
  }

  public void setSamenvatting(String samenvatting) {
    this.samenvatting = samenvatting;
  }
}
