package be.vlaanderen.omgeving.bezwaarschriften.project;

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
 * JPA-entiteit voor een bezwaardocument binnen een project.
 */
@Entity
@Table(name = "bezwaar_document")
public class BezwaarDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "project_naam", nullable = false)
  private String projectNaam;

  @Column(name = "bestandsnaam", nullable = false)
  private String bestandsnaam;

  @Enumerated(EnumType.STRING)
  @Column(name = "tekst_extractie_status", nullable = false)
  private TekstExtractieStatus tekstExtractieStatus = TekstExtractieStatus.GEEN;

  @Enumerated(EnumType.STRING)
  @Column(name = "bezwaar_extractie_status", nullable = false)
  private BezwaarExtractieStatus bezwaarExtractieStatus = BezwaarExtractieStatus.GEEN;

  @Column(name = "extractie_methode")
  private String extractieMethode;

  @Column(name = "aantal_woorden")
  private Integer aantalWoorden;

  @Column(name = "heeft_passages_die_niet_in_tekst_voorkomen", nullable = false)
  private boolean heeftPassagesDieNietInTekstVoorkomen;

  @Column(name = "heeft_manueel", nullable = false)
  private boolean heeftManueel;

  @Column(name = "foutmelding", columnDefinition = "text")
  private String foutmelding;

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

  public TekstExtractieStatus getTekstExtractieStatus() {
    return tekstExtractieStatus;
  }

  public void setTekstExtractieStatus(TekstExtractieStatus tekstExtractieStatus) {
    this.tekstExtractieStatus = tekstExtractieStatus;
  }

  public BezwaarExtractieStatus getBezwaarExtractieStatus() {
    return bezwaarExtractieStatus;
  }

  public void setBezwaarExtractieStatus(BezwaarExtractieStatus bezwaarExtractieStatus) {
    this.bezwaarExtractieStatus = bezwaarExtractieStatus;
  }

  public String getExtractieMethode() {
    return extractieMethode;
  }

  public void setExtractieMethode(String extractieMethode) {
    this.extractieMethode = extractieMethode;
  }

  public Integer getAantalWoorden() {
    return aantalWoorden;
  }

  public void setAantalWoorden(Integer aantalWoorden) {
    this.aantalWoorden = aantalWoorden;
  }

  public boolean isHeeftPassagesDieNietInTekstVoorkomen() {
    return heeftPassagesDieNietInTekstVoorkomen;
  }

  public void setHeeftPassagesDieNietInTekstVoorkomen(boolean heeftPassagesDieNietInTekstVoorkomen) {
    this.heeftPassagesDieNietInTekstVoorkomen = heeftPassagesDieNietInTekstVoorkomen;
  }

  public boolean isHeeftManueel() {
    return heeftManueel;
  }

  public void setHeeftManueel(boolean heeftManueel) {
    this.heeftManueel = heeftManueel;
  }

  public String getFoutmelding() {
    return foutmelding;
  }

  public void setFoutmelding(String foutmelding) {
    this.foutmelding = foutmelding;
  }

  public int getVersie() {
    return versie;
  }

  public void setVersie(int versie) {
    this.versie = versie;
  }

  // --- Domeinmethoden voor status-transities ---

  /**
   * Start de tekst-extractie voor dit document.
   */
  public void startTekstExtractie() {
    this.tekstExtractieStatus = TekstExtractieStatus.BEZIG;
    this.foutmelding = null;
  }

  /**
   * Markeert tekst-extractie als succesvol afgerond.
   *
   * @param extractieMethode de gebruikte extractiemethode
   */
  public void voltooiTekstExtractie(String extractieMethode) {
    this.tekstExtractieStatus = TekstExtractieStatus.KLAAR;
    this.extractieMethode = extractieMethode;
  }

  /**
   * Markeert tekst-extractie als mislukt.
   *
   * @param foutmelding omschrijving van de fout
   */
  public void markeerTekstExtractieFout(String foutmelding) {
    this.tekstExtractieStatus = TekstExtractieStatus.FOUT;
    this.foutmelding = foutmelding;
  }

  /**
   * Reset de tekst-extractie status naar GEEN.
   */
  public void resetTekstExtractie() {
    this.tekstExtractieStatus = TekstExtractieStatus.GEEN;
    this.foutmelding = null;
    this.extractieMethode = null;
  }

  /**
   * Start de bezwaar-extractie voor dit document.
   */
  public void startBezwaarExtractie() {
    this.bezwaarExtractieStatus = BezwaarExtractieStatus.BEZIG;
    this.foutmelding = null;
  }

  /**
   * Markeert bezwaar-extractie als succesvol afgerond.
   *
   * @param heeftPassagesDieNietInTekstVoorkomen of er passages zijn die niet in de tekst voorkomen
   * @param heeftManueel of er manueel toegevoegde bezwaren zijn
   */
  public void voltooiBezwaarExtractie(boolean heeftPassagesDieNietInTekstVoorkomen,
      boolean heeftManueel) {
    this.bezwaarExtractieStatus = BezwaarExtractieStatus.KLAAR;
    this.heeftPassagesDieNietInTekstVoorkomen = heeftPassagesDieNietInTekstVoorkomen;
    this.heeftManueel = heeftManueel;
  }

  /**
   * Markeert bezwaar-extractie als mislukt.
   *
   * @param foutmelding omschrijving van de fout
   */
  public void markeerBezwaarExtractieFout(String foutmelding) {
    this.bezwaarExtractieStatus = BezwaarExtractieStatus.FOUT;
    this.foutmelding = foutmelding;
  }

  /**
   * Reset de bezwaar-extractie status naar GEEN.
   */
  public void resetBezwaarExtractie() {
    this.bezwaarExtractieStatus = BezwaarExtractieStatus.GEEN;
    this.heeftPassagesDieNietInTekstVoorkomen = false;
    this.heeftManueel = false;
  }
}
