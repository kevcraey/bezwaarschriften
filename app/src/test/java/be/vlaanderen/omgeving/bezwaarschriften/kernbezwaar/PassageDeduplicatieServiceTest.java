package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarEntiteit;
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
    var b1 = maakBezwaar(1L, 10L, 0, "samenvatting A", "bestand1.pdf");
    var b2 = maakBezwaar(2L, 20L, 0, "samenvatting B", "bestand2.pdf");

    var passageLookup =
        Map.of(
            10L, Map.of(0, "De stikstofuitstoot is onvoldoende onderzocht."),
            20L, Map.of(0, "De stikstofuitstoot is onvoldoende onderzocht."));

    var groepen = service.groepeer(List.of(b1, b2), passageLookup);

    assertThat(groepen).hasSize(1);
    assertThat(groepen.get(0).leden()).hasSize(2);
    assertThat(groepen.get(0).passage())
        .isEqualTo("De stikstofuitstoot is onvoldoende onderzocht.");
  }

  @Test
  void verschillendeTekstenWordenNietGegroepeerd() {
    var b1 = maakBezwaar(1L, 10L, 0, "samenvatting A", "bestand1.pdf");
    var b2 = maakBezwaar(2L, 20L, 0, "samenvatting B", "bestand2.pdf");

    var passageLookup =
        Map.of(
            10L, Map.of(0, "De stikstofuitstoot is onvoldoende onderzocht."),
            20L, Map.of(0, "Het verkeersmodel klopt niet met de realiteit."));

    var groepen = service.groepeer(List.of(b1, b2), passageLookup);

    assertThat(groepen).hasSize(2);
    assertThat(groepen.get(0).leden()).hasSize(1);
    assertThat(groepen.get(1).leden()).hasSize(1);
  }

  @Test
  void binaIdentiekeTekstenWordenGegroepeerd() {
    var b1 = maakBezwaar(1L, 10L, 0, "samenvatting A", "bestand1.pdf");
    var b2 = maakBezwaar(2L, 20L, 0, "samenvatting B", "bestand2.pdf");

    // Verschil van 1 karakter in een lange tekst -> Dice >= 0.9
    var passageLookup =
        Map.of(
            10L,
                Map.of(
                    0,
                    "De stikstofuitstoot is onvoldoende onderzocht in het milieueffectenrapport."),
            20L,
                Map.of(
                    0,
                    "De stikstofuitstoot is onvoldoende onderzocht in het milieueffectrapport."));

    // Verifieer dat de Dice-coefficient inderdaad >= 0.9 is
    double dice =
        service.berekenDiceCoefficient(
            "De stikstofuitstoot is onvoldoende onderzocht in het milieueffectenrapport.",
            "De stikstofuitstoot is onvoldoende onderzocht in het milieueffectrapport.");
    assertThat(dice).isGreaterThanOrEqualTo(0.9);

    var groepen = service.groepeer(List.of(b1, b2), passageLookup);

    assertThat(groepen).hasSize(1);
    assertThat(groepen.get(0).leden()).hasSize(2);
  }

  @Test
  void langstePassageWordtRepresentatief() {
    var b1 = maakBezwaar(1L, 10L, 0, "korte samenvatting", "kort.pdf");
    var b2 = maakBezwaar(2L, 20L, 0, "langere samenvatting met meer detail", "lang.pdf");

    var kortePassage =
        "De stikstofuitstoot is onvoldoende onderzocht in het milieueffectenrapport.";
    var langePassage =
        "De stikstofuitstoot is onvoldoende onderzocht in het milieueffectenrapport van dit project.";

    // Verifieer dat ze gelijk genoeg zijn
    double dice = service.berekenDiceCoefficient(kortePassage, langePassage);
    assertThat(dice).isGreaterThanOrEqualTo(0.9);

    // Korte eerst, lange daarna -> lange wordt representatief
    var passageLookup =
        Map.of(10L, Map.of(0, kortePassage), 20L, Map.of(0, langePassage));

    var groepen = service.groepeer(List.of(b1, b2), passageLookup);

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
    // Specifieke testcase om te verifi-eren dat Java-implementatie overeenkomt met JS frontend.
    // JS berekening voor "nacht" vs "nacho":
    //   bigrammen("nacht") = {na:1, ac:1, ch:1, ht:1} -> lengte=5, #bigrammen=4
    //   bigrammen("nacho") = {na:1, ac:1, ch:1, ho:1} -> lengte=5, #bigrammen=4
    //   overlap = min(1,1) + min(1,1) + min(1,1) = 3
    //   dice = (2 * 3) / (4 + 4) = 0.75
    double dice = service.berekenDiceCoefficient("nacht", "nacho");
    assertThat(dice).isCloseTo(0.75, within(0.001));
  }

  @Test
  void passageLookupFallbackNaarSamenvatting() {
    var b1 = maakBezwaar(1L, 10L, 0, "dezelfde samenvatting als passage", "bestand1.pdf");
    var b2 = maakBezwaar(2L, 20L, 0, "dezelfde samenvatting als passage", "bestand2.pdf");

    // Geen passage in lookup -> fallback naar samenvatting
    Map<Long, Map<Integer, String>> passageLookup = Map.of();

    var groepen = service.groepeer(List.of(b1, b2), passageLookup);

    assertThat(groepen).hasSize(1);
    assertThat(groepen.get(0).leden()).hasSize(2);
    assertThat(groepen.get(0).passage()).isEqualTo("dezelfde samenvatting als passage");
  }

  @Test
  void bestandsnaamWordtOvergenomenVanEntiteit() {
    var b1 = maakBezwaar(1L, 10L, 0, "een passage", "mijn-bestand.pdf");

    var passageLookup = Map.of(10L, Map.of(0, "een passage"));

    var groepen = service.groepeer(List.of(b1), passageLookup);

    assertThat(groepen).hasSize(1);
    assertThat(groepen.get(0).leden().get(0).bestandsnaam()).isEqualTo("mijn-bestand.pdf");
  }

  private GeextraheerdBezwaarEntiteit maakBezwaar(
      Long id, Long taakId, int passageNr, String samenvatting, String bestandsnaam) {
    var b = new GeextraheerdBezwaarEntiteit();
    b.setId(id);
    b.setTaakId(taakId);
    b.setPassageNr(passageNr);
    b.setSamenvatting(samenvatting);
    b.setBestandsnaam(bestandsnaam);
    return b;
  }
}
