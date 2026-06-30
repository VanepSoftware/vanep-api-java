package br.com.vanep.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.client.ClientRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RegistrationControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private PasswordEncoder passwordEncoder;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  @Test
  void signupPagesArePublic() throws Exception {
    mockMvc.perform(get("/signup")).andExpect(status().isOk());
    mockMvc.perform(get("/signup/client")).andExpect(status().isOk());
    mockMvc
        .perform(get("/signup/driver"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Cadastro de motorista")));
  }

  @Test
  void registersClientAndRedirects() throws Exception {
    mockMvc
        .perform(
            post("/signup/client")
                .with(csrf())
                .param("name", "Ana")
                .param("email", "ana@vanep.com")
                .param("password", "secret1")
                .param("document", "11111111111")
                .param("acceptTerms", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?registered"));

    User user = users.findByEmail("ana@vanep.com").orElseThrow();
    assertThat(user.getType()).isEqualTo(UserType.CLIENT);
    assertThat(clients.count()).isEqualTo(1);
  }

  @Test
  void registersDriverAndRedirects() throws Exception {
    mockMvc
        .perform(
            post("/signup/driver")
                .with(csrf())
                .param("name", "Bruno")
                .param("email", "bruno@vanep.com")
                .param("password", "secret1")
                .param("document", "22222222222")
                .param("city", "Taguatinga")
                .param("basePrice", "120.00")
                .param("acceptTerms", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?registered"));

    User user = users.findByEmail("bruno@vanep.com").orElseThrow();
    assertThat(user.getType()).isEqualTo(UserType.DRIVER);
    assertThat(drivers.count()).isEqualTo(1);
  }

  @Test
  void rejectsDuplicateEmail() throws Exception {
    User existing = new User();
    existing.setType(UserType.CLIENT);
    existing.setName("Existing");
    existing.setEmail("dup@vanep.com");
    existing.setDocument("33333333333");
    existing.setPassword(passwordEncoder.encode("secret1"));
    users.save(existing);

    mockMvc
        .perform(
            post("/signup/client")
                .with(csrf())
                .param("name", "Other")
                .param("email", "dup@vanep.com")
                .param("password", "secret1")
                .param("document", "44444444444")
                .param("acceptTerms", "true"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("este e-mail")));

    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void rejectsInvalidForm() throws Exception {
    mockMvc
        .perform(post("/signup/client").with(csrf()).param("email", "not-an-email"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Cadastro de cliente")));

    assertThat(users.count()).isZero();
  }
}
