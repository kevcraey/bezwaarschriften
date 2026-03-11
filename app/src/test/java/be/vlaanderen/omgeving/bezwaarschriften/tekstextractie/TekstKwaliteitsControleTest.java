package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import static org.assertj.core.api.Assertions.assertThat;

import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.TekstKwaliteitsControle.Resultaat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TekstKwaliteitsControleTest {

  private TekstKwaliteitsControle controle;

  @BeforeEach
  void setUp() {
    controle = new TekstKwaliteitsControle();
  }

  @Test
  void valideNederlandseTekstGeeftValideResultaat() {
    String tekst = genereerValideNederlandseTekst();

    Resultaat resultaat = controle.controleer(tekst);

    assertThat(resultaat.isValide()).isTrue();
    assertThat(resultaat.reden()).isNull();
  }

  @Test
  void teWeinigWoordenGeeftOngeldigResultaat() {
    String tekst = "Dit is een korte tekst met te weinig woorden.";

    Resultaat resultaat = controle.controleer(tekst);

    assertThat(resultaat.isValide()).isFalse();
    assertThat(resultaat.reden()).containsIgnoringCase("woorden");
  }

  @Test
  void minimumAantalWoordenIs40() {
    // 39 woorden: ongeldig
    String tekst39 = "een twee drie vier vijf zes zeven acht negen tien "
        + "elf twaalf dertien veertien vijftien zestien zeventien achttien negentien twintig "
        + "eenentwintig tweeentwintig drieentwintig vierentwintig vijfentwintig zesentwintig "
        + "zevenentwintig achtentwintig negenentwintig dertig eenendertig tweeendertig "
        + "drieendertig vierendertig vijfendertig zesendertig zevenendertig achtendertig negenendertig";
    assertThat(controle.controleer(tekst39).isValide()).isFalse();

    // 40 woorden met valide klinkerverhouding: geldig
    String tekst40 = tekst39 + " veertig";
    assertThat(controle.controleer(tekst40).isValide()).isTrue();
  }

  @Test
  void teVeelSpecialeTekensGeeftOngeldigResultaat() {
    // Meer dan 30% speciale tekens (exclusief spaties)
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 120; i++) {
      if (i % 2 == 0) {
        sb.append("@#$%&! ");
      } else {
        sb.append("woord ");
      }
    }

    Resultaat resultaat = controle.controleer(sb.toString());

    assertThat(resultaat.isValide()).isFalse();
    assertThat(resultaat.reden()).containsIgnoringCase("alfanumeriek");
  }

  @Test
  void klinkerRatioTeLaagGeeftOngeldigResultaat() {
    // Tekst met bijna geen klinkers (medeklinker-brij)
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 110; i++) {
      sb.append("brtkls ");
    }

    Resultaat resultaat = controle.controleer(sb.toString());

    assertThat(resultaat.isValide()).isFalse();
    assertThat(resultaat.reden()).containsIgnoringCase("klinker");
  }

  @Test
  void klinkerRatioTeHoogGeeftOngeldigResultaat() {
    // Tekst met bijna alleen klinkers
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 110; i++) {
      sb.append("aeiou ");
    }

    Resultaat resultaat = controle.controleer(sb.toString());

    assertThat(resultaat.isValide()).isFalse();
    assertThat(resultaat.reden()).containsIgnoringCase("klinker");
  }

  @Test
  void legeTekstGeeftOngeldigResultaat() {
    Resultaat resultaat = controle.controleer("");

    assertThat(resultaat.isValide()).isFalse();
    assertThat(resultaat.reden()).isNotBlank();
  }

  @Test
  void nullTekstGeeftOngeldigResultaat() {
    Resultaat resultaat = controle.controleer(null);

    assertThat(resultaat.isValide()).isFalse();
    assertThat(resultaat.reden()).isNotBlank();
  }

  /**
   * Genereert een valide Nederlandse tekst van meer dan 200 woorden
   * met normale klinker/medeklinker-verdeling.
   */
  private String genereerValideNederlandseTekst() {
    return "Het college van burgemeester en schepenen heeft op basis van "
        + "de ingediende bouwaanvraag een vergunning verleend voor het "
        + "oprichten van een eengezinswoning op het perceel gelegen aan "
        + "de Kerkstraat nummer vijftien in de gemeente Gent. De aanvrager "
        + "heeft alle vereiste documenten ingediend bij het gemeentebestuur "
        + "en de stedenbouwkundige dienst heeft een gunstig advies "
        + "uitgebracht over het bouwproject. Het bezwaarschrift dat werd "
        + "ingediend door de buren stelt dat de nieuwbouw niet in "
        + "overeenstemming is met het geldende ruimtelijk uitvoeringsplan. "
        + "Volgens de bezwaarindiener zou het gebouw de maximaal toegelaten "
        + "bouwhoogte overschrijden en zou de voorziene dakconstructie niet "
        + "passen binnen het straatbeeld van de omgeving. Daarnaast wordt "
        + "aangevoerd dat de voorziene parking onvoldoende capaciteit biedt "
        + "voor de verwachte verkeersgeneratie. De bezwaarindiener verwijst "
        + "naar verschillende artikelen uit de gemeentelijke stedenbouwkundige "
        + "verordening en naar relevante rechtspraak van de Raad voor "
        + "Vergunningsbetwistingen. Het college heeft deze bezwaren grondig "
        + "onderzocht en is tot de conclusie gekomen dat het bouwproject "
        + "volledig voldoet aan alle geldende voorschriften. De bouwhoogte "
        + "blijft binnen de toegelaten grenzen en de dakconstructie is "
        + "conform de architecturale richtlijnen voor de betrokken zone. "
        + "Wat betreft de parkeerproblematiek heeft de aanvrager een "
        + "mobiliteitsplan voorgelegd dat aantoont dat de voorziene "
        + "parkeerplaatsen ruimschoots voldoende zijn voor het project.";
  }
}
