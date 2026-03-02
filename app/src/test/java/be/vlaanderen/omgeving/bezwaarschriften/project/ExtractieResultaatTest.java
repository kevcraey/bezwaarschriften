package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractieResultaatTest {

  @Test
  void volledigResultaatBevatPassagesEnBezwaren() {
    var passage = new Passage(1, "De geluidsoverlast is onaanvaardbaar.");
    var bezwaar = new GeextraheerdBezwaar(1, "Geluidsoverlast door evenementen", "milieu");

    var resultaat = new ExtractieResultaat(
        542, 1, List.of(passage), List.of(bezwaar), "Bezwaar over geluid");

    assertEquals(542, resultaat.aantalWoorden());
    assertEquals(1, resultaat.aantalBezwaren());
    assertEquals(1, resultaat.passages().size());
    assertEquals("De geluidsoverlast is onaanvaardbaar.", resultaat.passages().get(0).tekst());
    assertEquals(1, resultaat.bezwaren().size());
    assertEquals("milieu", resultaat.bezwaren().get(0).categorie());
    assertEquals("Bezwaar over geluid", resultaat.documentSamenvatting());
  }

  @Test
  void terugwaartseCompatibiliteitMetEnkelCounts() {
    var resultaat = new ExtractieResultaat(100, 3);

    assertEquals(100, resultaat.aantalWoorden());
    assertEquals(3, resultaat.aantalBezwaren());
    assertNotNull(resultaat.passages());
    assertEquals(0, resultaat.passages().size());
    assertNotNull(resultaat.bezwaren());
    assertEquals(0, resultaat.bezwaren().size());
  }
}
