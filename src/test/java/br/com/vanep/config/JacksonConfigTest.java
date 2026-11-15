package br.com.vanep.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.trip.repository.TripRepository;
import br.com.vanep.user.enums.Gender;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class JacksonConfigTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private TripRepository trips;

  private MockMvc mockMvc;
  private String clientUid;
  private String driverUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    clientUid = createUser(UserType.CLIENT, "Client", "client@strict-json.test", "15350946056");
    driverUid = createUser(UserType.DRIVER, "Driver", "driver@strict-json.test", "52998224725");

    DriverModel driver = new DriverModel();
    driver.setUser(users.findByToken(driverUid).orElseThrow());
    driver.setBasePrice(BigDecimal.TEN);
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    drivers.save(driver);
  }

  @Test
  void rejectsNumericEnumAndDoesNotCreateTrip() throws Exception {
    mockMvc
        .perform(
            post("/api/drivers/me/trips/start")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shift\":1}"))
        .andExpect(status().isBadRequest());

    assertThat(trips.findAll()).isEmpty();
  }

  @Test
  void rejectsEnumWithSurroundingWhitespaceAndDoesNotCreateTrip() throws Exception {
    mockMvc
        .perform(
            post("/api/drivers/me/trips/start")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shift\":\"MORNING \"}"))
        .andExpect(status().isBadRequest());

    assertThat(trips.findAll()).isEmpty();
  }

  @Test
  void rejectsNumericEnumInsideJsonNullableAndDoesNotUpdateUser() throws Exception {
    UserModel client = users.findByToken(clientUid).orElseThrow();
    client.setGender(Gender.MALE);
    users.saveAndFlush(client);

    mockMvc
        .perform(
            patch("/api/user/me")
                .with(clientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gender\":1}"))
        .andExpect(status().isBadRequest());

    assertThat(users.findByToken(clientUid).orElseThrow().getGender()).isEqualTo(Gender.MALE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"name\":42}", "{\"name\":42.5}", "{\"name\":true}"})
  void rejectsNonTextScalarForTextFieldAndDoesNotUpdateUser(String body) throws Exception {
    mockMvc
        .perform(
            patch("/api/user/me")
                .with(clientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());

    assertThat(users.findByToken(clientUid).orElseThrow().getName()).isEqualTo("Client");
  }

  @Test
  void keepsAcceptingEnumByExactName() throws Exception {
    mockMvc
        .perform(
            patch("/api/user/me")
                .with(clientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gender\":\"FEMALE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gender").value("FEMALE"));
  }

  private String createUser(UserType type, String name, String email, String document) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName(name);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user).getToken();
  }

  private JwtRequestPostProcessor clientJwt() {
    return jwt().jwt(token -> token.claim("uid", clientUid).subject(clientUid));
  }

  private JwtRequestPostProcessor driverJwt() {
    return jwt()
        .jwt(token -> token.claim("uid", driverUid).subject(driverUid))
        .authorities(new SimpleGrantedAuthority("start_trip"));
  }
}
