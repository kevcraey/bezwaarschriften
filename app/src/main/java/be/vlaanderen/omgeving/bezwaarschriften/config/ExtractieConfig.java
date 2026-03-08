package be.vlaanderen.omgeving.bezwaarschriften.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuratie voor de asynchrone extractie-verwerking.
 *
 * <p>Stelt thread pools in voor het parallel verwerken van extractie-, consolidatie-
 * en clustering-taken.
 */
@Configuration
public class ExtractieConfig {

  @Bean
  public ThreadPoolTaskExecutor extractieExecutor(
      @Value("${bezwaarschriften.extractie.max-concurrent:3}") int maxConcurrent) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(maxConcurrent);
    executor.setMaxPoolSize(maxConcurrent);
    executor.setThreadNamePrefix("extractie-");
    executor.initialize();
    return executor;
  }

  @Bean
  public ThreadPoolTaskExecutor consolidatieExecutor(
      @Value("${bezwaarschriften.consolidatie.max-concurrent:3}") int maxConcurrent) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(maxConcurrent);
    executor.setMaxPoolSize(maxConcurrent);
    executor.setThreadNamePrefix("consolidatie-");
    executor.initialize();
    return executor;
  }

  @Bean
  public ThreadPoolTaskExecutor clusteringExecutor(
      @Value("${bezwaarschriften.clustering.max-concurrent:2}") int maxConcurrent) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(maxConcurrent);
    executor.setMaxPoolSize(maxConcurrent);
    executor.setThreadNamePrefix("clustering-");
    executor.initialize();
    return executor;
  }

  @Bean
  public ThreadPoolTaskExecutor tekstExtractieExecutor(
      @Value("${bezwaarschriften.tekst-extractie.max-concurrent:2}") int maxConcurrent) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(maxConcurrent);
    executor.setMaxPoolSize(maxConcurrent);
    executor.setThreadNamePrefix("tekst-extractie-");
    executor.initialize();
    return executor;
  }
}
