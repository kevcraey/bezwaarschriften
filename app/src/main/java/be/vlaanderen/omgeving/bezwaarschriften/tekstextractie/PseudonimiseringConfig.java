package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuratie voor de Obscuro pseudonimiseringsservice. */
@Component
@ConfigurationProperties(prefix = "bezwaarschriften.pseudonimisering")
public class PseudonimiseringConfig {

  private String url = "http://localhost:8000";
  private int ttlSeconds = 31536000;
  private int connectTimeoutMs = 30000;
  private int readTimeoutMs = 120000;

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public int getTtlSeconds() {
    return ttlSeconds;
  }

  public void setTtlSeconds(int ttlSeconds) {
    this.ttlSeconds = ttlSeconds;
  }

  public int getConnectTimeoutMs() {
    return connectTimeoutMs;
  }

  public void setConnectTimeoutMs(int connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
  }

  public int getReadTimeoutMs() {
    return readTimeoutMs;
  }

  public void setReadTimeoutMs(int readTimeoutMs) {
    this.readTimeoutMs = readTimeoutMs;
  }
}
