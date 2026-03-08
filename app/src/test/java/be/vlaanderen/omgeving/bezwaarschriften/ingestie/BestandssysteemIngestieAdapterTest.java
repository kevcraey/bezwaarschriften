package be.vlaanderen.omgeving.bezwaarschriften.ingestie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TDD Test suite voor BestandssysteemIngestieAdapter.
 *
 * <p>Test AC1: Succesvol inlezen van TXT bestand
 */
class BestandssysteemIngestieAdapterTest {

  @TempDir
  Path tempDir;

  private BestandssysteemIngestieAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new BestandssysteemIngestieAdapter();
  }

  @Test
  void leestBasisTxtBestandSuccesvol() throws Exception {
    // Given: een geldig .txt bestand met bekende inhoud
    var inhoud = "Dit is een test document met simpele tekst.\\nDit is de tweede regel.";
    var testBestand = tempDir.resolve("test-basic.txt");
    Files.writeString(testBestand, inhoud);

    // When: de service wordt aangeroepen met het bestandspad
    var resultaat = adapter.leesBestand(testBestand);

    // Then: retourneert een Brondocument met exacte inhoud
    assertThat(resultaat).isNotNull();
    assertThat(resultaat.tekst()).isEqualTo(inhoud);
    assertThat(resultaat.bestandsnaam()).isEqualTo("test-basic.txt");
    assertThat(resultaat.pad()).isEqualTo(testBestand.toString());
  }

  @Test
  void gooitExceptionBijNietBestaandBestand() {
    // Given: een pad naar een niet-bestaand bestand
    var nietBestaandBestand = tempDir.resolve("niet-bestaand.txt");

    // When/Then: FileIngestionException wordt gegooid
    assertThatThrownBy(() -> adapter.leesBestand(nietBestaandBestand))
        .isInstanceOf(FileIngestionException.class)
        .hasMessageContaining("niet-bestaand.txt")
        .hasMessageContaining("bestaat niet");
  }

  @Test
  void leestBestandMetWillekeurigeExtensieSuccesvol() throws Exception {
    // Given: een bestand met niet-.txt extensie maar tekstinhoud
    var bestand = tempDir.resolve("document.pdf");
    Files.writeString(bestand, "tekst inhoud van het bestand");

    // When: de service wordt aangeroepen
    var resultaat = adapter.leesBestand(bestand);

    // Then: retourneert een Brondocument met de inhoud
    assertThat(resultaat).isNotNull();
    assertThat(resultaat.tekst()).isEqualTo("tekst inhoud van het bestand");
    assertThat(resultaat.bestandsnaam()).isEqualTo("document.pdf");
  }

  @Test
  void leestLeegTxtBestandSuccesvol() throws Exception {
    // Given: een leeg .txt bestand
    var leegBestand = tempDir.resolve("leeg.txt");
    Files.writeString(leegBestand, "");

    // When: de service wordt aangeroepen
    var resultaat = adapter.leesBestand(leegBestand);

    // Then: retourneert een Brondocument met lege string
    assertThat(resultaat).isNotNull();
    assertThat(resultaat.tekst()).isEmpty();
    assertThat(resultaat.bestandsnaam()).isEqualTo("leeg.txt");
  }

  @Test
  void gooitExceptionBijBestandGroterDan50MB() throws Exception {
    // Given: een bestand groter dan 50MB
    var grootBestand = tempDir.resolve("groot.txt");
    // Simuleer 51 MB (51 * 1024 * 1024 bytes)
    var grootteInBytes = 51L * 1024 * 1024;
    Files.writeString(grootBestand, "x".repeat((int) Math.min(grootteInBytes, Integer.MAX_VALUE)));

    // When/Then: FileIngestionException wordt gegooid
    assertThatThrownBy(() -> adapter.leesBestand(grootBestand))
        .isInstanceOf(FileIngestionException.class)
        .hasMessageContaining("50 MB")
        .hasMessageContaining("groot.txt");
  }

  @Test
  void gooitExceptionBijNullParameter() {
    // When/Then: FileIngestionException wordt gegooid
    assertThatThrownBy(() -> adapter.leesBestand(null))
        .isInstanceOf(FileIngestionException.class)
        .hasMessageContaining("null");
  }

  @Test
  void gooitExceptionBijDirectory() throws Exception {
    // Given: een directory
    var directory = tempDir.resolve("test-dir");
    Files.createDirectory(directory);

    // When/Then: FileIngestionException wordt gegooid
    assertThatThrownBy(() -> adapter.leesBestand(directory))
        .isInstanceOf(FileIngestionException.class)
        .hasMessageContaining("directory")
        .hasMessageContaining("test-dir");
  }
}
