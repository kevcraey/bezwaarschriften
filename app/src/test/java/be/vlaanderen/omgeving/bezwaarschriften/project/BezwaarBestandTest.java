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
  void maaktKopieMetAndereStatus() {
    var bestand = new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.TODO);
    var bijgewerkt = bestand.withStatus(BezwaarBestandStatus.EXTRACTIE_KLAAR);

    assertThat(bijgewerkt.bestandsnaam()).isEqualTo("bezwaar-001.txt");
    assertThat(bijgewerkt.status()).isEqualTo(BezwaarBestandStatus.EXTRACTIE_KLAAR);
    assertThat(bestand.status()).isEqualTo(BezwaarBestandStatus.TODO); // original unchanged
  }
}
