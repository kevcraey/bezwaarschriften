package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.json.JsonProviders;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class AzureChatModelTest {

  private static final String COMPLETIONS_JSON = """
      {
        "id": "chatcmpl-test",
        "choices": [
          {
            "index": 0,
            "message": {
              "role": "assistant",
              "content": "{\\"passages\\": []}"
            },
            "finish_reason": "stop"
          }
        ],
        "model": "gpt-5-nano",
        "usage": {
          "prompt_tokens": 10,
          "completion_tokens": 5,
          "total_tokens": 15
        }
      }
      """;

  private static ChatCompletions completionsVanJson(String json) {
    try (var reader = JsonProviders.createReader(
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))) {
      return ChatCompletions.fromJson(reader);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void stuurtPromptEnRetourneertAntwoord() {
    var gevraagdeOptions = new AtomicReference<ChatCompletionsOptions>();
    var gevraagdDeployment = new AtomicReference<String>();

    BiFunction<String, ChatCompletionsOptions, ChatCompletions> stubFunctie =
        (deployment, options) -> {
          gevraagdDeployment.set(deployment);
          gevraagdeOptions.set(options);
          return completionsVanJson(COMPLETIONS_JSON);
        };

    var chatModel = new AzureChatModel(stubFunctie, "test-deployment");

    var result = chatModel.chat("system prompt", "user prompt");

    assertEquals("{\"passages\": []}", result);
    assertEquals("test-deployment", gevraagdDeployment.get());
    assertEquals(2, gevraagdeOptions.get().getMessages().size());
  }

  @Test
  void gooitExceptieBijApiFout() {
    BiFunction<String, ChatCompletionsOptions, ChatCompletions> faalendeFunc =
        (deployment, options) -> {
          throw new RuntimeException("API error");
        };

    var chatModel = new AzureChatModel(faalendeFunc, "test-deployment");

    assertThrows(RuntimeException.class,
        () -> chatModel.chat("system", "user"));
  }
}
