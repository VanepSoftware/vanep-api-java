package br.com.vanep.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AuthEndpointsTest {

  private static final String EMAIL = "tester@vanep.com.br";
  private static final String PASSWORD = "password123";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();

    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName("Tester");
    user.setEmail(EMAIL);
    user.setDocument("12345678901");
    user.setPassword(passwordEncoder.encode(PASSWORD));
    user.setVerified(true);
    users.save(user);
  }

  @Test
  void loginPageRendersVanepBrand() throws Exception {
    mockMvc
        .perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Vanep")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"email\"")));
  }

  @Test
  void jwksEndpointIsPublic() throws Exception {
    mockMvc.perform(get("/oauth2/jwks")).andExpect(status().isOk());
  }

  @Test
  void protectedResourceRedirectsUnauthenticatedToLogin() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void profileRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/user/profile")).andExpect(status().isUnauthorized());
  }

  @Test
  void profileReturnsUserForValidJwt() throws Exception {
    mockMvc
        .perform(get("/api/user/profile").with(jwt().jwt(token -> token.subject(EMAIL))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(EMAIL))
        .andExpect(jsonPath("$.name").value("Tester"))
        .andExpect(jsonPath("$.type").value("CLIENT"))
        .andExpect(jsonPath("$.token").isNotEmpty());
  }

  @Test
  void profileReturns404ForUnknownSubject() throws Exception {
    mockMvc
        .perform(
            get("/api/user/profile").with(jwt().jwt(token -> token.subject("ghost@vanep.com"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void formLoginSucceedsWithValidCredentials() throws Exception {
    mockMvc
        .perform(formLogin("/login").user("email", EMAIL).password(PASSWORD))
        .andExpect(status().is3xxRedirection())
        .andExpect(authenticated().withUsername(EMAIL));
  }

  @Test
  void formLoginFailsWithWrongPassword() throws Exception {
    mockMvc
        .perform(formLogin("/login").user("email", EMAIL).password("wrong"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?error"))
        .andExpect(unauthenticated());
  }
}
