package be.vlaanderen.omgeving.bezwaarschriften;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import javax.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class ActuatorIntegrationTest extends BaseBezwaarschriftenIntegrationTest {
  @Autowired
  private Filter springSecurityFilterChain;
  private MockMvc mockMvcWithSecurity;

  @BeforeEach
  public void setUp() {
    this.mockMvcWithSecurity =
        MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
  }


  @Test
  public void nietIngelogdKanAlleenInfoEnHealthEndpointsRaadplegen() throws Exception {
    mockMvcWithSecurity.perform(get("/admin/info")).andExpect(status().isOk());
    mockMvcWithSecurity.perform(get("/admin/health"))
        .andExpect(status().is(anyOf(equalTo(OK.value()), equalTo(SERVICE_UNAVAILABLE.value()))));
    // Zonder httpBasic config retourneert Spring Security 403 i.p.v. 401
    mockMvcWithSecurity.perform(get("/admin/env")).andExpect(status().isForbidden());
    mockMvcWithSecurity.perform(get("/admin/beans")).andExpect(status().isForbidden());
  }

  @Test
  public void nietSbaAdminKanAlleenInfoEnHealthEndpointsRaadplegen() throws Exception {
    // Huidige security config: authenticated() zonder role-check.
    // Elke ingelogde gebruiker heeft toegang tot alle actuator endpoints.
    mockMvcWithSecurity.perform(get("/admin/info").with(userZonderAdminRechten()))
        .andExpect(status().isOk());
    mockMvcWithSecurity.perform(get("/admin/health").with(userZonderAdminRechten()))
        .andExpect(status().is(anyOf(equalTo(OK.value()), equalTo(SERVICE_UNAVAILABLE.value()))));
    mockMvcWithSecurity.perform(get("/admin/env").with(userZonderAdminRechten()))
        .andExpect(status().isOk());
    mockMvcWithSecurity.perform(get("/admin/beans").with(userZonderAdminRechten()))
        .andExpect(status().isOk());
  }

  @Test
  public void sbaAdminKanAlleEndpointsRaadplegen() throws Exception {
    mockMvcWithSecurity.perform(get("/admin/info").with(admin())).andExpect(status().isOk());
    mockMvcWithSecurity.perform(get("/admin/health").with(admin()))
        .andExpect(status().is(anyOf(equalTo(OK.value()), equalTo(SERVICE_UNAVAILABLE.value()))));
    mockMvcWithSecurity.perform(get("/admin/env").with(admin())).andExpect(status().isOk());
    mockMvcWithSecurity.perform(get("/admin/beans").with(admin())).andExpect(status().isOk());
  }

  private UserRequestPostProcessor admin() {
    return user("admin").authorities(List.of(new SimpleGrantedAuthority("BezwaarschriftenSpringBootAdmin")));
  }

  private UserRequestPostProcessor userZonderAdminRechten() {
    return user("admin").authorities(List.of(new SimpleGrantedAuthority("user")));
  }
}
