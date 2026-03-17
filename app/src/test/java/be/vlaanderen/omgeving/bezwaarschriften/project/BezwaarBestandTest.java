package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BezwaarBestandTest {

  @Test
  void maaktRecordAanMetAlleVelden() {
    var bestand = new BezwaarBestand("bezwaar-001.txt", "KLAAR", "KLAAR",
        42, 3, true, false, "DIGITAAL", null);

    assertThat(bestand.bestandsnaam()).isEqualTo("bezwaar-001.txt");
    assertThat(bestand.tekstExtractieStatus()).isEqualTo("KLAAR");
    assertThat(bestand.bezwaarExtractieStatus()).isEqualTo("KLAAR");
    assertThat(bestand.aantalWoorden()).isEqualTo(42);
    assertThat(bestand.aantalBezwaren()).isEqualTo(3);
    assertThat(bestand.heeftPassagesDieNietInTekstVoorkomen()).isTrue();
    assertThat(bestand.heeftManueel()).isFalse();
    assertThat(bestand.extractieMethode()).isEqualTo("DIGITAAL");
    assertThat(bestand.foutmelding()).isNull();
  }

  @Test
  void maaktRecordAanMetFoutmelding() {
    var bestand = new BezwaarBestand("bezwaar-001.pdf", "FOUT", "GEEN",
        null, null, false, false, null, "Te weinig woorden: 28 (minimum 40)");

    assertThat(bestand.tekstExtractieStatus()).isEqualTo("FOUT");
    assertThat(bestand.foutmelding()).isEqualTo("Te weinig woorden: 28 (minimum 40)");
  }

  @Test
  void maaktRecordAanMetMinimaleVelden() {
    var bestand = new BezwaarBestand("bezwaar-001.txt", "GEEN", "GEEN",
        null, null, false, false, null, null);

    assertThat(bestand.bestandsnaam()).isEqualTo("bezwaar-001.txt");
    assertThat(bestand.aantalWoorden()).isNull();
    assertThat(bestand.aantalBezwaren()).isNull();
    assertThat(bestand.heeftPassagesDieNietInTekstVoorkomen()).isFalse();
    assertThat(bestand.heeftManueel()).isFalse();
    assertThat(bestand.extractieMethode()).isNull();
    assertThat(bestand.foutmelding()).isNull();
  }
}
