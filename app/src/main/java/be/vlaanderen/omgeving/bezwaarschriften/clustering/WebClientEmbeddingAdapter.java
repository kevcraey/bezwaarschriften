package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Embedding-adapter die via WebClient een Ollama of OpenAI embedding API aanroept.
 */
@Component
public class WebClientEmbeddingAdapter implements EmbeddingPoort {

  private final WebClient webClient;
  private final EmbeddingConfig config;

  /**
   * Maakt een nieuwe WebClientEmbeddingAdapter aan.
   *
   * @param webClientBuilder builder voor de WebClient
   * @param config embedding-configuratie
   */
  public WebClientEmbeddingAdapter(WebClient.Builder webClientBuilder,
      EmbeddingConfig config) {
    this.webClient = webClientBuilder.build();
    this.config = config;
  }

  @Override
  public List<float[]> genereerEmbeddings(List<String> teksten) {
    if (teksten.isEmpty()) {
      return List.of();
    }
    var resultaat = new ArrayList<float[]>();
    for (var tekst : teksten) {
      resultaat.add(genereerEmbedding(tekst));
    }
    return resultaat;
  }

  private float[] genereerEmbedding(String tekst) {
    if ("ollama".equals(config.getProvider())) {
      return ollamaEmbedding(tekst);
    }
    return openaiEmbedding(tekst);
  }

  @SuppressWarnings("unchecked")
  private float[] ollamaEmbedding(String tekst) {
    var body = Map.of("model", config.getModel(), "prompt", tekst);
    var response = webClient.post()
        .uri(config.getOllamaUrl() + "/api/embeddings")
        .bodyValue(body)
        .retrieve()
        .bodyToMono(Map.class)
        .block();
    var embedding = (List<Number>) response.get("embedding");
    return toFloatArray(embedding);
  }

  @SuppressWarnings("unchecked")
  private float[] openaiEmbedding(String tekst) {
    var body = Map.of(
        "model", config.getModel(),
        "input", tekst);
    var response = webClient.post()
        .uri(config.getOpenaiUrl() + "/embeddings")
        .header("Authorization", "Bearer " + config.getOpenaiKey())
        .bodyValue(body)
        .retrieve()
        .bodyToMono(Map.class)
        .block();
    var data = (List<Map<String, Object>>) response.get("data");
    var embedding = (List<Number>) data.get(0).get("embedding");
    return toFloatArray(embedding);
  }

  private float[] toFloatArray(List<Number> numbers) {
    var result = new float[numbers.size()];
    for (int i = 0; i < numbers.size(); i++) {
      result[i] = numbers.get(i).floatValue();
    }
    return result;
  }
}
