package br.com.vanep.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.auth.mail.MailService;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SignupApiControllerTest {

  private static final String VALID_CPF = "39053344705";
  private static final String OTHER_CPF = "52998224725";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private PasswordEncoder passwordEncoder;
  @MockitoBean private MailService mail;

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
  void clientSignupCreatesTheAccountUnverifiedAndSendsTheVerificationEmail() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/signup/client")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Ana","email":"ana@vanep.com","password":"secret1",
                     "document":"390.533.447-05","acceptTerms":true}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("ana@vanep.com"))
        .andExpect(jsonPath("$.emailVerified").value(false));

    UserModel created = users.findByEmail("ana@vanep.com").orElseThrow();
    assertThat(created.getType()).isEqualTo(UserType.CLIENT);
    assertThat(created.getDocument()).isEqualTo(VALID_CPF);
    assertThat(clients.findByUserId(created.getId())).isPresent();
    verify(mail).send(eq("ana@vanep.com"), anyString(), eq("email/verification"), anyMap());
  }

  @Test
  void driverSignupCreatesAPendingDriver() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/signup/driver")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Bruno","email":"bruno@vanep.com","password":"secret1",
                     "document":"52998224725","basePrice":120.00,"acceptTerms":true}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.emailVerified").value(false));

    UserModel created = users.findByEmail("bruno@vanep.com").orElseThrow();
    assertThat(drivers.findByUserId(created.getId()).orElseThrow().getApprovalStatus())
        .isEqualTo(DriverApprovalStatus.PENDING);
  }

  @Test
  void assistantSignupIsAccepted() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/signup/assistant")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Carla","email":"carla@vanep.com","password":"secret1",
                     "document":"11144477735","acceptTerms":true}
                    """))
        .andExpect(status().isCreated());

    assertThat(users.findByEmail("carla@vanep.com")).isPresent();
  }

  @Test
  void invalidFieldsAreReportedOneByOne() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/signup/client")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"  ","email":"ana@vanep.com","password":"secret1",
                     "document":"11111111111","acceptTerms":true}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_error"))
        .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
        .andExpect(
            jsonPath("$.errors[?(@.field == 'document')].message")
                .value("CPF inválido. Verifique os números informados."));

    assertThat(users.count()).isZero();
  }

  @Test
  void driverWithoutBasePriceIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/signup/driver")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Bruno","email":"bruno@vanep.com","password":"secret1",
                     "document":"52998224725","acceptTerms":true}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_error"))
        .andExpect(jsonPath("$.errors[?(@.field == 'basePrice')]").exists());

    assertThat(users.count()).isZero();
    verify(mail, never()).send(anyString(), anyString(), anyString(), anyMap());
  }

  @Test
  void termsMustBeAccepted() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/signup/client")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Ana","email":"ana@vanep.com","password":"secret1",
                     "document":"39053344705","acceptTerms":false}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.field == 'acceptTerms')]").exists());
  }

  @Test
  void duplicateEmailAndDocumentAnswerWithConflict() throws Exception {
    persistExistingUser();

    mockMvc
        .perform(
            post("/api/auth/signup/client")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Other","email":"existing@vanep.com","password":"secret1",
                     "document":"11144477735","acceptTerms":true}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("email_duplicate"));

    mockMvc
        .perform(
            post("/api/auth/signup/client")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Other","email":"other@vanep.com","password":"secret1",
                     "document":"529.982.247-25","acceptTerms":true}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("document_duplicate"));

    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void authRoutesAnswerWithoutATokenAndEvenWithAStaleOne() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/signup/client")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer not-a-valid-token")
                .content(
                    """
                    {"name":"Ana","email":"ana@vanep.com","password":"secret1",
                     "document":"39053344705","acceptTerms":true}
                    """))
        .andExpect(status().isCreated());
  }

  @Test
  void otherApiRoutesStayProtected() throws Exception {
    mockMvc.perform(get("/api/user/me")).andExpect(status().isUnauthorized());
  }

  private void persistExistingUser() {
    UserModel existing = new UserModel();
    existing.setType(UserType.CLIENT);
    existing.setName("Existing");
    existing.setEmail("existing@vanep.com");
    existing.setDocument(OTHER_CPF);
    existing.setPassword(passwordEncoder.encode("secret1"));
    users.save(existing);
  }
}
