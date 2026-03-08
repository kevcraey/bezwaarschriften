package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BezwaarBestandTest {

  @Test
  void maaktRecordAanMetBestandsnaamEnStatus() {
    var bestand = new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.TODO);

    assertThat(bestand.bestandsnaam()).isEqualTo("bezwaar-001.txt");
    assertThat(bestand.status()).isEqualTo(BezwaarBestandStatus.TODO);
    assertThat(bestand.extractieMethode()).isNull();
  }

  @Test
  void maaktRecordAanMetAantalWoordenEnAantalBezwaren() {
    var bestand = new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.EXTRACTIE_KLAAR,
        42, 3, false, false, "DIGITAAL");

    assertThat(bestand.aantalWoorden()).isEqualTo(42);
    assertThat(bestand.aantalBezwaren()).isEqualTo(3);
    assertThat(bestand.heeftPassagesDieNietInTekstVoorkomen()).isFalse();
    assertThat(bestand.heeftManueel()).isFalse();
    assertThat(bestand.extractieMethode()).isEqualTo("DIGITAAL");
  }

  @Test
  void tweeArgConstructorZetAantalWoordenEnAantalBezwarenOpNull() {
    var bestand = new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.TODO);

    assertThat(bestand.aantalWoorden()).isNull();
    assertThat(bestand.aantalBezwaren()).isNull();
    assertThat(bestand.heeftPassagesDieNietInTekstVoorkomen()).isFalse();
    assertThat(bestand.heeftManueel()).isFalse();
    assertThat(bestand.extractieMethode()).isNull();
  }

  @Test
  void maaktRecordAanMetHeeftPassagesDieNietInTekstVoorkomen() {
    var bestand = new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.EXTRACTIE_KLAAR,
        42, 3, true, false, "OCR");

    assertThat(bestand.heeftPassagesDieNietInTekstVoorkomen()).isTrue();
    assertThat(bestand.extractieMethode()).isEqualTo("OCR");
  }
}
