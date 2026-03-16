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

  public ObscuroAdapter(PseudonimiseringConfig config, ObjectMapper objectMapper) {
    this.config = config;
    this.objectMapper = objectMapper;
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
        var foutmelding = parseerObscuroFout(response.statusCode(), response.body());
        throw new PseudonimiseringException(foutmelding);
      }

      var json = objectMapper.readTree(response.body());
      var tekstNode = json.get("text");
      var mappingNode = json.get("mapping_id");
      if (tekstNode == null || mappingNode == null) {
        throw new PseudonimiseringException(
            "Obscuro response mist verplichte velden (text/mapping_id): " + response.body());
      }
      var gepseudonimiseerdeTekst = tekstNode.asText();
      var mappingId = mappingNode.asText();

      LOGGER.info("Tekst gepseudonimiseerd (mapping={})", mappingId);
      return new PseudonimiseringResultaat(gepseudonimiseerdeTekst, mappingId);

    } catch (PseudonimiseringException e) {
      throw e;
    } catch (Exception e) {
      throw new PseudonimiseringException("Pseudonimisering mislukt: " + e.getMessage(), e);
    }
  }

  private String parseerObscuroFout(int statusCode, String body) {
    if (statusCode == 422) {
      try {
        var json = objectMapper.readTree(body);
        var detail = json.get("detail");
        if (detail != null && detail.isArray() && detail.size() > 0) {
          var msg = detail.get(0).get("msg").asText();
          if (msg.contains("maximaal") && msg.contains("tekens")) {
            return "Tekst te lang voor pseudonimisering: " + msg;
          }
        }
      } catch (Exception e) {
        LOGGER.debug("Kon Obscuro 422-response niet parsen: {}", e.getMessage());
      }
    }
    return "Obscuro retourneerde HTTP " + statusCode + ": " + body;
  }
}
