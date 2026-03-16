package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PseudonimiseringChunkerTest {

  private static final int MAX_TEKENS = 100_000;
  private final PseudonimiseringChunker chunker = new PseudonimiseringChunker(MAX_TEKENS);

  @Test
  void korteTekstGeeftEenChunk() {
    var tekst = "Dit is een korte tekst.";

    var chunks = chunker.chunk(tekst);

    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).isEqualTo(tekst);
  }

  @Test
  void langeTekstWordtGesplitsOpDubbeleNewline() {
    var paragraaf1 = "A".repeat(60_000);
    var paragraaf2 = "B".repeat(60_000);
    var tekst = paragraaf1 + "\n\n" + paragraaf2;

    var chunks = chunker.chunk(tekst);

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0)).isEqualTo(paragraaf1);
    assertThat(chunks.get(1)).isEqualTo(paragraaf2);
  }

  @Test
  void splitstOpLaatsteNewlineVoorLimiet() {
    var p1 = "A".repeat(40_000);
    var p2 = "B".repeat(40_000);
    var p3 = "C".repeat(40_000);
    var tekst = p1 + "\n\n" + p2 + "\n\n" + p3;

    var chunks = chunker.chunk(tekst);

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0)).isEqualTo(p1 + "\n\n" + p2);
    assertThat(chunks.get(1)).isEqualTo(p3);
  }

  @Test
  void fallbackOpEnkeleNewlineAlsGeenDubbele() {
    var regel1 = "A".repeat(60_000);
    var regel2 = "B".repeat(60_000);
    var tekst = regel1 + "\n" + regel2;

    var chunks = chunker.chunk(tekst);

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0)).isEqualTo(regel1);
    assertThat(chunks.get(1)).isEqualTo(regel2);
  }

  @Test
  void hardeSplitAlsGeenNewlines() {
    var tekst = "X".repeat(250_000);

    var chunks = chunker.chunk(tekst);

    assertThat(chunks).hasSize(3);
    assertThat(chunks.get(0)).hasSize(MAX_TEKENS);
    assertThat(chunks.get(1)).hasSize(MAX_TEKENS);
    assertThat(chunks.get(2)).hasSize(50_000);
  }

  @Test
  void elkeChunkIsBinnenDeLimiet() {
    var tekst = "Paragraaf met tekst. ".repeat(10_000);

    var chunks = chunker.chunk(tekst);

    for (var chunk : chunks) {
      assertThat(chunk.length())
          .as("Elke chunk moet <= %d tekens zijn", MAX_TEKENS)
          .isLessThanOrEqualTo(MAX_TEKENS);
    }
  }

  @Test
  void samengevoegdeBijDubbeleNewlineSplitGeeftOrigineleTekst() {
    // NB: deze invariant geldt alleen als alle splits op \n\n-grenzen vallen
    var p1 = "Eerste paragraaf. ".repeat(3000);
    var p2 = "Tweede paragraaf. ".repeat(3000);
    var p3 = "Derde paragraaf. ".repeat(3000);
    var tekst = p1 + "\n\n" + p2 + "\n\n" + p3;

    var chunks = chunker.chunk(tekst);

    var samengevoegd = String.join("\n\n", chunks);
    assertThat(samengevoegd).isEqualTo(tekst);
  }

  @Test
  void legeTekstGeeftEenLegeChunk() {
    var chunks = chunker.chunk("");
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).isEmpty();
  }
}
