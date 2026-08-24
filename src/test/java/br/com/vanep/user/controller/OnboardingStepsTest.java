package br.com.vanep.user.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** D10: o mobile só percorre {@code onboarding.pendingSteps} e mostra a tela correspondente. */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OnboardingStepsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private CountryRepository countries;

  @MockitoBean private PlacesClient places;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() throws IOException {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    CountryModel brasil = new CountryModel();
    brasil.setName("Brasil");
    brasil.setIsoCode("BR");
    brasil.setPhoneCode("+55");
    brasil.setCurrency("BRL");
    countries.save(brasil);

    BDDMockito.given(places.findPlaceDetails("taguatinga", null))
        .willReturn(fixture("df-taguatinga-qnl5"));
  }

  private PlaceDetailsResponseDTO fixture(String name) throws IOException {
    String json =
        new ClassPathResource("fixtures/places/" + name + ".json")
            .getContentAsString(StandardCharsets.UTF_8);
    return MAPPER.readValue(json, PlaceDetailsResponseDTO.class);
  }

  private JwtRequestPostProcessor as(String uid) {
    return jwt().jwt(builder -> builder.claim("uid", uid).subject(uid));
  }

  private UserModel saveUser(UserType type, String email, String document) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName("Usuário " + email);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }

  private UserModel saveDriverUser(String email, String document) {
    UserModel user = saveUser(UserType.DRIVER, email, document);
    DriverModel driver = new DriverModel();
    driver.setUser(user);
    driver.setBasePrice(BigDecimal.TEN);
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    drivers.save(driver);
    return user;
  }

  private void setAddress(String uid) throws Exception {
    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(as(uid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"taguatinga\"}"))
        .andExpect(status().isOk());
  }

  private void setServiceArea(String uid) throws Exception {
    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .with(as(uid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areas\":[{\"placeId\":\"taguatinga\"}]}"))
        .andExpect(status().isOk());
  }

  @Test
  void driverWithNothingIsMissingBothSteps() throws Exception {
    String uid = saveDriverUser("vazio@vanep.com", "11111111111").getToken();

    mockMvc
        .perform(get("/api/user/me").with(as(uid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.onboarding.pendingSteps.length()").value(2))
        .andExpect(jsonPath("$.onboarding.pendingSteps[0]").value("PERSONAL_ADDRESS"))
        .andExpect(jsonPath("$.onboarding.pendingSteps[1]").value("SERVICE_AREA"));
  }

  @Test
  void driverMissingOnlyServiceAreas() throws Exception {
    String uid = saveDriverUser("com-endereco@vanep.com", "22222222222").getToken();
    setAddress(uid);

    mockMvc
        .perform(get("/api/user/me").with(as(uid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.onboarding.pendingSteps.length()").value(1))
        .andExpect(jsonPath("$.onboarding.pendingSteps[0]").value("SERVICE_AREA"));
  }

  /** SERVICE_AREA não se aplica a cliente — a pergunta não existe para ele. */
  @Test
  void clientWithoutAddressIsMissingOnlyThePersonalAddress() throws Exception {
    String uid = saveUser(UserType.CLIENT, "cliente@vanep.com", "33333333333").getToken();

    mockMvc
        .perform(get("/api/user/me").with(as(uid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.onboarding.pendingSteps.length()").value(1))
        .andExpect(jsonPath("$.onboarding.pendingSteps[0]").value("PERSONAL_ADDRESS"));
  }

  @Test
  void fullyRegisteredDriverHasNoPendingSteps() throws Exception {
    String uid = saveDriverUser("completo@vanep.com", "44444444444").getToken();
    setAddress(uid);
    setServiceArea(uid);

    mockMvc
        .perform(get("/api/user/me").with(as(uid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.onboarding.pendingSteps.length()").value(0));
  }

  @Test
  void fullyRegisteredClientHasNoPendingSteps() throws Exception {
    String uid = saveUser(UserType.CLIENT, "cliente2@vanep.com", "55555555555").getToken();
    setAddress(uid);

    mockMvc
        .perform(get("/api/user/me").with(as(uid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.onboarding.pendingSteps.length()").value(0));
  }
}
