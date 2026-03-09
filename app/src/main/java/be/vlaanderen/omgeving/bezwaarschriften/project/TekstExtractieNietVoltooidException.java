package be.vlaanderen.omgeving.bezwaarschriften.project;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class TekstExtractieNietVoltooidException extends RuntimeException {
  public TekstExtractieNietVoltooidException(String bestandsnaam) {
    super("Kan geen bezwaren extraheren zonder eerst tekst te extraheren: " + bestandsnaam);
  }
}
