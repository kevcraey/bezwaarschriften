package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BezwaarBestandTest {

  @Test
  void maaktRecordAanMetBestandsnaamEnStatus() {
    var bestand = new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.TODO);

    assertThat(bestand.bestandsnaam()).isEqualTo("bezwaar-001.txt");
    assertThat(bestand.status()).isEqualTo(BezwaarBestandStatus.TODO);
  }

  @Test
  void maaktRecordAanMetAantalWoorden() {
    var bestand = new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.EXTRACTIE_KLAAR, 42);

    assertThat(bestand.aantalWoorden()).isEqualTo(42);
  }

  @Test
  void tweeArgConstructorZetAantalWoordenOpNull() {
    var bestand = new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.TODO);

    assertThat(bestand.aantalWoorden()).isNull();
  }
}
