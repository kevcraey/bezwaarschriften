package be.vlaanderen.omgeving.bezwaarschriften.project;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.util.HttpClientOptions;
import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.List;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Azure OpenAI implementatie van {@link ChatModelPoort}.
 *
 * <p>Stuurt prompts naar een Azure OpenAI deployment en retourneert het antwoord.
 */
public class AzureChatModel implements ChatModelPoort {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final BiFunction<String, ChatCompletionsOptions, ChatCompletions> completionsFunctie;
  private final String deploymentName;

  /**
   * Maakt een nieuwe AzureChatModel aan voor productiegebruik.
   *
   * @param endpoint Azure OpenAI endpoint URL
   * @param deploymentName naam van de deployment (bv. "gpt-5-nano")
   * @param apiKey Azure OpenAI API key
   */
  public AzureChatModel(String endpoint, String deploymentName, String apiKey) {
    this(buildClient(endpoint, apiKey), deploymentName);
  }

  /**
   * Constructor voor testgebruik — injectie van een bestaande client.
   */
  AzureChatModel(OpenAIClient client, String deploymentName) {
    this(client::getChatCompletions, deploymentName);
  }

  /**
   * Constructor met functionele abstractie — voor testen zonder mocking van final classes.
   */
  AzureChatModel(
      BiFunction<String, ChatCompletionsOptions, ChatCompletions> completionsFunctie,
      String deploymentName) {
    this.completionsFunctie = completionsFunctie;
    this.deploymentName = deploymentName;
  }

  private static OpenAIClient buildClient(String endpoint, String apiKey) {
    var httpClientOptions = new HttpClientOptions()
        .setResponseTimeout(Duration.ofMinutes(5))
        .setReadTimeout(Duration.ofMinutes(5));
    return new OpenAIClientBuilder()
        .credential(new AzureKeyCredential(apiKey))
        .endpoint(endpoint)
        .clientOptions(httpClientOptions)
        .buildClient();
  }

  @Override
  public String chat(String systemPrompt, String userPrompt) {
    LOGGER.debug("Azure OpenAI call naar deployment '{}'", deploymentName);

    var options = new ChatCompletionsOptions(List.of(
        new ChatRequestSystemMessage(systemPrompt),
        new ChatRequestUserMessage(userPrompt)));

    var completions = completionsFunctie.apply(deploymentName, options);
    var keuze = completions.getChoices().get(0);
    var antwoord = keuze.getMessage().getContent();

    LOGGER.debug("Azure OpenAI antwoord ontvangen ({} chars)", antwoord.length());
    return antwoord;
  }
}
