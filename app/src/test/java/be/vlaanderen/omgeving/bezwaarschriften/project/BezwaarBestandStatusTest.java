package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BezwaarBestandStatusTest {

  @Test
  void heeftElfStatussen() {
    assertThat(BezwaarBestandStatus.values()).hasSize(11);
  }

  @Test
  void bevatAlleVerwachteWaarden() {
    assertThat(BezwaarBestandStatus.TODO).isNotNull();
    assertThat(BezwaarBestandStatus.TEKST_EXTRACTIE_WACHTEND).isNotNull();
    assertThat(BezwaarBestandStatus.TEKST_EXTRACTIE_BEZIG).isNotNull();
    assertThat(BezwaarBestandStatus.TEKST_EXTRACTIE_KLAAR).isNotNull();
    assertThat(BezwaarBestandStatus.TEKST_EXTRACTIE_MISLUKT).isNotNull();
    assertThat(BezwaarBestandStatus.TEKST_EXTRACTIE_OCR_NIET_BESCHIKBAAR).isNotNull();
    assertThat(BezwaarBestandStatus.WACHTEND).isNotNull();
    assertThat(BezwaarBestandStatus.BEZIG).isNotNull();
    assertThat(BezwaarBestandStatus.EXTRACTIE_KLAAR).isNotNull();
    assertThat(BezwaarBestandStatus.FOUT).isNotNull();
    assertThat(BezwaarBestandStatus.NIET_ONDERSTEUND).isNotNull();
  }
}
