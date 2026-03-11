package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ObscuroAdapterTest {

  private MockWebServer mockServer;
  private ObscuroAdapter adapter;

  @BeforeEach
  void setUp() throws IOException {
    mockServer = new MockWebServer();
    mockServer.start();

    var config = new PseudonimiseringConfig();
    config.setUrl(mockServer.url("/").toString());
    config.setTtlSeconds(31536000);
    config.setConnectTimeoutMs(5000);
    config.setReadTimeoutMs(5000);

    adapter = new ObscuroAdapter(config, new ObjectMapper());
  }

  @AfterEach
  void tearDown() throws IOException {
    mockServer.shutdown();
  }

  @Test
  void pseudonimiseertTekstSuccesvol() throws InterruptedException {
    mockServer.enqueue(
        new MockResponse()
            .setBody(
                "{\"text\":\"Jan woont in {adres_1} en zijn IBAN is {rekeningnummer_1}.\","
                    + "\"mapping_id\":\"550e8400-e29b-41d4-a716-446655440000\"}")
            .addHeader("Content-Type", "application/json"));

    var resultaat =
        adapter.pseudonimiseer("Jan woont in Gent en zijn IBAN is BE12 3456 7890 1234.");

    assertThat(resultaat.gepseudonimiseerdeTekst())
        .contains("{adres_1}")
        .contains("{rekeningnummer_1}")
        .doesNotContain("Gent")
        .doesNotContain("BE12");
    assertThat(resultaat.mappingId())
        .isEqualTo("550e8400-e29b-41d4-a716-446655440000");

    RecordedRequest request = mockServer.takeRequest();
    assertThat(request.getPath()).isEqualTo("/pseudonymize");
    assertThat(request.getBody().readUtf8()).contains("\"ttl_seconds\":31536000");
  }

  @Test
  void gooitExceptieBijHttpFout() {
    mockServer.enqueue(
        new MockResponse()
            .setResponseCode(500)
            .setBody("{\"detail\":\"Internal server error\"}"));

    assertThatThrownBy(() -> adapter.pseudonimiseer("test tekst"))
        .isInstanceOf(PseudonimiseringException.class)
        .hasMessageContaining("500");
  }

  @Test
  void gooitExceptieBijOnbereikbareService() throws IOException {
    mockServer.shutdown();

    assertThatThrownBy(() -> adapter.pseudonimiseer("test tekst"))
        .isInstanceOf(PseudonimiseringException.class);
  }
}
