package be.vlaanderen.omgeving.bezwaarschriften;

import be.vlaanderen.omgeving.bezwaarschriften.config.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles(Constants.SPRING_PROFILE_DEVELOPMENT)
public abstract class BaseBezwaarschriftenIntegrationTest {

  static final PostgreSQLContainer<?> postgres;

  static {
    postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
        .withDatabaseName("bezwaarschriften")
        .withUsername("test")
        .withPassword("test");
    postgres.start();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
  }

  @Autowired
  protected ObjectMapper objectMapper;

  @Autowired
  protected WebApplicationContext context;

  protected <T extends Object> T readJson(String json, Class<T> expectedClass) throws IOException {
    return objectMapper.readValue(json, expectedClass);
  }
}
