package be.vlaanderen.omgeving.juris;

import be.vlaanderen.omgeving.juris.config.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles(Constants.SPRING_PROFILE_DEVELOPMENT)
public abstract class BaseJurisIntegrationTest {
  @Autowired
  protected ObjectMapper objectMapper;

  @Autowired
  protected WebApplicationContext context;

  protected <T extends Object> T readJson(String json, Class<T> expectedClass) throws IOException {
    return objectMapper.readValue(json, expectedClass);
  }
}
