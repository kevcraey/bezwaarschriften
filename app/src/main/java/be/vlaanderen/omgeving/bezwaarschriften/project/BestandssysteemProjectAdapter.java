package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adapter die projecten en bezwaarbestanden leest van het bestandssysteem.
 */
@Component
public final class BestandssysteemProjectAdapter implements ProjectPoort {

  /** Logger voor deze klasse. */
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /** Pad naar de input-folder met projecten. */
  private final Path inputFolder;

  /**
   * Maakt een nieuwe BestandssysteemProjectAdapter aan.
   *
   * @param folder Pad naar de input-folder met projecten
   */
  public BestandssysteemProjectAdapter(
      @Value("${bezwaarschriften.input.folder}") final String folder) {
    this.inputFolder = Path.of(folder);
  }

  @Override
  public List<String> geefProjecten() {
    if (!Files.exists(inputFolder)) {
      return Collections.emptyList();
    }
    try (var stream = Files.list(inputFolder)) {
      return stream
          .filter(Files::isDirectory)
          .map(pad -> pad.getFileName().toString())
          .toList();
    } catch (IOException e) {
      LOGGER.warn("Kon input-folder niet lezen: {}", inputFolder, e);
      return Collections.emptyList();
    }
  }

  @Override
  public List<String> geefBestandsnamen(final String projectNaam) {
    var bezwarenPad = resolveEnValideerBezwarenPad(projectNaam);
    if (!Files.isDirectory(bezwarenPad)) {
      return Collections.emptyList();
    }
    try (var stream = Files.list(bezwarenPad)) {
      return stream
          .filter(pad -> !Files.isDirectory(pad))
          .filter(pad -> !pad.getFileName().toString().startsWith("."))
          .map(pad -> pad.getFileName().toString())
          .toList();
    } catch (IOException e) {
      LOGGER.warn("Kon bezwaren-map niet lezen voor project '{}': {}",
          projectNaam, bezwarenPad, e);
      return Collections.emptyList();
    }
  }

  @Override
  public void slaBestandOp(final String projectNaam, final String bestandsnaam,
      final byte[] inhoud) {
    var bezwarenPad = resolveEnValideerBezwarenPad(projectNaam);
    var doelPad = bezwarenPad.resolve(bestandsnaam).normalize();
    if (!doelPad.startsWith(bezwarenPad)) {
      throw new IllegalArgumentException("Ongeldige bestandsnaam: " + bestandsnaam);
    }
    try {
      Files.createDirectories(bezwarenPad);
      Files.write(doelPad, inhoud);
      LOGGER.info("Bestand '{}' opgeslagen voor project '{}'", bestandsnaam, projectNaam);
    } catch (IOException e) {
      throw new RuntimeException("Kon bestand niet opslaan: " + bestandsnaam, e);
    }
  }

  @Override
  public boolean verwijderBestand(final String projectNaam, final String bestandsnaam) {
    var bezwarenPad = resolveEnValideerBezwarenPad(projectNaam);
    var doelPad = bezwarenPad.resolve(bestandsnaam).normalize();
    if (!doelPad.startsWith(bezwarenPad)) {
      throw new IllegalArgumentException("Ongeldige bestandsnaam: " + bestandsnaam);
    }
    try {
      boolean verwijderd = Files.deleteIfExists(doelPad);
      if (verwijderd) {
        LOGGER.info("Bestand '{}' verwijderd voor project '{}'", bestandsnaam, projectNaam);
      }
      return verwijderd;
    } catch (IOException e) {
      throw new RuntimeException("Kon bestand niet verwijderen: " + bestandsnaam, e);
    }
  }

  @Override
  public Path geefBestandsPad(final String projectNaam, final String bestandsnaam) {
    if (bestandsnaam.contains("..") || bestandsnaam.contains("/")
        || bestandsnaam.contains("\\")) {
      throw new IllegalArgumentException("Ongeldige bestandsnaam: " + bestandsnaam);
    }
    var bezwarenPad = resolveEnValideerBezwarenPad(projectNaam);
    var bestandsPad = bezwarenPad.resolve(bestandsnaam).normalize();
    if (!bestandsPad.startsWith(bezwarenPad) || !Files.exists(bestandsPad)) {
      throw new BestandNietGevondenException(bestandsnaam);
    }
    return bestandsPad;
  }

  @Override
  public void maakProjectAan(final String naam) {
    var projectPad = inputFolder.resolve(naam).normalize();
    if (!projectPad.startsWith(inputFolder.normalize())) {
      throw new IllegalArgumentException("Ongeldige projectnaam: " + naam);
    }
    if (Files.exists(projectPad)) {
      throw new IllegalArgumentException("Project bestaat al: " + naam);
    }
    try {
      Files.createDirectories(projectPad.resolve("bezwaren-orig"));
      Files.createDirectories(projectPad.resolve("bezwaren-text"));
      LOGGER.info("Project '{}' aangemaakt", naam);
    } catch (IOException e) {
      throw new RuntimeException("Kon project niet aanmaken: " + naam, e);
    }
  }

