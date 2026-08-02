package br.com.vanep.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.util.Locale;
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

  private static final String VALID_CPF_ANA = "39053344705";
  private static final String VALID_CPF_BRUNO = "52998224725";
  private static final String VALID_CPF_CARLA = "11144477735";
  private static final String VALID_CPF_OTHER = "12345678909";
  private static final String VALID_CPF_EXISTING = "86288366757";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private AssistantRepository assistants;
  @Autowired private PasswordEncoder passwordEncoder;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .defaultRequest(get("/").locale(Locale.forLanguageTag("pt-BR")))
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
    mockMvc
        .perform(get("/signup/assistant"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Cadastro de assistente")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("linkCode"))))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("invite"))));
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
                .param("document", "390.533.447-05")
                .param("acceptTerms", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?registered"));

    UserModel user = users.findByEmail("ana@vanep.com").orElseThrow();
    assertThat(user.getType()).isEqualTo(UserType.CLIENT);
    assertThat(user.getDocument()).isEqualTo(VALID_CPF_ANA);
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
                .param("document", VALID_CPF_BRUNO)
                .param("city", "Taguatinga")
                .param("basePrice", "120.00")
                .param("acceptTerms", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?registered"));

    UserModel user = users.findByEmail("bruno@vanep.com").orElseThrow();
    assertThat(user.getType()).isEqualTo(UserType.DRIVER);
    assertThat(drivers.count()).isEqualTo(1);
  }

  @Test
  void registersAssistantAsUnlinkedAndRedirects() throws Exception {
    mockMvc
        .perform(
            post("/signup/assistant")
                .with(csrf())
                .param("name", "Carla")
                .param("email", "carla@vanep.com")
                .param("password", "secret1")
                .param("document", VALID_CPF_CARLA)
                .param("acceptTerms", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?registered"));

    UserModel user = users.findByEmail("carla@vanep.com").orElseThrow();
    assertThat(user.getType()).isEqualTo(UserType.ASSISTANT);
    assertThat(assistants.count()).isEqualTo(1);
    assertThat(assistants.findByUserId(user.getId()).orElseThrow().getStatus())
        .isEqualTo(AssistantStatus.UNLINKED);
    assertThat(assistants.findByUserId(user.getId()).orElseThrow().getDriver()).isNull();
  }

  @Test
  void rejectsInvalidCpfWithClearMessage() throws Exception {
    mockMvc
        .perform(
            post("/signup/client")
                .with(csrf())
                .param("name", "Ana")
                .param("email", "ana@vanep.com")
                .param("password", "secret1")
                .param("document", "11111111111")
                .param("acceptTerms", "true"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("CPF inválido")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(
                            "Já existe uma conta com este documento"))));

    assertThat(users.count()).isZero();
  }

  @Test
  void rejectsDuplicateValidCpf() throws Exception {
    UserModel existing = new UserModel();
    existing.setType(UserType.CLIENT);
    existing.setName("Existing");
    existing.setEmail("existing@vanep.com");
    existing.setDocument(VALID_CPF_EXISTING);
    existing.setPassword(passwordEncoder.encode("secret1"));
    users.save(existing);

    mockMvc
        .perform(
            post("/signup/client")
                .with(csrf())
                .param("name", "Other")
                .param("email", "other@vanep.com")
                .param("password", "secret1")
                .param("document", VALID_CPF_EXISTING)
                .param("acceptTerms", "true"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString("Já existe uma conta com este documento")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("CPF inválido"))));

    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void rejectsDuplicateEmail() throws Exception {
    UserModel existing = new UserModel();
    existing.setType(UserType.CLIENT);
    existing.setName("Existing");
    existing.setEmail("dup@vanep.com");
    existing.setDocument(VALID_CPF_EXISTING);
    existing.setPassword(passwordEncoder.encode("secret1"));
    users.save(existing);

    mockMvc
        .perform(
            post("/signup/client")
                .with(csrf())
                .param("name", "Other")
                .param("email", "dup@vanep.com")
                .param("password", "secret1")
                .param("document", VALID_CPF_OTHER)
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
