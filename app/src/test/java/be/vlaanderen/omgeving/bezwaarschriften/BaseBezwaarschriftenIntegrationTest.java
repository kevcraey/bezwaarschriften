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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles(Constants.SPRING_PROFILE_DEVELOPMENT)
@Testcontainers
public abstract class BaseBezwaarschriftenIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
      .withDatabaseName("bezwaarschriften")
      .withUsername("test")
      .withPassword("test");

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
