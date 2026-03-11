package be.vlaanderen.omgeving.bezwaarschriften.project;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "bezwaar_bestand")
public class BezwaarBestandEntiteit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "project_naam", nullable = false)
  private String projectNaam;

  @Column(name = "bestandsnaam", nullable = false)
  private String bestandsnaam;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private BezwaarBestandStatus status;

  public BezwaarBestandEntiteit() {}

  public BezwaarBestandEntiteit(String projectNaam, String bestandsnaam,
      BezwaarBestandStatus status) {
    this.projectNaam = projectNaam;
    this.bestandsnaam = bestandsnaam;
    this.status = status;
  }

  public Long getId() {
    return id;
  }

  public String getProjectNaam() {
    return projectNaam;
  }

  public String getBestandsnaam() {
    return bestandsnaam;
  }

  public BezwaarBestandStatus getStatus() {
    return status;
  }

  public void setStatus(BezwaarBestandStatus status) {
    this.status = status;
  }
}
