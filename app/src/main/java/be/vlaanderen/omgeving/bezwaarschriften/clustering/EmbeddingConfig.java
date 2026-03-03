package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuratie voor de embedding-provider.
 */
@Component
@ConfigurationProperties(prefix = "bezwaarschriften.embedding")
public class EmbeddingConfig {

  private String provider = "ollama";
  private String model = "bge-m3";
  private int dimensions = 1024;
  private String ollamaUrl = "http://localhost:11434";
  private String openaiUrl = "https://api.openai.com/v1";
  private String openaiKey = "";

  /**
   * Geeft de embedding-provider terug.
   *
   * @return de provider
   */
  public String getProvider() {
    return provider;
  }

  /**
   * Stelt de embedding-provider in.
   *
   * @param provider de provider
   */
  public void setProvider(String provider) {
    this.provider = provider;
  }

  /**
   * Geeft het model terug.
   *
   * @return het model
   */
  public String getModel() {
    return model;
  }

  /**
   * Stelt het model in.
   *
   * @param model het model
   */
  public void setModel(String model) {
    this.model = model;
  }

  /**
   * Geeft het aantal dimensies terug.
   *
   * @return het aantal dimensies
   */
  public int getDimensions() {
    return dimensions;
  }

  /**
   * Stelt het aantal dimensies in.
   *
   * @param dimensions het aantal dimensies
   */
  public void setDimensions(int dimensions) {
    this.dimensions = dimensions;
  }

  /**
   * Geeft de Ollama-URL terug.
   *
   * @return de Ollama-URL
   */
  public String getOllamaUrl() {
    return ollamaUrl;
  }

  /**
   * Stelt de Ollama-URL in.
   *
   * @param ollamaUrl de Ollama-URL
   */
  public void setOllamaUrl(String ollamaUrl) {
    this.ollamaUrl = ollamaUrl;
  }

  /**
   * Geeft de OpenAI-URL terug.
   *
   * @return de OpenAI-URL
   */
  public String getOpenaiUrl() {
    return openaiUrl;
  }

  /**
   * Stelt de OpenAI-URL in.
   *
   * @param openaiUrl de OpenAI-URL
   */
  public void setOpenaiUrl(String openaiUrl) {
    this.openaiUrl = openaiUrl;
  }

  /**
   * Geeft de OpenAI API-sleutel terug.
   *
   * @return de API-sleutel
   */
  public String getOpenaiKey() {
    return openaiKey;
  }

  /**
   * Stelt de OpenAI API-sleutel in.
   *
   * @param openaiKey de API-sleutel
   */
  public void setOpenaiKey(String openaiKey) {
    this.openaiKey = openaiKey;
  }
}
