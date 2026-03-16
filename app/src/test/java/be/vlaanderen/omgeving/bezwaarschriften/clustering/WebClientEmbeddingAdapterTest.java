package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class WebClientEmbeddingAdapterTest {

  private MockWebServer mockServer;
  private WebClientEmbeddingAdapter adapter;

  @BeforeEach
  void setUp() throws IOException {
    mockServer = new MockWebServer();
    mockServer.start();

    var config = new EmbeddingConfig();
    config.setProvider("ollama");
    config.setModel("bge-m3");
    config.setDimensions(3);
    config.setOllamaUrl(mockServer.url("/").toString());

    adapter = new WebClientEmbeddingAdapter(
        WebClient.builder(), config);
  }

  @AfterEach
  void tearDown() throws IOException {
    mockServer.shutdown();
  }

  @Test
  void genereertEmbeddingsViaOllamaBatch() throws InterruptedException {
    mockServer.enqueue(new MockResponse()
        .setBody("{\"embeddings\":[[0.1,0.2,0.3],[0.4,0.5,0.6]]}")
        .addHeader("Content-Type", "application/json"));

    var resultaat = adapter.genereerEmbeddings(
        List.of("tekst een", "tekst twee"));

    assertThat(resultaat).hasSize(2);
    assertThat(resultaat.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
    assertThat(resultaat.get(1)).containsExactly(0.4f, 0.5f, 0.6f);

    // Verifieer dat er slechts 1 request is gedaan (batch)
    assertThat(mockServer.getRequestCount()).isEqualTo(1);
    var request = mockServer.takeRequest();
    assertThat(request.getPath()).isEqualTo("/api/embed");
  }

  @Test
  void legeInvoerGeeftLeegResultaat() {
    var resultaat = adapter.genereerEmbeddings(List.of());
    assertThat(resultaat).isEmpty();
  }
}
