package be.vlaanderen.omgeving.bezwaarschriften.config;

import be.vlaanderen.omgeving.bezwaarschriften.project.BestandNietGevondenException;
import be.vlaanderen.omgeving.bezwaarschriften.project.ProjectNietGevondenException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Gecentraliseerde exception handling voor REST controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Handelt ProjectNietGevondenException af met HTTP 404.
   *
   * @param e De exception
   * @return Gestructureerde foutrespons
   */
  @ExceptionHandler(ProjectNietGevondenException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, List<Map<String, Object>>> handleProjectNietGevonden(
      ProjectNietGevondenException e) {
    return Map.of("messages", List.of(
        Map.of("code", "project.not-found",
            "parameters", Map.of("naam", e.getProjectNaam()))
    ));
  }

  /**
   * Handelt BestandNietGevondenException af met HTTP 404.
   */
  @ExceptionHandler(BestandNietGevondenException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, List<Map<String, Object>>> handleBestandNietGevonden(
      BestandNietGevondenException e) {
    return Map.of("messages", List.of(
        Map.of("code", "bestand.not-found",
            "parameters", Map.of("bestandsnaam", e.getBestandsnaam()))
    ));
  }

  /**
   * Handelt IllegalArgumentException af met HTTP 400.
   */
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, List<Map<String, Object>>> handleOngeldigArgument(
      IllegalArgumentException e) {
    return Map.of("messages", List.of(
        Map.of("code", "invalid.argument",
            "parameters", Map.of("message", e.getMessage()))
    ));
  }
}
