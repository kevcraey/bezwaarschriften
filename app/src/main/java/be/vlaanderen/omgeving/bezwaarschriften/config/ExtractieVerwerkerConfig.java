package be.vlaanderen.omgeving.bezwaarschriften.config;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import be.vlaanderen.omgeving.bezwaarschriften.project.AiExtractieVerwerker;
import be.vlaanderen.omgeving.bezwaarschriften.project.ChatModelPoort;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieVerwerker;
import be.vlaanderen.omgeving.bezwaarschriften.project.FixtureChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuratie voor fixture-gebaseerde extractie in test-modus.
 *
 * <p>Activeert {@link FixtureChatModel} en {@link AiExtractieVerwerker} wanneer
 * het "fixture" profiel actief is.
 */
@Configuration
@Profile("fixture")
public class ExtractieVerwerkerConfig {

  @Bean
  ChatModelPoort fixtureChatModel(
      @Value("${bezwaarschriften.testdata.pad}") String testdataPad) {
    return new FixtureChatModel(testdataPad);
  }

  @Bean
  ExtractieVerwerker aiExtractieVerwerker(
      ChatModelPoort chatModel,
      IngestiePoort ingestiePoort,
      @Value("${bezwaarschriften.input.folder}") String inputFolder) {
    return new AiExtractieVerwerker(chatModel, ingestiePoort, inputFolder);
  }
}
