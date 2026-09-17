package br.com.vanep.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.auth.signup.SignupTicketService;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.user.enums.AuthProvider;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.OAuthAccountRepository;
import br.com.vanep.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class GoogleSignupCompleteApiTest {

  private static final String SUBJECT = "google-subject-1";
  private static final String EMAIL = "person@gmail.com";
  private static final String VALID_CPF = "39053344705";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private OAuthAccountRepository oauthAccounts;
  @Autowired private SignupTicketService signupTickets;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  private String issuedTicket() {
    return signupTickets.issue(AuthProvider.GOOGLE, SUBJECT, EMAIL, "Person Example");
  }

  @Test
  void aClientIsCreatedVerifiedAndLinkedToGoogle() throws Exception {
    mockMvc
        .perform(complete(issuedTicket(), "CLIENT", VALID_CPF, null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(EMAIL))
        .andExpect(jsonPath("$.emailVerified").value(true));

    UserModel created = users.findByEmail(EMAIL).orElseThrow();
    assertThat(created.getType()).isEqualTo(UserType.CLIENT);
    assertThat(created.getName()).isEqualTo("Person Example");
    assertThat(created.getPassword()).isNull();
    assertThat(clients.findByUserId(created.getId())).isPresent();
    assertThat(oauthAccounts.findByProviderAndProviderUid(AuthProvider.GOOGLE, SUBJECT))
        .isPresent();
  }

  @Test
  void aDriverIsCreatedPending() throws Exception {
    mockMvc
        .perform(complete(issuedTicket(), "DRIVER", VALID_CPF, "150.00"))
        .andExpect(status().isCreated());

    UserModel created = users.findByEmail(EMAIL).orElseThrow();
    assertThat(drivers.findByUserId(created.getId()).orElseThrow().getApprovalStatus())
        .isEqualTo(DriverApprovalStatus.PENDING);
  }

  @Test
  void aDriverWithoutBasePriceIsRejected() throws Exception {
    mockMvc
        .perform(complete(issuedTicket(), "DRIVER", VALID_CPF, null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_error"));

    assertThat(users.count()).isZero();
  }

  @Test
  void anUnknownExpiredOrUsedTicketCreatesNothing() throws Exception {
    mockMvc
        .perform(complete("never-issued", "CLIENT", VALID_CPF, null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid_signup_ticket"));

    String ticket = issuedTicket();
    mockMvc.perform(complete(ticket, "CLIENT", VALID_CPF, null)).andExpect(status().isCreated());
    mockMvc
        .perform(complete(ticket, "CLIENT", "52998224725", null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid_signup_ticket"));

    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void anEmailRegisteredWhileTheTicketWasOpenAnswersWithConflict() throws Exception {
    String ticket = issuedTicket();
    persistUserWith(EMAIL, "52998224725");

    mockMvc
        .perform(complete(ticket, "CLIENT", VALID_CPF, null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("email_duplicate"));

    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void aDuplicateDocumentAnswersWithConflict() throws Exception {
    String ticket = issuedTicket();
    persistUserWith("someone.else@vanep.com", VALID_CPF);

    mockMvc
        .perform(complete(ticket, "CLIENT", "390.533.447-05", null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("document_duplicate"));

    assertThat(users.findByEmail(EMAIL)).isEmpty();
  }

  private RequestBuilder complete(String ticket, String type, String document, String basePrice) {
    String basePriceField = basePrice == null ? "" : ",\"basePrice\":" + basePrice;
    return post("/api/auth/signup/complete")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"signupTicket\":\""
                + ticket
                + "\",\"type\":\""
                + type
                + "\",\"document\":\""
                + document
                + "\",\"acceptTerms\":true"
                + basePriceField
                + "}");
  }

  private void persistUserWith(String email, String document) {
    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName("Existing");
    user.setEmail(email);
    user.setDocument(document);
    user.setPassword("hashed");
    users.save(user);
  }
}
