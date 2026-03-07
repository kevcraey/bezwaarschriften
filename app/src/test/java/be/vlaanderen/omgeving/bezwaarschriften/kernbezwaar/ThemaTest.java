package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ThemaTest {

  @Test
  void themaBevatKernbezwarenMetPassageGroepen() {
    var doc1 = new PassageGroepDocument(1L, "bezwaar1.pdf");
    var doc2 = new PassageGroepDocument(2L, "bezwaar2.pdf");
    var groep = new PassageGroepDto(10L, "Geluidshinder passage...", List.of(doc1, doc2));

    assertThat(groep.documenten()).hasSize(2);
    assertThat(groep.passage()).isEqualTo("Geluidshinder passage...");
  }

  @Test
  void themaHeeftDeduplicatieVlag() {
    var kern = new Kernbezwaar(1L, "Geluidshinder", List.of(), null);
    var thema = new Thema("Milieu", List.of(kern), true);

    assertThat(thema.passageDeduplicatieVoorClustering()).isTrue();
    assertThat(thema.naam()).isEqualTo("Milieu");
    assertThat(thema.kernbezwaren()).hasSize(1);
  }
}
