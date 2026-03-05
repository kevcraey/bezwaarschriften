package be.vlaanderen.omgeving.bezwaarschriften.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activeert scheduling voor periodieke polling van wachtende taken.
 *
 * <p>Kan uitgeschakeld worden met {@code bezwaarschriften.scheduling.enabled=false},
 * bijvoorbeeld in tests om race conditions met Testcontainers te voorkomen.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "bezwaarschriften.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
