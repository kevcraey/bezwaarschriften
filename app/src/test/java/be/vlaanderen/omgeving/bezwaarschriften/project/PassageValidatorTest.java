package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PassageValidatorTest {

  private PassageValidator validator;

  @BeforeEach
  void setUp() {
    validator = new PassageValidator();
  }

  @Test
  void exacteMatchGevondenEenKeer() {
    var bezwaar = maakBezwaar(1);
    var passageMap = Map.of(1, "de geluidshinder is onaanvaardbaar");
    var document =
        "In het rapport staat dat de geluidshinder is onaanvaardbaar voor omwonenden.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, document);

    assertThat(resultaat.aantalNietGevonden()).isZero();
    assertThat(bezwaar.isPassageGevonden()).isTrue();
  }

  @Test
  void exacteMatchGevondenMeerdereKeren() {
    var bezwaar = maakBezwaar(1);
    var passageMap = Map.of(1, "onaanvaardbaar");
    var document =
        "De hinder is onaanvaardbaar. Het geluid is ook onaanvaardbaar.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, document);

    assertThat(resultaat.aantalNietGevonden()).isZero();
    assertThat(bezwaar.isPassageGevonden()).isTrue();
  }

  @Test
  void nietGevondenGeeftFalse() {
    var bezwaar = maakBezwaar(1);
    var passageMap = Map.of(1, "dit staat helemaal niet in het document");
    var document =
        "Het rapport bespreekt de milieueffecten van het project.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, document);

    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
    assertThat(bezwaar.isPassageGevonden()).isFalse();
  }

  @Test
  void whitespaceNormalisatieExtraSpaties() {
    var bezwaar = maakBezwaar(1);
    var passageMap =
        Map.of(1, "de  geluidshinder  is  onaanvaardbaar");
    var document =
        "In het rapport staat dat de geluidshinder is onaanvaardbaar"
            + " voor omwonenden.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, document);

    assertThat(resultaat.aantalNietGevonden()).isZero();
    assertThat(bezwaar.isPassageGevonden()).isTrue();
  }

  @Test
  void whitespaceNormalisatieNewlines() {
    var bezwaar = maakBezwaar(1);
    var passageMap =
        Map.of(1, "de geluidshinder\nis\nonaanvaardbaar");
    var document =
        "In het rapport staat dat de geluidshinder is onaanvaardbaar"
            + " voor omwonenden.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, document);

    assertThat(resultaat.aantalNietGevonden()).isZero();
    assertThat(bezwaar.isPassageGevonden()).isTrue();
  }

  @Test
  void fuzzyMatchNetBovenDrempel() {
    var bezwaar = maakBezwaar(1);
    // "bewoners" vs "bevoners" — 1 char difference in a long passage
    var passageMap = Map.of(1,
        "de geluidsoverlast in de omgeving van het projectgebied"
            + " is bijzonder problematisch voor de bewoners");
    var document =
        "Uit het onderzoek blijkt dat de geluidsoverlast in de"
            + " omgeving van het projectgebied is bijzonder"
            + " problematisch voor de bevoners en hun gezondheid.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, document);

    assertThat(resultaat.aantalNietGevonden()).isZero();
    assertThat(bezwaar.isPassageGevonden()).isTrue();
  }

  @Test
  void fuzzyMatchOnderDrempel() {
    var bezwaar = maakBezwaar(1);
    var passageMap = Map.of(1,
        "compleet andere tekst over iets heel anders dan wat er staat");
    var document =
        "Het rapport bespreekt de milieueffecten van het"
            + " industriegebied op de lokale waterhuishouding.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, document);

    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
    assertThat(bezwaar.isPassageGevonden()).isFalse();
  }

  @Test
  void legePassageTekst() {
    var bezwaar = maakBezwaar(1);
    var passageMap = Map.of(1, "");
    var document = "Het rapport bespreekt de milieueffecten.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, document);

    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
    assertThat(bezwaar.isPassageGevonden()).isFalse();
  }

  @Test
  void legeDocumentTekst() {
    var bezwaar = maakBezwaar(1);
    var passageMap =
        Map.of(1, "de geluidshinder is onaanvaardbaar");
    var document = "";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, document);

    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
    assertThat(bezwaar.isPassageGevonden()).isFalse();
  }

  @Test
  void meerdereBezwarenMixVanGevondenEnNiet() {
    var bezwaar1 = maakBezwaar(1);
    var bezwaar2 = maakBezwaar(2);
    var passageMap = Map.of(
        1, "geluidshinder is onaanvaardbaar",
        2, "dit staat nergens in het document");
    var document =
        "De geluidshinder is onaanvaardbaar voor de omwonenden.";

    var resultaat = validator.valideer(
        List.of(bezwaar1, bezwaar2), passageMap, document);

    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
    assertThat(bezwaar1.isPassageGevonden()).isTrue();
    assertThat(bezwaar2.isPassageGevonden()).isFalse();
  }

  @Test
  void passageZonderEntryInMap() {
    var bezwaar = maakBezwaar(99);
    var passageMap = Map.of(1, "een passage die wel bestaat");
    var document = "Een passage die wel bestaat in het document.";

    var resultaat = validator.valideer(List.of(bezwaar), passageMap, document);

    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
    assertThat(bezwaar.isPassageGevonden()).isFalse();
  }

  private GeextraheerdBezwaarEntiteit maakBezwaar(int passageNr) {
    var bezwaar = new GeextraheerdBezwaarEntiteit();
    bezwaar.setPassageNr(passageNr);
    bezwaar.setSamenvatting("Samenvatting " + passageNr);
    bezwaar.setCategorie("milieu");
    return bezwaar;
  }
}
