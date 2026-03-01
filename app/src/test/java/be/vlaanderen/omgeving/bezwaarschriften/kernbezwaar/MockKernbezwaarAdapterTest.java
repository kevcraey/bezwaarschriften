package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MockKernbezwaarAdapterTest {

  private final MockKernbezwaarAdapter adapter = new MockKernbezwaarAdapter();

  @Test
  void groepeertBezwarenInThemas() {
    var invoer = List.of(
        new KernbezwaarPoort.BezwaarInvoer(1L, "bezwaar1.txt", "tekst 1"),
        new KernbezwaarPoort.BezwaarInvoer(2L, "bezwaar2.txt", "tekst 2"),
        new KernbezwaarPoort.BezwaarInvoer(3L, "bezwaar3.txt", "tekst 3"));

    var themas = adapter.groepeer(invoer);

    assertThat(themas).isNotEmpty();
    assertThat(themas).allSatisfy(thema -> {
      assertThat(thema.naam()).isNotBlank();
      assertThat(thema.kernbezwaren()).isNotEmpty();
      assertThat(thema.kernbezwaren()).allSatisfy(kern -> {
        assertThat(kern.samenvatting()).isNotBlank();
        assertThat(kern.individueleBezwaren()).isNotEmpty();
        assertThat(kern.individueleBezwaren()).allSatisfy(ref -> {
          assertThat(ref.bestandsnaam()).isNotBlank();
          assertThat(ref.passage()).isNotBlank();
        });
      });
    });
  }

  @Test
  void retourneertDrieThemas() {
    var invoer = List.of(
        new KernbezwaarPoort.BezwaarInvoer(1L, "b1.txt", "t1"));

    var themas = adapter.groepeer(invoer);

    var themaNamen = themas.stream().map(Thema::naam).toList();
    assertThat(themaNamen).containsExactly("Geluid", "Mobiliteit", "Geurhinder");
  }

  @Test
  void retourneertLeegBijGeenInvoer() {
    var themas = adapter.groepeer(List.of());

    assertThat(themas).isEmpty();
  }
}
