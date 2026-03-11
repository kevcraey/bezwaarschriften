package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter die teksten pseudonimiseert via de Obscuro REST API.
 *
 * <p>Vervangt PII (namen, adressen, IBAN, etc.) door generieke tokens. Gebruikt Java HttpClient
 * voor synchrone HTTP-calls.
 */
@Component
public class ObscuroAdapter implements PseudonimiseringPoort {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final PseudonimiseringConfig config;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public ObscuroAdapter(PseudonimiseringConfig config) {
    this.config = config;
    this.objectMapper = new ObjectMapper();
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
            .build();
  }

  @Override
  public PseudonimiseringResultaat pseudonimiseer(String tekst) {
    try {
      var body =
          objectMapper.writeValueAsString(
              Map.of("text", tekst, "ttl_seconds", config.getTtlSeconds()));

      var request =
          HttpRequest.newBuilder()
              .uri(URI.create(config.getUrl().replaceAll("/+$", "") + "/pseudonymize"))
              .header("Content-Type", "application/json")
              .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();

      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new PseudonimiseringException(
            "Obscuro retourneerde HTTP " + response.statusCode() + ": " + response.body());
      }

      var json = objectMapper.readTree(response.body());
      var gepseudonimiseerdeTekst = json.get("text").asText();
      var mappingId = json.get("mapping_id").asText();

      LOGGER.info("Tekst gepseudonimiseerd (mapping={})", mappingId);
      return new PseudonimiseringResultaat(gepseudonimiseerdeTekst, mappingId);

    } catch (PseudonimiseringException e) {
      throw e;
    } catch (Exception e) {
      throw new PseudonimiseringException("Pseudonimisering mislukt: " + e.getMessage(), e);
    }
  }
}
