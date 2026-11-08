package br.com.vanep.auth.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class ActuatorMetricsSecurityTest {
  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void redirectsToLoginWhenNotAuthenticated() throws Exception {
    mockMvc.perform(get("/actuator/metrics")).andExpect(status().is3xxRedirection());
  }

  @Test
  void rejectsAuthenticatedNonAdmin() throws Exception {
    mockMvc
        .perform(get("/actuator/metrics").with(user("driver@vanep.com").roles("DRIVER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void allowsAdmin() throws Exception {
    mockMvc
        .perform(get("/actuator/metrics").with(user("admin@vanep.com").roles("ADMIN")))
        .andExpect(status().isOk());
  }
}
