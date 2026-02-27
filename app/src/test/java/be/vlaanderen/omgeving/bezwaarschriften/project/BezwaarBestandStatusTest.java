package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BezwaarBestandStatusTest {

  @Test
  void heeftVierStatussen() {
    assertThat(BezwaarBestandStatus.values()).hasSize(4);
  }

  @Test
  void bevatAlleVerwachteWaarden() {
    assertThat(BezwaarBestandStatus.TODO).isNotNull();
    assertThat(BezwaarBestandStatus.EXTRACTIE_KLAAR).isNotNull();
    assertThat(BezwaarBestandStatus.FOUT).isNotNull();
    assertThat(BezwaarBestandStatus.NIET_ONDERSTEUND).isNotNull();
  }
}
