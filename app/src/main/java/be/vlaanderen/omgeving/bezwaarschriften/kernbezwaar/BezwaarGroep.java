package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "bezwaar_groep")
public class BezwaarGroep {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "clustering_taak_id", nullable = false)
  private Long clusteringTaakId;

  @Column(name = "passage", columnDefinition = "text", nullable = false)
  private String passage;

  @Column(name = "samenvatting", columnDefinition = "text", nullable = false)
  private String samenvatting;

  @Column(name = "categorie", length = 50, nullable = false)
  private String categorie;

  @Column(name = "score_percentage")
  private Integer scorePercentage;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getClusteringTaakId() {
    return clusteringTaakId;
  }

  public void setClusteringTaakId(Long clusteringTaakId) {
    this.clusteringTaakId = clusteringTaakId;
  }

  public String getPassage() {
    return passage;
  }

  public void setPassage(String passage) {
    this.passage = passage;
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

  public Integer getScorePercentage() {
    return scorePercentage;
  }

  public void setScorePercentage(Integer scorePercentage) {
    this.scorePercentage = scorePercentage;
  }
}
