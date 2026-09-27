package br.com.vanep.clientdriver.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class ClientDriverControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private ClientDriverRepository links;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;

  private MockMvc mockMvc;
  private ClientModel maria;
  private DriverModel carlos;
  private String mariaUid;
  private String carlosUid;
  private String strangerUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    maria = createClient("maria@vanep.com", "11144477735");
    mariaUid = maria.getUser().getToken();

    carlos = createDriver("carlos@vanep.com", "52998224725");
    carlosUid = carlos.getUser().getToken();

    strangerUid = createClient("bruno@vanep.com", "12345678909").getUser().getToken();
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/client-drivers")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/client-drivers/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void adminCreatesTheLinkAsPending() throws Exception {
    mockMvc
        .perform(
            post("/api/client-drivers")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(maria.getToken(), carlos.getToken())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.token").isNotEmpty());
  }

  @Test
  void rejectsADuplicateActivePair() throws Exception {
    persistLink(RelationshipStatus.ACTIVE);

    mockMvc
        .perform(
            post("/api/client-drivers")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(maria.getToken(), carlos.getToken())))
        .andExpect(status().isConflict());
  }

  @Test
  void rejectsAnUnknownClient() throws Exception {
    mockMvc
        .perform(
            post("/api/client-drivers")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("naoexiste", carlos.getToken())))
        .andExpect(status().isNotFound());
  }

  @Test
  void listingExcludesRemovedLinks() throws Exception {
    ClientDriverModel removed = persistLink(RelationshipStatus.ACTIVE);
    links.delete(removed);

    mockMvc
        .perform(get("/api/client-drivers").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  void theClientSideReadsItsOwnLink() throws Exception {
    ClientDriverModel link = persistLink(RelationshipStatus.ACTIVE);

    mockMvc
        .perform(get("/api/client-drivers/" + link.getToken()).with(partyJwt(mariaUid, "CLIENT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(link.getToken()));
  }

  @Test
  void theDriverSideReadsTheSameLink() throws Exception {
    ClientDriverModel link = persistLink(RelationshipStatus.ACTIVE);

    mockMvc
        .perform(get("/api/client-drivers/" + link.getToken()).with(partyJwt(carlosUid, "DRIVER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(link.getToken()));
  }

  @Test
  void anUnrelatedUserIsRefused() throws Exception {
    ClientDriverModel link = persistLink(RelationshipStatus.ACTIVE);

    mockMvc
        .perform(
            get("/api/client-drivers/" + link.getToken()).with(partyJwt(strangerUid, "CLIENT")))
        .andExpect(status().isForbidden());
  }

  @Test
  void neitherPartyCanDeleteTheSharedLink() throws Exception {
    ClientDriverModel link = persistLink(RelationshipStatus.ACTIVE);

    mockMvc
        .perform(
            delete("/api/client-drivers/" + link.getToken()).with(partyJwt(mariaUid, "CLIENT")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            delete("/api/client-drivers/" + link.getToken()).with(partyJwt(carlosUid, "DRIVER")))
        .andExpect(status().isForbidden());

    assertThat(links.findByToken(link.getToken())).isPresent();
  }

  @Test
  void adminDeletesAndRestoresTheLink() throws Exception {
    ClientDriverModel link = persistLink(RelationshipStatus.ACTIVE);

    mockMvc
        .perform(delete("/api/client-drivers/" + link.getToken()).with(adminJwt()))
        .andExpect(status().isNoContent());
    assertThat(links.findByToken(link.getToken())).isEmpty();

    mockMvc
        .perform(post("/api/client-drivers/" + link.getToken() + "/restore").with(adminJwt()))
        .andExpect(status().isOk());
    assertThat(links.findByToken(link.getToken())).isPresent();
  }

  @Test
  void patchingTheStatusIsPersisted() throws Exception {
    ClientDriverModel link = persistLink(RelationshipStatus.PENDING);

    mockMvc
        .perform(
            patch("/api/client-drivers/" + link.getToken())
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void anEmptyPatchLeavesEveryStoredFieldUnchanged() throws Exception {
    ClientDriverModel link = persistLink(RelationshipStatus.ACTIVE);

    mockMvc
        .perform(
            patch("/api/client-drivers/" + link.getToken())
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());

    ClientDriverModel stored = links.findByToken(link.getToken()).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(RelationshipStatus.ACTIVE);
    assertThat(stored.getClient().getToken()).isEqualTo(maria.getToken());
    assertThat(stored.getDriver().getToken()).isEqualTo(carlos.getToken());
  }

  @Test
  void clearingTheStatusIsRefused() throws Exception {
    ClientDriverModel link = persistLink(RelationshipStatus.ACTIVE);

    mockMvc
        .perform(
            patch("/api/client-drivers/" + link.getToken())
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":null}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void eachPartyListsItsOwnLinks() throws Exception {
    persistLink(RelationshipStatus.ACTIVE);

    mockMvc
        .perform(get("/api/client-drivers/me").with(partyJwt(mariaUid, "CLIENT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mockMvc
        .perform(get("/api/client-drivers/me").with(partyJwt(carlosUid, "DRIVER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void aCallerWithNoLinksGetsAnEmptyList() throws Exception {
    mockMvc
        .perform(get("/api/client-drivers/me").with(partyJwt(strangerUid, "CLIENT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void responseExposesNoNumericIdentifier() throws Exception {
    ClientDriverModel link = persistLink(RelationshipStatus.ACTIVE);

    mockMvc
        .perform(get("/api/client-drivers/" + link.getToken()).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.clientId").doesNotExist())
        .andExpect(jsonPath("$.driverId").doesNotExist())
        .andExpect(jsonPath("$.clientToken").value(maria.getToken()))
        .andExpect(jsonPath("$.driverToken").value(carlos.getToken()));
  }

  private String createBody(String clientToken, String driverToken) {
    return "{\"clientToken\":\"" + clientToken + "\",\"driverToken\":\"" + driverToken + "\"}";
  }

  private ClientDriverModel persistLink(RelationshipStatus status) {
    ClientDriverModel link = new ClientDriverModel();
    link.setClient(maria);
    link.setDriver(carlos);
    link.setStatus(status);
    return links.save(link);
  }

  private UserModel createUser(UserType type, String name, String email, String document) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName(name);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }

  private ClientModel createClient(String email, String document) {
    ClientModel client = new ClientModel();
    client.setUser(createUser(UserType.CLIENT, "Cliente", email, document));
    return clients.save(client);
  }

  private DriverModel createDriver(String email, String document) {
    DriverModel driver = new DriverModel();
    driver.setUser(createUser(UserType.DRIVER, "Motorista", email, document));
    driver.setBasePrice(new BigDecimal("100.00"));
    return drivers.save(driver);
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(token -> token.claim("roles", List.of("ROLE_ADMIN")).subject("admin@vanep.com"))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("list_client_drivers"),
            new SimpleGrantedAuthority("show_client_driver"),
            new SimpleGrantedAuthority("create_client_driver"),
            new SimpleGrantedAuthority("update_client_driver"),
            new SimpleGrantedAuthority("delete_client_driver"),
            new SimpleGrantedAuthority("restore_client_driver"));
  }

  private JwtRequestPostProcessor partyJwt(String uid, String role) {
    return jwt()
        .jwt(token -> token.claim("uid", uid).claim("roles", List.of("ROLE_" + role)).subject(uid))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}