  @Override
  public boolean verwijderProject(final String naam) {
    var projectPad = inputFolder.resolve(naam).normalize();
    if (!projectPad.startsWith(inputFolder.normalize())) {
      throw new IllegalArgumentException("Ongeldige projectnaam: " + naam);
    }
    if (!Files.isDirectory(projectPad)) {
      return false;
    }
    try (Stream<Path> walk = Files.walk(projectPad)) {
      walk.sorted(Comparator.reverseOrder()).forEach(pad -> {
        try {
          Files.delete(pad);
        } catch (IOException e) {
          throw new RuntimeException("Kon niet verwijderen: " + pad, e);
        }
      });
      LOGGER.info("Project '{}' verwijderd", naam);
      return true;
    } catch (IOException e) {
      throw new RuntimeException("Kon project niet verwijderen: " + naam, e);
    }
  }

  /**
   * Valideert de projectnaam en geeft het pad naar de bezwaren-map terug.
   *
   * @param projectNaam Naam van het project
   * @return Pad naar de bezwaren-map van het project
   * @throws ProjectNietGevondenException Als het project niet bestaat of de naam ongeldig is
   */
  private Path resolveEnValideerBezwarenPad(final String projectNaam) {
    var projectPad = inputFolder.resolve(projectNaam).normalize();
    if (!projectPad.startsWith(inputFolder.normalize())) {
      throw new ProjectNietGevondenException(projectNaam);
    }
    if (!Files.isDirectory(projectPad)) {
      throw new ProjectNietGevondenException(projectNaam);
    }
    return projectPad.resolve("bezwaren-orig");
  }

  /**
   * Valideert de projectnaam en geeft het pad naar de bezwaren-text map terug.
   *
   * @param projectNaam Naam van het project
   * @return Pad naar de bezwaren-text map van het project
   * @throws ProjectNietGevondenException Als het project niet bestaat of de naam ongeldig is
   */
  private Path resolveEnValideerTekstPad(final String projectNaam) {
    var projectPad = inputFolder.resolve(projectNaam).normalize();
    if (!projectPad.startsWith(inputFolder.normalize())) {
      throw new ProjectNietGevondenException(projectNaam);
    }
    if (!Files.isDirectory(projectPad)) {
      throw new ProjectNietGevondenException(projectNaam);
    }
    return projectPad.resolve("bezwaren-text");
  }

  @Override
  public void slaTekstOp(final String projectNaam, final String bestandsnaam,
      final String tekst) {
    var tekstPad = resolveEnValideerTekstPad(projectNaam);
    var txtNaam = vervangExtensieDoorTxt(bestandsnaam);
    var doelPad = tekstPad.resolve(txtNaam).normalize();
    if (!doelPad.startsWith(tekstPad)) {
      throw new IllegalArgumentException("Ongeldige bestandsnaam: " + bestandsnaam);
    }
    try {
      Files.createDirectories(tekstPad);
      Files.writeString(doelPad, tekst, StandardCharsets.UTF_8);
      LOGGER.info("Tekst '{}' opgeslagen voor project '{}'", txtNaam, projectNaam);
    } catch (IOException e) {
      throw new RuntimeException("Kon tekst niet opslaan: " + txtNaam, e);
    }
  }

  @Override
  public Path geefTekstBestandsPad(final String projectNaam, final String bestandsnaam) {
    if (bestandsnaam.contains("..") || bestandsnaam.contains("/")
        || bestandsnaam.contains("\\")) {
      throw new IllegalArgumentException("Ongeldige bestandsnaam: " + bestandsnaam);
    }
    var tekstPad = resolveEnValideerTekstPad(projectNaam);
    var txtNaam = vervangExtensieDoorTxt(bestandsnaam);
    var bestandsPad = tekstPad.resolve(txtNaam).normalize();
    if (!bestandsPad.startsWith(tekstPad) || !Files.exists(bestandsPad)) {
      throw new BestandNietGevondenException(bestandsnaam);
    }
    return bestandsPad;
  }

  /**
   * Vervangt de extensie van een bestandsnaam door .txt.
   *
   * @param bestandsnaam Originele bestandsnaam
   * @return Bestandsnaam met .txt extensie
   */
  private static String vervangExtensieDoorTxt(final String bestandsnaam) {
    var dotIndex = bestandsnaam.lastIndexOf('.');
    if (dotIndex > 0) {
      return bestandsnaam.substring(0, dotIndex) + ".txt";
    }
    return bestandsnaam + ".txt";
  }
}
