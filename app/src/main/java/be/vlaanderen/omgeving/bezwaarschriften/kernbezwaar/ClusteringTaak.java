package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * JPA-entiteit voor een clustering-taak in de verwerkingsqueue.
 * Elke taak representeert het clusteren van alle bezwaren binnen een project.
 */
@Entity
@Table(name = "clustering_taak")
public class ClusteringTaak {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "project_naam", nullable = false)
  private String projectNaam;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ClusteringTaakStatus status;

  @Column(name = "foutmelding", columnDefinition = "text")
  private String foutmelding;

  @Column(name = "aangemaakt_op", nullable = false)
  private Instant aangemaaktOp;

  @Column(name = "verwerking_gestart_op")
  private Instant verwerkingGestartOp;

  @Column(name = "verwerking_voltooid_op")
  private Instant verwerkingVoltooidOp;

  @Column(name = "deduplicatie_voor_clustering", nullable = false)
  private boolean deduplicatieVoorClustering = false;

  @Version
  @Column(name = "versie", nullable = false)
  private int versie;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getProjectNaam() {
    return projectNaam;
  }

  public void setProjectNaam(String projectNaam) {
    this.projectNaam = projectNaam;
  }

  public ClusteringTaakStatus getStatus() {
    return status;
  }

  public void setStatus(ClusteringTaakStatus status) {
    this.status = status;
  }

  public String getFoutmelding() {
    return foutmelding;
  }

  public void setFoutmelding(String foutmelding) {
    this.foutmelding = foutmelding;
  }

  public Instant getAangemaaktOp() {
    return aangemaaktOp;
  }

  public void setAangemaaktOp(Instant aangemaaktOp) {
    this.aangemaaktOp = aangemaaktOp;
  }

  public Instant getVerwerkingGestartOp() {
    return verwerkingGestartOp;
  }

  public void setVerwerkingGestartOp(Instant verwerkingGestartOp) {
    this.verwerkingGestartOp = verwerkingGestartOp;
  }

  public Instant getVerwerkingVoltooidOp() {
    return verwerkingVoltooidOp;
  }

  public void setVerwerkingVoltooidOp(Instant verwerkingVoltooidOp) {
    this.verwerkingVoltooidOp = verwerkingVoltooidOp;
  }

  public boolean isDeduplicatieVoorClustering() {
    return deduplicatieVoorClustering;
  }

  public void setDeduplicatieVoorClustering(boolean deduplicatieVoorClustering) {
    this.deduplicatieVoorClustering = deduplicatieVoorClustering;
  }

  public int getVersie() {
    return versie;
  }

  public void setVersie(int versie) {
    this.versie = versie;
  }
}
