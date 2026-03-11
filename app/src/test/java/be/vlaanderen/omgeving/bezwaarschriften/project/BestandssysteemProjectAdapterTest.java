package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BestandssysteemProjectAdapterTest {

  @TempDir
  Path inputFolder;

  private BestandssysteemProjectAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new BestandssysteemProjectAdapter(inputFolder.toString());
  }

  @Test
  void geeftProjectenTerug() throws Exception {
    Files.createDirectory(inputFolder.resolve("windmolens"));
    Files.createDirectory(inputFolder.resolve("zonnepanelen"));

    var projecten = adapter.geefProjecten();

    assertThat(projecten).containsExactlyInAnyOrder("windmolens", "zonnepanelen");
  }

  @Test
  void negeertLosseBestandenInInputFolder() throws Exception {
    Files.createDirectory(inputFolder.resolve("windmolens"));
    Files.writeString(inputFolder.resolve("readme.txt"), "inhoud");

    var projecten = adapter.geefProjecten();

    assertThat(projecten).containsExactly("windmolens");
  }

  @Test
  void geeftLegeProjectenlijstAlsInputFolderLeegIs() {
    var projecten = adapter.geefProjecten();

    assertThat(projecten).isEmpty();
  }

  @Test
  void geeftLegeProjectenlijstAlsInputFolderNietBestaat() throws Exception {
    var adapter = new BestandssysteemProjectAdapter(inputFolder.resolve("bestaat-niet").toString());

    var projecten = adapter.geefProjecten();

    assertThat(projecten).isEmpty();
  }

  @Test
  void geeftBestandsnamenVanProject() throws Exception {
    var bezwarenMap = inputFolder.resolve("windmolens").resolve("bezwaren-orig");
    Files.createDirectories(bezwarenMap);
    Files.writeString(bezwarenMap.resolve("bezwaar-001.txt"), "inhoud");
    Files.writeString(bezwarenMap.resolve("bijlage.pdf"), "pdf inhoud");

    var bestandsnamen = adapter.geefBestandsnamen("windmolens");

    assertThat(bestandsnamen).containsExactlyInAnyOrder("bezwaar-001.txt", "bijlage.pdf");
  }

  @Test
  void geeftLegeBestandsnamenAlsBezwarenMapNietBestaat() throws Exception {
    Files.createDirectory(inputFolder.resolve("windmolens"));

    var bestandsnamen = adapter.geefBestandsnamen("windmolens");

    assertThat(bestandsnamen).isEmpty();
  }

  @Test
  void geeftLegeBestandsnamenAlsBezwarenMapLeegIs() throws Exception {
    Files.createDirectories(inputFolder.resolve("windmolens").resolve("bezwaren-orig"));

    var bestandsnamen = adapter.geefBestandsnamen("windmolens");

    assertThat(bestandsnamen).isEmpty();
  }

  @Test
  void negeertVerborgenBestandenInBezwarenMap() throws Exception {
    var bezwarenMap = inputFolder.resolve("windmolens").resolve("bezwaren-orig");
    Files.createDirectories(bezwarenMap);
    Files.writeString(bezwarenMap.resolve("bezwaar-001.txt"), "inhoud");
    Files.writeString(bezwarenMap.resolve(".DS_Store"), "verborgen");
    Files.writeString(bezwarenMap.resolve(".hidden"), "verborgen");

    var bestandsnamen = adapter.geefBestandsnamen("windmolens");

    assertThat(bestandsnamen).containsExactly("bezwaar-001.txt");
  }

  @Test
  void gooidExceptionVoorOnbekendProject() {
    var exception = assertThrows(
        ProjectNietGevondenException.class,
        () -> adapter.geefBestandsnamen("bestaat-niet")
    );

    assertThat(exception.getProjectNaam()).isEqualTo("bestaat-niet");
  }

  @Test
  void gooidExceptionBijPathTraversal() {
    assertThrows(
        ProjectNietGevondenException.class,
        () -> adapter.geefBestandsnamen("../andere-folder")
    );
  }

  @Test
  void geefBestandsPad_geeftPadVoorBestaandBestand() throws Exception {
    var bezwarenMap = inputFolder.resolve("windmolens").resolve("bezwaren-orig");
    Files.createDirectories(bezwarenMap);
    Files.writeString(bezwarenMap.resolve("bezwaar1.txt"), "inhoud");

    var result = adapter.geefBestandsPad("windmolens", "bezwaar1.txt");

    assertThat(result).isEqualTo(bezwarenMap.resolve("bezwaar1.txt"));
  }

  @Test
  void geefBestandsPad_gooitExceptieBijPathTraversal() throws Exception {
    var bezwarenMap = inputFolder.resolve("windmolens").resolve("bezwaren-orig");
    Files.createDirectories(bezwarenMap);

    assertThrows(
        IllegalArgumentException.class,
        () -> adapter.geefBestandsPad("windmolens", "../etc/passwd")
    );
  }

  @Test
  void geefBestandsPad_gooitExceptieBijOnbekendBestand() throws Exception {
    var bezwarenMap = inputFolder.resolve("windmolens").resolve("bezwaren-orig");
    Files.createDirectories(bezwarenMap);

    assertThrows(
        BestandNietGevondenException.class,
        () -> adapter.geefBestandsPad("windmolens", "bestaat-niet.txt")
    );
  }

  @Test
  void geefBestandsPad_gooitExceptieBijOnbekendProject() {
    assertThrows(
        ProjectNietGevondenException.class,
        () -> adapter.geefBestandsPad("onbekend", "bezwaar1.txt")
    );
  }

  @Test
  void maaktProjectMapAanMetBezwarenSubmappen() throws Exception {
    adapter.maakProjectAan("nieuw-project");

    assertThat(Files.isDirectory(inputFolder.resolve("nieuw-project"))).isTrue();
    assertThat(Files.isDirectory(inputFolder.resolve("nieuw-project").resolve("bezwaren-orig"))).isTrue();
    assertThat(Files.isDirectory(inputFolder.resolve("nieuw-project").resolve("bezwaren-text"))).isTrue();
  }

  @Test
  void maakProjectAan_gooitExceptionAlsProjectAlBestaat() throws Exception {
    Files.createDirectory(inputFolder.resolve("bestaand"));

    assertThrows(
        IllegalArgumentException.class,
        () -> adapter.maakProjectAan("bestaand")
    );
  }

  @Test
  void maakProjectAan_gooitExceptionBijPathTraversal() {
    assertThrows(
        IllegalArgumentException.class,
        () -> adapter.maakProjectAan("../kwaadaardig")
    );
  }

  @Test
  void verwijdertProjectMapRecursief() throws Exception {
    var bezwarenMap = inputFolder.resolve("oud-project").resolve("bezwaren-orig");
    Files.createDirectories(bezwarenMap);
    Files.writeString(bezwarenMap.resolve("bestand.txt"), "inhoud");

    boolean result = adapter.verwijderProject("oud-project");

    assertThat(result).isTrue();
    assertThat(Files.exists(inputFolder.resolve("oud-project"))).isFalse();
  }

  @Test
  void verwijderProject_geeftFalseAlsProjectNietBestaat() {
    boolean result = adapter.verwijderProject("bestaat-niet");

    assertThat(result).isFalse();
  }

  @Test
  void verwijderProject_gooitExceptionBijPathTraversal() {
    assertThrows(
        IllegalArgumentException.class,
        () -> adapter.verwijderProject("../kwaadaardig")
    );
  }

  @Test
  void slaTekstOp_schrijftTekstNaarBezwarenTextMap() throws Exception {
    Files.createDirectories(inputFolder.resolve("windmolens").resolve("bezwaren-text"));

    adapter.slaTekstOp("windmolens", "bezwaar-001.pdf", "Geëxtraheerde tekst");

    var tekstPad = inputFolder.resolve("windmolens").resolve("bezwaren-text").resolve("bezwaar-001.txt");
    assertThat(Files.exists(tekstPad)).isTrue();
    assertThat(Files.readString(tekstPad)).isEqualTo("Geëxtraheerde tekst");
  }

  @Test
  void slaTekstOp_vervangExtensie() throws Exception {
    Files.createDirectories(inputFolder.resolve("windmolens").resolve("bezwaren-text"));

    adapter.slaTekstOp("windmolens", "document.pdf", "tekst inhoud");

    assertThat(Files.exists(
        inputFolder.resolve("windmolens").resolve("bezwaren-text").resolve("document.txt")
    )).isTrue();
  }

  @Test
  void slaTekstOp_txtBestandBehoudtExtensie() throws Exception {
    Files.createDirectories(inputFolder.resolve("windmolens").resolve("bezwaren-text"));

    adapter.slaTekstOp("windmolens", "bezwaar.txt", "tekst inhoud");

    assertThat(Files.exists(
        inputFolder.resolve("windmolens").resolve("bezwaren-text").resolve("bezwaar.txt")
    )).isTrue();
  }

  @Test
  void geefTekstBestandsPad_geeftPadVoorBestaandTekstBestand() throws Exception {
    var tekstMap = inputFolder.resolve("windmolens").resolve("bezwaren-text");
    Files.createDirectories(tekstMap);
    Files.writeString(tekstMap.resolve("bezwaar1.txt"), "tekst");

    var result = adapter.geefTekstBestandsPad("windmolens", "bezwaar1.txt");

    assertThat(result).isEqualTo(tekstMap.resolve("bezwaar1.txt"));
  }

  @Test
  void geefTekstBestandsPad_gooitExceptieBijOnbekendBestand() throws Exception {
    var tekstMap = inputFolder.resolve("windmolens").resolve("bezwaren-text");
    Files.createDirectories(tekstMap);

    assertThrows(
        BestandNietGevondenException.class,
        () -> adapter.geefTekstBestandsPad("windmolens", "bestaat-niet.txt")
    );
  }

  @Test
  void geefTekstBestandsPad_gooitExceptieBijPathTraversal() throws Exception {
    Files.createDirectories(inputFolder.resolve("windmolens").resolve("bezwaren-text"));

    assertThrows(
        IllegalArgumentException.class,
        () -> adapter.geefTekstBestandsPad("windmolens", "../etc/passwd")
    );
  }

  @Test
  void verwijderBestandRuimtOokTekstBestandOp() throws Exception {
    // Setup: project met origineel PDF en geëxtraheerd tekstbestand
    adapter.maakProjectAan("test-project");
    adapter.slaBestandOp("test-project", "v2-001.pdf", "pdf-inhoud".getBytes());
    adapter.slaTekstOp("test-project", "v2-001.pdf", "Geëxtraheerde tekst uit PDF");

    var origPad = inputFolder.resolve("test-project/bezwaren-orig/v2-001.pdf");
    var tekstPad = inputFolder.resolve("test-project/bezwaren-text/v2-001.txt");
    assertThat(Files.exists(origPad)).isTrue();
    assertThat(Files.exists(tekstPad)).isTrue();

    // Verwijder het bezwaarbestand
    boolean verwijderd = adapter.verwijderBestand("test-project", "v2-001.pdf");

    // Origineel bestand is verwijderd
    assertThat(verwijderd).isTrue();
    assertThat(Files.exists(origPad)).isFalse();

    // Tekstbestand moet ook verwijderd worden (BUG: dit wordt niet gedaan)
    assertThat(Files.exists(tekstPad))
        .as("Tekstbestand in bezwaren-text/ moet verwijderd worden bij verwijdering van het origineel")
        .isFalse();
  }

  @Test
  void verwijderBestandMetTxtExtensieMoetGeenTekstBestandZoeken() throws Exception {
    // Een .txt bestand heeft als tekst-versie dezelfde naam
    adapter.maakProjectAan("test-project");
    adapter.slaBestandOp("test-project", "bezwaar.txt", "inhoud".getBytes());
    adapter.slaTekstOp("test-project", "bezwaar.txt", "dezelfde inhoud");

    var origPad = inputFolder.resolve("test-project/bezwaren-orig/bezwaar.txt");
    var tekstPad = inputFolder.resolve("test-project/bezwaren-text/bezwaar.txt");
    assertThat(Files.exists(origPad)).isTrue();
    assertThat(Files.exists(tekstPad)).isTrue();

    boolean verwijderd = adapter.verwijderBestand("test-project", "bezwaar.txt");

    assertThat(verwijderd).isTrue();
    assertThat(Files.exists(origPad)).isFalse();
    assertThat(Files.exists(tekstPad))
        .as("Tekstbestand moet ook verwijderd worden")
        .isFalse();
  }
}
