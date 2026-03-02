package be.vlaanderen.omgeving.bezwaarschriften.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuratie voor de asynchrone extractie-verwerking.
 *
 * <p>Stelt een thread pool in voor het parallel verwerken van extractie-taken
 * en activeert scheduling voor periodieke polling.
 */
@Configuration
@EnableScheduling
public class ExtractieConfig {

  /**
   * Creëert een {@link ThreadPoolTaskExecutor} voor het uitvoeren van extractie-taken.
   *
   * @param maxConcurrent maximum aantal gelijktijdige verwerkingen
   * @return geconfigureerde task executor
   */
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

  /**
   * Creëert een {@link ThreadPoolTaskExecutor} voor het uitvoeren van consolidatie-taken.
   *
   * @param maxConcurrent maximum aantal gelijktijdige verwerkingen
   * @return geconfigureerde task executor
   */
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
}
