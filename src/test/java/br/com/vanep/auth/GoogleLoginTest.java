package br.com.vanep.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.role.RoleName;
import br.com.vanep.role.model.RoleModel;
import br.com.vanep.role.repository.RoleRepository;
import br.com.vanep.user.AuthProvider;
import br.com.vanep.user.OAuthAccountRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class GoogleLoginTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private OAuthAccountRepository oauthAccounts;
  @Autowired private AssistantRepository assistants;
  @Autowired private RoleRepository roles;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  @Test
  void loginPageShowsGoogleButtonWhenConfigured() throws Exception {
    mockMvc
        .perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Entrar com Google")))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("/oauth2/authorization/google")));
  }

  @Test
  void signupCompleteRedirectsToLoginWhenAnonymous() throws Exception {
    mockMvc.perform(get("/signup/complete")).andExpect(status().is3xxRedirection());
  }

  @Test
  void signupCompleteShowsFormForOidcUser() throws Exception {
    mockMvc
        .perform(
            get("/signup/complete")
                .with(oidcLogin().idToken(t -> t.subject("g-1").claim("email", "new@gmail.com"))))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Complete seu cadastro")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("new@gmail.com")));
  }

  @Test
  void signupCompleteCreatesAccountAndLinksProvider() throws Exception {
    mockMvc
        .perform(
            post("/signup/complete")
                .with(
                    oidcLogin()
                        .idToken(
                            t ->
                                t.subject("g-2")
                                    .claim("email", "buyer@gmail.com")
                                    .claim("name", "Buyer")))
                .with(csrf())
                .param("type", "CLIENT")
                .param("document", "12345678901")
                .param("phone", "11999990000")
                .param("acceptTerms", "true"))
        .andExpect(status().is3xxRedirection());

    var created = users.findByEmail("buyer@gmail.com");
    assertThat(created).isPresent();
    assertThat(created.get().getDocument()).isEqualTo("12345678901");
    assertThat(oauthAccounts.findByProviderAndProviderUid(AuthProvider.GOOGLE, "g-2")).isPresent();
  }

  @Test
  void signupCompleteRejectsInvalidForm() throws Exception {
    mockMvc
        .perform(
            post("/signup/complete")
                .with(oidcLogin().idToken(t -> t.subject("g-3").claim("email", "x@gmail.com")))
                .with(csrf())
                .param("document", "")
                .param("acceptTerms", "false"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Complete seu cadastro")));

    assertThat(users.findByEmail("x@gmail.com")).isEmpty();
  }

  @Test
  void signupCompleteCreatesUnlinkedAssistantWithoutConsumingLinkCode() throws Exception {
    RoleModel assistantRole = new RoleModel();
    assistantRole.setName("assistant");
    assistantRole.setDescription("Assistant access");
    assistantRole.setRoleName(RoleName.ASSISTANT);
    assistantRole = roles.save(assistantRole);

    mockMvc
        .perform(
            post("/signup/complete")
                .with(
                    oidcLogin()
                        .idToken(
                            t ->
                                t.subject("g-asst")
                                    .claim("email", "asst@gmail.com")
                                    .claim("name", "Assistant")))
                .with(csrf())
                .param("type", "ASSISTANT")
                .param("document", "98765432100")
                .param("acceptTerms", "true"))
        .andExpect(status().is3xxRedirection());

    UserModel created = users.findByEmail("asst@gmail.com").orElseThrow();
    assertThat(created.getType()).isEqualTo(UserType.ASSISTANT);
    assertThat(created.getRoleId()).isEqualTo(assistantRole.getId());

    var assistant = assistants.findByUserId(created.getId()).orElseThrow();
    assertThat(assistant.getStatus()).isEqualTo(AssistantStatus.UNLINKED);
    assertThat(assistant.getDriver()).isNull();
    assertThat(oauthAccounts.findByProviderAndProviderUid(AuthProvider.GOOGLE, "g-asst"))
        .isPresent();
  }
}
