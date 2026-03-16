package be.vlaanderen.omgeving.bezwaarschriften.config;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import be.vlaanderen.omgeving.bezwaarschriften.project.AiExtractieVerwerker;
import be.vlaanderen.omgeving.bezwaarschriften.project.AzureChatModel;
import be.vlaanderen.omgeving.bezwaarschriften.project.ChatModelPoort;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieVerwerker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuratie voor Azure OpenAI-gebaseerde extractie.
 *
 * <p>Activeert {@link AzureChatModel} en {@link AiExtractieVerwerker} wanneer
 * het "azure" profiel actief is.
 */
@Configuration
@Profile("azure")
public class AzureExtractieConfig {

  @Bean
  ChatModelPoort azureChatModel(
      @Value("${bezwaarschriften.azure-openai.endpoint}") String endpoint,
      @Value("${bezwaarschriften.azure-openai.deployment}") String deployment,
      @Value("${bezwaarschriften.azure-openai.api-key}") String apiKey) {
    return new AzureChatModel(endpoint, deployment, apiKey);
  }

  @Bean
  ExtractieVerwerker aiExtractieVerwerker(
      ChatModelPoort chatModel,
      IngestiePoort ingestiePoort,
      @Value("${bezwaarschriften.input.folder}") String inputFolder) {
    return new AiExtractieVerwerker(chatModel, ingestiePoort, inputFolder);
  }
}
