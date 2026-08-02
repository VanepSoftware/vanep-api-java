package br.com.vanep.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.user.Gender;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProfileControllerTest {

  private static final String EMAIL = "profile@vanep.com";
  private static final String DOCUMENT = "12345678901";
  private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 5, 15);

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;

  private MockMvc mockMvc;
  private String uid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName("Tester");
    user.setEmail(EMAIL);
    user.setDocument(DOCUMENT);
    user.setBirthDate(BIRTH_DATE);
    user.setGender(Gender.FEMALE);
    user.setPhone("11999999999");
    user.setVerified(true);
    user = users.save(user);
    uid = user.getToken();
  }

  @Test
  void patchMeRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            patch("/api/user/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void patchMeHappyPathUpdatesNamePhoneGenderAndLeavesDocumentBirthDate() throws Exception {
    mockMvc
        .perform(
            patch("/api/user/me")
                .with(jwt().jwt(token -> token.claim("uid", uid).subject(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Updated Name",
                      "phone": "11888888888",
                      "gender": "MALE"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Name"))
        .andExpect(jsonPath("$.phone").value("11888888888"))
        .andExpect(jsonPath("$.gender").value("MALE"))
        .andExpect(jsonPath("$.document").value(DOCUMENT))
        .andExpect(jsonPath("$.birthDate").value("1990-05-15"))
        .andExpect(jsonPath("$.email").value(EMAIL))
        .andExpect(jsonPath("$.token").value(uid));

    UserModel reloaded = users.findByToken(uid).orElseThrow();
    assertThat(reloaded.getName()).isEqualTo("Updated Name");
    assertThat(reloaded.getPhone()).isEqualTo("11888888888");
    assertThat(reloaded.getGender()).isEqualTo(Gender.MALE);
    assertThat(reloaded.getDocument()).isEqualTo(DOCUMENT);
    assertThat(reloaded.getBirthDate()).isEqualTo(BIRTH_DATE);
    assertThat(reloaded.getLastNameChangeAt()).isNotNull();
    assertThat(reloaded.getLastPhoneChangeAt()).isNotNull();
  }

  @Test
  void patchMeBlankPhoneReturns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/user/me")
                .with(jwt().jwt(token -> token.claim("uid", uid).subject(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"\"}"))
        .andExpect(status().isBadRequest());

    UserModel reloaded = users.findByToken(uid).orElseThrow();
    assertThat(reloaded.getPhone()).isEqualTo("11999999999");
    assertThat(reloaded.getDocument()).isEqualTo(DOCUMENT);
    assertThat(reloaded.getBirthDate()).isEqualTo(BIRTH_DATE);
  }

  @Test
  void patchMeNameCooldownReturns409WithStructuredBody() throws Exception {
    UserModel user = users.findByToken(uid).orElseThrow();
    Instant lastChange = Instant.now().minus(5, ChronoUnit.DAYS);
    user.setLastNameChangeAt(lastChange);
    users.save(user);

    mockMvc
        .perform(
            patch("/api/user/me")
                .with(jwt().jwt(token -> token.claim("uid", uid).subject(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Blocked Name\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("cooldown"))
        .andExpect(jsonPath("$.field").value("name"))
        .andExpect(jsonPath("$.message").value(notNullValue()))
        .andExpect(jsonPath("$.retryAfter").value(notNullValue()));

    UserModel reloaded = users.findByToken(uid).orElseThrow();
    assertThat(reloaded.getName()).isEqualTo("Tester");
    assertThat(reloaded.getDocument()).isEqualTo(DOCUMENT);
    assertThat(reloaded.getBirthDate()).isEqualTo(BIRTH_DATE);
  }
}
