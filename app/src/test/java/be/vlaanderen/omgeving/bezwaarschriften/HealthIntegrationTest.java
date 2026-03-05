package be.vlaanderen.omgeving.bezwaarschriften;


import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.StreamSupport;
import javax.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;


public class HealthIntegrationTest extends BaseBezwaarschriftenIntegrationTest {

  private MockMvc mockMvc;

  @Autowired
  private Filter springSecurityFilterChain;

  @BeforeEach
  public void setUp() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
        .addFilters(springSecurityFilterChain).build();
  }

  @Test
  public void kanHealthOpvragenZonderIngelogdTeZijn() throws Exception {
    String healthJson =
        mockMvc.perform(get("/admin/health")).andReturn().getResponse().getContentAsString();
    JsonNode health = objectMapper.readValue(healthJson, JsonNode.class);
    assertThat(health.get("status").asText())
        .as("Health status (response: %s)", healthJson)
        .isEqualTo("UP");
  }

  @Test
  public void kanAlgemeneHealthOpvragen() throws Exception {
    JsonNode health = getHealth();
    Set<String> dependencies = healthOnderdelenEnStatus(health).keySet();
    assertThat(dependencies).contains("diskSpace", "ping");
  }

  @Test
  public void kanGedetailleerdeHealthOpvragen() throws Exception {
    JsonNode health = getHealth();
    assertThat(health.get("status").asText())
        .as("Health status (components: %s)", health.get("components"))
        .isEqualTo("UP");
    assertThat(health.has("components")).isTrue();
  }

  private JsonNode getHealth() throws Exception {
    return getHealth("");
  }

  private JsonNode getHealth(String group) throws Exception {
    String healthJson = mockMvc.perform(get("/admin/health/{group}", group).with(admin()))
        .andReturn().getResponse().getContentAsString();
    return readJson(healthJson, JsonNode.class);
  }

  protected SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
    return user("admin").authorities(List.of(new SimpleGrantedAuthority("sba-admin")));
  }

  private Map<String, String> healthOnderdelenEnStatus(JsonNode health) {
    Iterator<Map.Entry<String, JsonNode>> fields = health.get("components").fields();
    return StreamSupport.stream(spliteratorUnknownSize(fields, Spliterator.NONNULL), false)
        .collect(toMap(Map.Entry::getKey, field -> field.getValue().get("status").asText()));
  }
}
