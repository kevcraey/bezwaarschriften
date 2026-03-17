package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BezwaarDocumentTest {

  private BezwaarDocument maakDocument() {
    var doc = new BezwaarDocument();
    doc.setProjectNaam("test-project");
    doc.setBestandsnaam("bezwaar-001.pdf");
    return doc;
  }

  // --- startTekstExtractie ---

  @Test
  void startTekstExtractieZetStatusOpBezig() {
    var doc = maakDocument();

    doc.startTekstExtractie();

    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.BEZIG);
  }

  @Test
  void startTekstExtractieResetFoutmelding() {
    var doc = maakDocument();
    doc.setFoutmelding("vorige fout");

    doc.startTekstExtractie();

    assertThat(doc.getFoutmelding()).isNull();
  }

  @Test
  void startTekstExtractieZetExtractieIngediendOp() {
    var doc = maakDocument();

    doc.startTekstExtractie();

    assertThat(doc.getExtractieIngediendOp()).isNotNull();
  }

  @Test
  void startTekstExtractieNulltExtractieGestartOp() {
    var doc = maakDocument();
    doc.setExtractieGestartOp(java.time.Instant.now());

    doc.startTekstExtractie();

    assertThat(doc.getExtractieGestartOp()).isNull();
  }

  // --- voltooiTekstExtractie ---

  @Test
  void voltooiTekstExtractieZetStatusOpKlaar() {
    var doc = maakDocument();

    doc.voltooiTekstExtractie("DIGITAAL");

    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.KLAAR);
  }

  @Test
  void voltooiTekstExtractieZetExtractieMethode() {
    var doc = maakDocument();

    doc.voltooiTekstExtractie("OCR");

    assertThat(doc.getExtractieMethode()).isEqualTo("OCR");
  }

  // --- markeerTekstExtractieFout ---

  @Test
  void markeerTekstExtractieFoutZetStatusOpFout() {
    var doc = maakDocument();

    doc.markeerTekstExtractieFout("PDF kon niet gelezen worden");

    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.FOUT);
  }

  @Test
  void markeerTekstExtractieFoutZetFoutmelding() {
    var doc = maakDocument();

    doc.markeerTekstExtractieFout("PDF kon niet gelezen worden");

    assertThat(doc.getFoutmelding()).isEqualTo("PDF kon niet gelezen worden");
  }

  // --- resetTekstExtractie ---

  @Test
  void resetTekstExtractieZetStatusOpGeen() {
    var doc = maakDocument();
    doc.setTekstExtractieStatus(TekstExtractieStatus.KLAAR);

    doc.resetTekstExtractie();

    assertThat(doc.getTekstExtractieStatus()).isEqualTo(TekstExtractieStatus.GEEN);
  }

  @Test
  void resetTekstExtractieResetFoutmelding() {
    var doc = maakDocument();
    doc.setFoutmelding("vorige fout");

    doc.resetTekstExtractie();

    assertThat(doc.getFoutmelding()).isNull();
  }

  @Test
  void resetTekstExtractieResetExtractieMethode() {
    var doc = maakDocument();
    doc.setExtractieMethode("DIGITAAL");

    doc.resetTekstExtractie();

    assertThat(doc.getExtractieMethode()).isNull();
  }

  // --- startBezwaarExtractie ---

  @Test
  void startBezwaarExtractieZetStatusOpBezig() {
    var doc = maakDocument();

    doc.startBezwaarExtractie();

    assertThat(doc.getBezwaarExtractieStatus()).isEqualTo(BezwaarExtractieStatus.BEZIG);
  }

  @Test
  void startBezwaarExtractieResetFoutmelding() {
    var doc = maakDocument();
    doc.setFoutmelding("vorige fout");

    doc.startBezwaarExtractie();

    assertThat(doc.getFoutmelding()).isNull();
  }

  @Test
  void startBezwaarExtractieZetExtractieIngediendOp() {
    var doc = maakDocument();

    doc.startBezwaarExtractie();

    assertThat(doc.getExtractieIngediendOp()).isNotNull();
  }

  @Test
  void startBezwaarExtractieNulltExtractieGestartOp() {
    var doc = maakDocument();
    doc.setExtractieGestartOp(java.time.Instant.now());

    doc.startBezwaarExtractie();

    assertThat(doc.getExtractieGestartOp()).isNull();
  }

  // --- voltooiBezwaarExtractie ---

  @Test
  void voltooiBezwaarExtractieZetStatusOpKlaar() {
    var doc = maakDocument();

    doc.voltooiBezwaarExtractie(true, false);

    assertThat(doc.getBezwaarExtractieStatus()).isEqualTo(BezwaarExtractieStatus.KLAAR);
  }

  @Test
  void voltooiBezwaarExtractieZetHeeftPassages() {
    var doc = maakDocument();

    doc.voltooiBezwaarExtractie(true, false);

    assertThat(doc.isHeeftPassagesDieNietInTekstVoorkomen()).isTrue();
  }

  @Test
  void voltooiBezwaarExtractieZetHeeftManueel() {
    var doc = maakDocument();

    doc.voltooiBezwaarExtractie(false, true);

    assertThat(doc.isHeeftManueel()).isTrue();
  }

  // --- markeerBezwaarExtractieFout ---

  @Test
  void markeerBezwaarExtractieFoutZetStatusOpFout() {
    var doc = maakDocument();

    doc.markeerBezwaarExtractieFout("AI-service onbereikbaar");

    assertThat(doc.getBezwaarExtractieStatus()).isEqualTo(BezwaarExtractieStatus.FOUT);
  }

  @Test
  void markeerBezwaarExtractieFoutZetFoutmelding() {
    var doc = maakDocument();

    doc.markeerBezwaarExtractieFout("AI-service onbereikbaar");

    assertThat(doc.getFoutmelding()).isEqualTo("AI-service onbereikbaar");
  }

  // --- resetBezwaarExtractie ---

  @Test
  void resetBezwaarExtractieZetStatusOpGeen() {
    var doc = maakDocument();
    doc.setBezwaarExtractieStatus(BezwaarExtractieStatus.KLAAR);

    doc.resetBezwaarExtractie();

    assertThat(doc.getBezwaarExtractieStatus()).isEqualTo(BezwaarExtractieStatus.GEEN);
  }

  @Test
  void resetBezwaarExtractieResetHeeftPassages() {
    var doc = maakDocument();
    doc.setHeeftPassagesDieNietInTekstVoorkomen(true);

    doc.resetBezwaarExtractie();

    assertThat(doc.isHeeftPassagesDieNietInTekstVoorkomen()).isFalse();
  }

  @Test
  void resetBezwaarExtractieResetHeeftManueel() {
    var doc = maakDocument();
    doc.setHeeftManueel(true);

    doc.resetBezwaarExtractie();

    assertThat(doc.isHeeftManueel()).isFalse();
  }

  // --- markeerVerwerkingGestart ---

  @Test
  void markeerVerwerkingGestartZetExtractieGestartOp() {
    var doc = maakDocument();

    doc.markeerVerwerkingGestart();

    assertThat(doc.getExtractieGestartOp()).isNotNull();
  }
}
