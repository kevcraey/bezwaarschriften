package be.vlaanderen.omgeving.bezwaarschriften.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class IndividueelBezwaarIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
      DockerImageName.parse("pgvector/pgvector:pg17")
          .asCompatibleSubstituteFor("postgres"))
      .withDatabaseName("bezwaarschriften")
      .withUsername("test")
      .withPassword("test")
      .withInitScript("init-pgvector.sql");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
  }

  @Autowired
  private IndividueelBezwaarRepository repository;

  @Test
  void testSaveAndRetrieveIndividueelBezwaar() {
    IndividueelBezwaar bezwaar = new IndividueelBezwaar();
    bezwaar.setTekst("Dit is een test bezwaarschrift");

    repository.save(bezwaar);

    List<IndividueelBezwaar> all = repository.findAll();
    assertThat(all).hasSize(1);

    IndividueelBezwaar saved = all.get(0);
    assertThat(saved.getTekst()).isEqualTo("Dit is een test bezwaarschrift");
    assertThat(saved.getId()).isNotNull();
  }
}
