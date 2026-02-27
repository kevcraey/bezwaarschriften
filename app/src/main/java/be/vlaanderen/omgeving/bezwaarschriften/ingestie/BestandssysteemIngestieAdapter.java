package be.vlaanderen.omgeving.bezwaarschriften.ingestie;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Adapter voor het inlezen van bestanden vanuit het bestandssysteem.
 *
 * <p>Implementeert file ingestie volgens hexagonale architectuur.
 */
@Service
public class BestandssysteemIngestieAdapter implements IngestiePoort {

  private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024; // 50 MB

  @Override
  public Brondocument leesBestand(Path pad) {
    // Valideer null parameter
    if (pad == null) {
      throw new FileIngestionException("Bestandspad mag niet null zijn");
    }

    // Valideer extensie
    var bestandsnaam = pad.getFileName().toString();
    if (!bestandsnaam.toLowerCase().endsWith(".txt")) {
      throw new FileIngestionException(
          String.format("Bestand '%s' heeft geen .txt extensie. "
              + "Alleen .txt bestanden worden ondersteund.", bestandsnaam)
      );
    }

    // Valideer dat het een bestand is en geen directory
    if (Files.isDirectory(pad)) {
      throw new FileIngestionException(
          String.format("'%s' is een directory, geen bestand", bestandsnaam)
      );
    }

    try {
      // Valideer bestandsgrootte
      var fileSize = Files.size(pad);
      if (fileSize > MAX_FILE_SIZE_BYTES) {
        throw new FileIngestionException(
            String.format("Bestand '%s' is te groot (%d MB). "
                + "Maximum toegestane grootte is 50 MB.",
                bestandsnaam, fileSize / (1024 * 1024))
        );
      }

      // Lees bestand
      var tekst = Files.readString(pad, StandardCharsets.UTF_8);
      var timestamp = Instant.now();

      return new Brondocument(
          tekst,
          bestandsnaam,
          pad.toString(),
          timestamp
      );
    } catch (NoSuchFileException e) {
      throw new FileIngestionException(
          String.format("Bestand '%s' bestaat niet", bestandsnaam),
          e
      );
    } catch (IOException e) {
      throw new FileIngestionException(
          String.format("Kon bestand '%s' niet inlezen", bestandsnaam),
          e
      );
    }
  }
}
