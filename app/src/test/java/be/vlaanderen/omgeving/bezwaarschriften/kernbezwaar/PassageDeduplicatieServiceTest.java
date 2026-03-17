package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import be.vlaanderen.omgeving.bezwaarschriften.project.IndividueelBezwaar;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PassageDeduplicatieServiceTest {

  private PassageDeduplicatieService service;

  @BeforeEach
  void setUp() {
    service = new PassageDeduplicatieService();
  }

  @Test
  void identiekeTekstenWordenGegroepeerd() {
    var b1 = maakBezwaar(1L, 10L, "De stikstofuitstoot is onvoldoende onderzocht.",
        "samenvatting A");
    var b2 = maakBezwaar(2L, 20L, "De stikstofuitstoot is onvoldoende onderzocht.",
        "samenvatting B");

    var bestandsnaamLookup = Map.of(10L, "bestand1.pdf", 20L, "bestand2.pdf");

    var groepen = service.groepeer(List.of(b1, b2), bestandsnaamLookup);

    assertThat(groepen).hasSize(1);
    assertThat(groepen.get(0).leden()).hasSize(2);
    assertThat(groepen.get(0).passage())
        .isEqualTo("De stikstofuitstoot is onvoldoende onderzocht.");
  }

  @Test
  void verschillendeTekstenWordenNietGegroepeerd() {
    var b1 = maakBezwaar(1L, 10L, "De stikstofuitstoot is onvoldoende onderzocht.",
        "samenvatting A");
    var b2 = maakBezwaar(2L, 20L, "Het verkeersmodel klopt niet met de realiteit.",
        "samenvatting B");

    var bestandsnaamLookup = Map.of(10L, "bestand1.pdf", 20L, "bestand2.pdf");

    var groepen = service.groepeer(List.of(b1, b2), bestandsnaamLookup);

    assertThat(groepen).hasSize(2);
    assertThat(groepen.get(0).leden()).hasSize(1);
    assertThat(groepen.get(1).leden()).hasSize(1);
  }

  @Test
  void binaIdentiekeTekstenWordenGegroepeerd() {
    var korteVariant =
        "De stikstofuitstoot is onvoldoende onderzocht in het milieueffectenrapport.";
    var langeVariant =
        "De stikstofuitstoot is onvoldoende onderzocht in het milieueffectrapport.";

    var b1 = maakBezwaar(1L, 10L, korteVariant, "samenvatting A");
    var b2 = maakBezwaar(2L, 20L, langeVariant, "samenvatting B");

    // Verifieer dat de Dice-coefficient inderdaad >= 0.9 is
    double dice = service.berekenDiceCoefficient(korteVariant, langeVariant);
    assertThat(dice).isGreaterThanOrEqualTo(0.9);

    var bestandsnaamLookup = Map.of(10L, "bestand1.pdf", 20L, "bestand2.pdf");

    var groepen = service.groepeer(List.of(b1, b2), bestandsnaamLookup);

    assertThat(groepen).hasSize(1);
    assertThat(groepen.get(0).leden()).hasSize(2);
  }

  @Test
  void langstePassageWordtRepresentatief() {
    var kortePassage =
        "De stikstofuitstoot is onvoldoende onderzocht in het milieueffectenrapport.";
    var langePassage =
        "De stikstofuitstoot is onvoldoende onderzocht in het milieueffectenrapport van dit project.";

    var b1 = maakBezwaar(1L, 10L, kortePassage, "korte samenvatting");
    var b2 = maakBezwaar(2L, 20L, langePassage, "langere samenvatting met meer detail");

    // Verifieer dat ze gelijk genoeg zijn
    double dice = service.berekenDiceCoefficient(kortePassage, langePassage);
    assertThat(dice).isGreaterThanOrEqualTo(0.9);

    var bestandsnaamLookup = Map.of(10L, "kort.pdf", 20L, "lang.pdf");

    // Korte eerst, lange daarna -> lange wordt representatief
    var groepen = service.groepeer(List.of(b1, b2), bestandsnaamLookup);

    assertThat(groepen).hasSize(1);
    assertThat(groepen.get(0).representatief().getId()).isEqualTo(2L);
    assertThat(groepen.get(0).passage()).isEqualTo(langePassage);
    assertThat(groepen.get(0).samenvatting()).isEqualTo("langere samenvatting met meer detail");
  }

  @Test
  void legeInvoerGeeftLegeOutput() {
    var groepen = service.groepeer(List.of(), Map.of());

    assertThat(groepen).isEmpty();
  }

  @Test
  void diceCoefficient_identiekeTeksten() {
    double dice = service.berekenDiceCoefficient("identiek", "identiek");
    assertThat(dice).isEqualTo(1.0);
  }

  @Test
  void diceCoefficient_totaalVerschillend() {
    double dice = service.berekenDiceCoefficient("abc", "xyz");
    assertThat(dice).isEqualTo(0.0);
  }

  @Test
  void diceCoefficient_verifyMetFrontend() {
    double dice = service.berekenDiceCoefficient("nacht", "nacho");
    assertThat(dice).isCloseTo(0.75, within(0.001));
  }

  @Test
  void passageTekstFallbackNaarSamenvatting() {
    // Bezwaren zonder passageTekst -> fallback naar samenvatting
    var b1 = maakBezwaar(1L, 10L, null, "dezelfde samenvatting als passage");
    var b2 = maakBezwaar(2L, 20L, null, "dezelfde samenvatting als passage");

    var bestandsnaamLookup = Map.of(10L, "bestand1.pdf", 20L, "bestand2.pdf");

    var groepen = service.groepeer(List.of(b1, b2), bestandsnaamLookup);

    assertThat(groepen).hasSize(1);
    assertThat(groepen.get(0).leden()).hasSize(2);
    assertThat(groepen.get(0).passage()).isEqualTo("dezelfde samenvatting als passage");
  }

  @Test
  void bestandsnaamWordtOvergenomenVanLookup() {
    var b1 = maakBezwaar(1L, 10L, "een passage", "samenvatting");

    var bestandsnaamLookup = Map.of(10L, "mijn-bestand.pdf");

    var groepen = service.groepeer(List.of(b1), bestandsnaamLookup);

    assertThat(groepen).hasSize(1);
    assertThat(groepen.get(0).leden().get(0).bestandsnaam()).isEqualTo("mijn-bestand.pdf");
  }

  private IndividueelBezwaar maakBezwaar(
      Long id, Long documentId, String passageTekst, String samenvatting) {
    var b = new IndividueelBezwaar();
    b.setId(id);
    b.setDocumentId(documentId);
    b.setPassageTekst(passageTekst);
    b.setSamenvatting(samenvatting);
    return b;
  }
}
