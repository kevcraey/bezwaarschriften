package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
    var bezwaar = maakBezwaar("de geluidshinder is onaanvaardbaar");
    var document =
        "In het rapport staat dat de geluidshinder is onaanvaardbaar voor omwonenden.";

    var resultaat = validator.valideer(List.of(bezwaar), document);

    assertThat(resultaat.aantalNietGevonden()).isZero();
    assertThat(bezwaar.isPassageGevonden()).isTrue();
  }

  @Test
  void exacteMatchGevondenMeerdereKeren() {
    var bezwaar = maakBezwaar("onaanvaardbaar");
    var document =
        "De hinder is onaanvaardbaar. Het geluid is ook onaanvaardbaar.";

    var resultaat = validator.valideer(List.of(bezwaar), document);

    assertThat(resultaat.aantalNietGevonden()).isZero();
    assertThat(bezwaar.isPassageGevonden()).isTrue();
  }

  @Test
  void nietGevondenGeeftFalse() {
    var bezwaar = maakBezwaar("dit staat helemaal niet in het document");
    var document =
        "Het rapport bespreekt de milieueffecten van het project.";

    var resultaat = validator.valideer(List.of(bezwaar), document);

    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
    assertThat(bezwaar.isPassageGevonden()).isFalse();
  }

  @Test
  void whitespaceNormalisatieExtraSpaties() {
    var bezwaar = maakBezwaar("de  geluidshinder  is  onaanvaardbaar");
    var document =
        "In het rapport staat dat de geluidshinder is onaanvaardbaar"
            + " voor omwonenden.";

    var resultaat = validator.valideer(List.of(bezwaar), document);

    assertThat(resultaat.aantalNietGevonden()).isZero();
    assertThat(bezwaar.isPassageGevonden()).isTrue();
  }

  @Test
  void whitespaceNormalisatieNewlines() {
    var bezwaar = maakBezwaar("de geluidshinder\nis\nonaanvaardbaar");
    var document =
        "In het rapport staat dat de geluidshinder is onaanvaardbaar"
            + " voor omwonenden.";

    var resultaat = validator.valideer(List.of(bezwaar), document);

    assertThat(resultaat.aantalNietGevonden()).isZero();
    assertThat(bezwaar.isPassageGevonden()).isTrue();
  }

  @Test
  void fuzzyMatchNetBovenDrempel() {
    var bezwaar = maakBezwaar(
        "de geluidsoverlast in de omgeving van het projectgebied"
            + " is bijzonder problematisch voor de bewoners");
    var document =
        "Uit het onderzoek blijkt dat de geluidsoverlast in de"
            + " omgeving van het projectgebied is bijzonder"
            + " problematisch voor de bevoners en hun gezondheid.";

    var resultaat = validator.valideer(List.of(bezwaar), document);

    assertThat(resultaat.aantalNietGevonden()).isZero();
    assertThat(bezwaar.isPassageGevonden()).isTrue();
  }

  @Test
  void fuzzyMatchOnderDrempel() {
    var bezwaar = maakBezwaar(
        "compleet andere tekst over iets heel anders dan wat er staat");
    var document =
        "Het rapport bespreekt de milieueffecten van het"
            + " industriegebied op de lokale waterhuishouding.";

    var resultaat = validator.valideer(List.of(bezwaar), document);

    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
    assertThat(bezwaar.isPassageGevonden()).isFalse();
  }

  @Test
  void legePassageTekst() {
    var bezwaar = maakBezwaar("");
    var document = "Het rapport bespreekt de milieueffecten.";

    var resultaat = validator.valideer(List.of(bezwaar), document);

    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
    assertThat(bezwaar.isPassageGevonden()).isFalse();
  }

  @Test
  void legeDocumentTekst() {
    var bezwaar = maakBezwaar("de geluidshinder is onaanvaardbaar");
    var document = "";

    var resultaat = validator.valideer(List.of(bezwaar), document);

    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
    assertThat(bezwaar.isPassageGevonden()).isFalse();
  }

  @Test
  void meerdereBezwarenMixVanGevondenEnNiet() {
    var bezwaar1 = maakBezwaar("geluidshinder is onaanvaardbaar");
    var bezwaar2 = maakBezwaar("dit staat nergens in het document");
    var document =
        "De geluidshinder is onaanvaardbaar voor de omwonenden.";

    var resultaat = validator.valideer(
        List.of(bezwaar1, bezwaar2), document);

    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
    assertThat(bezwaar1.isPassageGevonden()).isTrue();
    assertThat(bezwaar2.isPassageGevonden()).isFalse();
  }

  @Test
  void passageZonderTekst() {
    var bezwaar = maakBezwaar(null);
    var document = "Een passage die wel bestaat in het document.";

    var resultaat = validator.valideer(List.of(bezwaar), document);

    assertThat(resultaat.aantalNietGevonden()).isEqualTo(1);
    assertThat(bezwaar.isPassageGevonden()).isFalse();
  }

  private IndividueelBezwaar maakBezwaar(String passageTekst) {
    var bezwaar = new IndividueelBezwaar();
    bezwaar.setPassageTekst(passageTekst);
    bezwaar.setSamenvatting("Samenvatting");
    return bezwaar;
  }
}
