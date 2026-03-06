package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ThemaTest {

  @Test
  void themaBevatKernbezwarenMetReferenties() {
    var ref1 = new IndividueelBezwaarReferentie(1L, "bezwaar1.txt",
        "De geluidsoverlast is ondraaglijk.", null, ToewijzingsMethode.HDBSCAN);
    var ref2 = new IndividueelBezwaarReferentie(2L, "bezwaar3.txt",
        "Wij ondervinden ernstige hinder van nachtelijk transport.", null, ToewijzingsMethode.HDBSCAN);
    var kern = new Kernbezwaar(1L,
        "Geluidshinder tijdens nachtelijke uren door vrachtverkeer",
        List.of(ref1, ref2), null);
    var thema = new Thema("Geluid", List.of(kern));

    assertThat(thema.naam()).isEqualTo("Geluid");
    assertThat(thema.kernbezwaren()).hasSize(1);
    assertThat(thema.kernbezwaren().get(0).samenvatting())
        .isEqualTo("Geluidshinder tijdens nachtelijke uren door vrachtverkeer");
    assertThat(thema.kernbezwaren().get(0).individueleBezwaren()).hasSize(2);
    assertThat(thema.kernbezwaren().get(0).individueleBezwaren().get(0).passage())
        .isEqualTo("De geluidsoverlast is ondraaglijk.");
  }
}
