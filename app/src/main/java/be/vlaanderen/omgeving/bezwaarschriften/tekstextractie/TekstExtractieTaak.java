package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

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

/** JPA-entiteit voor een tekst-extractie taak in de verwerkingsqueue. */
@Entity
@Table(name = "tekst_extractie_taak")
public class TekstExtractieTaak {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "project_naam", nullable = false)
  private String projectNaam;

  @Column(name = "bestandsnaam", nullable = false)
  private String bestandsnaam;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private TekstExtractieTaakStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "extractie_methode")
  private ExtractieMethode extractieMethode;

  @Column(name = "foutmelding", columnDefinition = "text")
  private String foutmelding;

  @Column(name = "aangemaakt_op", nullable = false)
  private Instant aangemaaktOp;

  @Column(name = "verwerking_gestart_op")
  private Instant verwerkingGestartOp;

  @Column(name = "afgerond_op")
  private Instant afgerondOp;

  @Column(name = "pseudonimisering_mapping_id")
  private String pseudonimiseringMappingId;

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

  public TekstExtractieTaakStatus getStatus() {
    return status;
  }

  public void setStatus(TekstExtractieTaakStatus status) {
    this.status = status;
  }

  public ExtractieMethode getExtractieMethode() {
    return extractieMethode;
  }

  public void setExtractieMethode(ExtractieMethode extractieMethode) {
    this.extractieMethode = extractieMethode;
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

  public String getPseudonimiseringMappingId() {
    return pseudonimiseringMappingId;
  }

  public void setPseudonimiseringMappingId(String pseudonimiseringMappingId) {
    this.pseudonimiseringMappingId = pseudonimiseringMappingId;
  }
}
