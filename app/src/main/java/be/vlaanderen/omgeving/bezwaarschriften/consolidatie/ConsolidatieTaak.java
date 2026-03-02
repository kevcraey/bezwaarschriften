package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

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

@Entity
@Table(name = "consolidatie_taak")
public class ConsolidatieTaak {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "project_naam", nullable = false)
  private String projectNaam;

  @Column(name = "bestandsnaam", nullable = false)
  private String bestandsnaam;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ConsolidatieTaakStatus status;

  @Column(name = "aantal_pogingen", nullable = false)
  private int aantalPogingen;

  @Column(name = "max_pogingen", nullable = false)
  private int maxPogingen;

  @Column(name = "foutmelding", columnDefinition = "text")
  private String foutmelding;

  @Column(name = "aangemaakt_op", nullable = false)
  private Instant aangemaaktOp;

  @Column(name = "verwerking_gestart_op")
  private Instant verwerkingGestartOp;

  @Column(name = "afgerond_op")
  private Instant afgerondOp;

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

  public String getBestandsnaam() {
    return bestandsnaam;
  }

  public void setBestandsnaam(String bestandsnaam) {
    this.bestandsnaam = bestandsnaam;
  }

  public ConsolidatieTaakStatus getStatus() {
    return status;
  }

  public void setStatus(ConsolidatieTaakStatus status) {
    this.status = status;
  }

  public int getAantalPogingen() {
    return aantalPogingen;
  }

  public void setAantalPogingen(int aantalPogingen) {
    this.aantalPogingen = aantalPogingen;
  }

  public int getMaxPogingen() {
    return maxPogingen;
  }

  public void setMaxPogingen(int maxPogingen) {
    this.maxPogingen = maxPogingen;
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

  public Instant getAfgerondOp() {
    return afgerondOp;
  }

  public void setAfgerondOp(Instant afgerondOp) {
    this.afgerondOp = afgerondOp;
  }

  public int getVersie() {
    return versie;
  }

  public void setVersie(int versie) {
    this.versie = versie;
  }
}
