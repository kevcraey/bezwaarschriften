package be.vlaanderen.omgeving.bezwaarschriften.project;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "geextraheerd_bezwaar")
public class GeextraheerdBezwaarEntiteit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "taak_id", nullable = false)
  private Long taakId;

  @Column(name = "passage_nr", nullable = false)
  private int passageNr;

  @Column(name = "samenvatting", columnDefinition = "text", nullable = false)
  private String samenvatting;

  @Column(name = "categorie", length = 50, nullable = false)
  private String categorie;

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

  public String getSamenvatting() {
    return samenvatting;
  }

  public void setSamenvatting(String samenvatting) {
    this.samenvatting = samenvatting;
  }

  public String getCategorie() {
    return categorie;
  }

  public void setCategorie(String categorie) {
    this.categorie = categorie;
  }
}
