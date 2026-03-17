package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Embedding-adapter via WebClient naar Ollama/OpenAI.
 *
 * <p>Gebruikt batch-endpoints: Ollama {@code /api/embed}
 * en OpenAI {@code /embeddings} met {@code input}-lijst.
 * Eén HTTP-call per batch i.p.v. één per tekst.
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
    this.webClient = webClientBuilder
        .exchangeStrategies(ExchangeStrategies.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build())
        .build();
    this.config = config;
  }

  @Override
  public List<float[]> genereerEmbeddings(List<String> teksten) {
    if (teksten.isEmpty()) {
      return List.of();
    }
    if ("ollama".equals(config.getProvider())) {
      return ollamaBatchEmbeddings(teksten);
    }
    return openaiBatchEmbeddings(teksten);
  }

  @SuppressWarnings("unchecked")
  private List<float[]> ollamaBatchEmbeddings(List<String> teksten) {
    var body = Map.of("model", config.getModel(), "input", teksten);
    var response = webClient.post()
        .uri(config.getOllamaUrl() + "/api/embed")
        .bodyValue(body)
        .retrieve()
        .bodyToMono(Map.class)
        .block();
    var embeddings = (List<List<Number>>) response.get("embeddings");
    var resultaat = new ArrayList<float[]>(embeddings.size());
    for (var embedding : embeddings) {
      resultaat.add(toFloatArray(embedding));
    }
    return resultaat;
  }

  @SuppressWarnings("unchecked")
  private List<float[]> openaiBatchEmbeddings(List<String> teksten) {
    var body = Map.of(
        "model", config.getModel(),
        "input", teksten);
    var response = webClient.post()
        .uri(config.getOpenaiUrl() + "/embeddings")
        .header("Authorization", "Bearer " + config.getOpenaiKey())
        .bodyValue(body)
        .retrieve()
        .bodyToMono(Map.class)
        .block();
    var data = (List<Map<String, Object>>) response.get("data");
    var resultaat = new ArrayList<float[]>(data.size());
    for (var item : data) {
      resultaat.add(toFloatArray((List<Number>) item.get("embedding")));
    }
    return resultaat;
  }

  private float[] toFloatArray(List<Number> numbers) {
    var result = new float[numbers.size()];
    for (int i = 0; i < numbers.size(); i++) {
      result[i] = numbers.get(i).floatValue();
    }
    return result;
  }
}
