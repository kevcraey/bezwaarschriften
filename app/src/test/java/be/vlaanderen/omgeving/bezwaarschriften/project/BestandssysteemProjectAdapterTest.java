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
    var bezwarenMap = inputFolder.resolve("windmolens").resolve("bezwaren");
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
    Files.createDirectories(inputFolder.resolve("windmolens").resolve("bezwaren"));

    var bestandsnamen = adapter.geefBestandsnamen("windmolens");

    assertThat(bestandsnamen).isEmpty();
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
}
