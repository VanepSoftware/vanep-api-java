package br.com.vanep.clientrating.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.clientrating.model.ClientRatingModel;
import br.com.vanep.clientrating.repository.ClientRatingRepository;
import br.com.vanep.contract.enums.ContractStatus;
import br.com.vanep.contract.model.ContractModel;
import br.com.vanep.contract.repository.ContractRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
class ClientRatingControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private ClientRatingRepository clientRatings;
  @Autowired private ClientDriverRepository links;
  @Autowired private ContractRepository contracts;
  @Autowired private MessageSource messages;

  private MockMvc mockMvc;

  private String clientToken;
  private String driverUserEmail;
  private String driverUserUid;
  private String ratingToken;
  private String driverToken;
  private DriverModel driver;
  private int documentSequence;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    // Driver
    UserModel driverUser = new UserModel();
    driverUser.setType(UserType.DRIVER);
    driverUser.setName("Driver Name");
    driverUser.setEmail("driver@vanep.com");
    driverUser.setDocument("11111111111");
    driverUser.setVerified(true);
    driverUser.setTermsAcceptedAt(Instant.now());
    driverUser = users.save(driverUser);

    driverUserEmail = driverUser.getEmail();
    driverUserUid = driverUser.getToken();

    driver = new DriverModel();
    driver.setUser(driverUser);
    driver.setBasePrice(BigDecimal.valueOf(50));
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    driver = drivers.save(driver);
    driverToken = driver.getToken();

    // Client
    UserModel clientUser = new UserModel();
    clientUser.setType(UserType.CLIENT);
    clientUser.setName("Client Name");
    clientUser.setEmail("client@vanep.com");
    clientUser.setDocument("22222222222");
    clientUser.setVerified(true);
    clientUser.setTermsAcceptedAt(Instant.now());
    clientUser = users.save(clientUser);

    ClientModel client = new ClientModel();
    client.setUser(clientUser);
    client = clients.save(client);
    clientToken = client.getToken();

    ClientDriverModel link = new ClientDriverModel();
    link.setClient(client);
    link.setDriver(driver);
    link.setStatus(RelationshipStatus.ACTIVE);
    link = links.save(link);
    giveTheLinkAContract(link, ContractStatus.ACTIVE);

    // Initial Rating
    ClientRatingModel rating = new ClientRatingModel();
    rating.setLink(link);
    rating.setRating(BigDecimal.valueOf(5.00));
    rating.setComment("Great passenger!");
    rating = clientRatings.save(rating);
    ratingToken = rating.getToken();
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(t -> t.claim("uid", "admin-uid").claim("roles", List.of("ROLE_ADMIN")))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("list_client_ratings"),
            new SimpleGrantedAuthority("show_client_rating"),
            new SimpleGrantedAuthority("create_client_rating"),
            new SimpleGrantedAuthority("delete_client_rating"));
  }

  private JwtRequestPostProcessor driverJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", driverUserUid)
                    .subject(driverUserEmail)
                    .claim("roles", List.of("ROLE_DRIVER")))
        .authorities(
            new SimpleGrantedAuthority("ROLE_DRIVER"),
            new SimpleGrantedAuthority("create_client_rating"),
            new SimpleGrantedAuthority("list_client_ratings"),
            new SimpleGrantedAuthority("show_client_rating"));
  }

  private JwtRequestPostProcessor otherDriverJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", "other-driver-uid")
                    .subject("other@vanep.com")
                    .claim("roles", List.of("ROLE_DRIVER")))
        .authorities(
            new SimpleGrantedAuthority("ROLE_DRIVER"),
            new SimpleGrantedAuthority("create_client_rating"),
            new SimpleGrantedAuthority("list_client_ratings"));
  }

  @Test
  void createRequiresAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/client-ratings").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createReturns400OnInvalidRatingValue() throws Exception {
    String requestBody =
        """
        {
          "clientToken": "%s",
          "rating": 6.00,
          "comment": "Too high"
        }
        """
            .formatted(clientToken);

    mockMvc
        .perform(
            post("/api/client-ratings")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listReturnsPageOfRatings() throws Exception {
    mockMvc
        .perform(get("/api/client-ratings").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].token").value(ratingToken));
  }

  @Test
  void listFilterByClientToken() throws Exception {
    mockMvc
        .perform(get("/api/client-ratings?clientToken=" + clientToken).with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].clientToken").value(clientToken));
  }

  @Test
  void getByTokenReturnsRating() throws Exception {
    mockMvc
        .perform(get("/api/client-ratings/" + ratingToken).with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(ratingToken))
        .andExpect(jsonPath("$.rating").value(5.0));
  }

  @Test
  void deleteReturns204ForOwner() throws Exception {
    mockMvc
        .perform(delete("/api/client-ratings/" + ratingToken).with(driverJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteForbidsOtherDriver() throws Exception {
    mockMvc
        .perform(delete("/api/client-ratings/" + ratingToken).with(otherDriverJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteReturns204ForAdmin() throws Exception {
    mockMvc
        .perform(delete("/api/client-ratings/" + ratingToken).with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void theResponseStillCarriesBothParties() throws Exception {
    mockMvc
        .perform(get("/api/client-ratings/" + ratingToken).with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.driverToken").value(driverToken))
        .andExpect(jsonPath("$.driverName").value("Driver Name"))
        .andExpect(jsonPath("$.clientToken").value(clientToken))
        .andExpect(jsonPath("$.clientName").value("Client Name"))
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.clientDriverId").doesNotExist());
  }

  @Test
  void deletingRemovesTheRowPhysically() throws Exception {
    mockMvc
        .perform(delete("/api/client-ratings/" + ratingToken).with(driverJwt()))
        .andExpect(status().isNoContent());

    assertThat(clientRatings.count()).isZero();
  }

  @Test
  void aPairCanBeRatedAgainAfterItsRatingIsDeleted() throws Exception {
    mockMvc
        .perform(delete("/api/client-ratings/" + ratingToken).with(driverJwt()))
        .andExpect(status().isNoContent());

    String requestBody =
        """
        {
          "clientToken": "%s",
          "rating": 3.00,
          "comment": "Mudei de ideia"
        }
        """
            .formatted(clientToken);

    mockMvc
        .perform(
            post("/api/client-ratings")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rating").value(3.0));

    assertThat(clientRatings.count()).isEqualTo(1);
  }

  @Test
  void aLinkWithoutContractCannotRateTheClient() throws Exception {
    String pendingClientToken = linkTheDriverToANewClient(RelationshipStatus.PENDING);

    mockMvc
        .perform(
            post("/api/client-ratings")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ratingBody(pendingClientToken)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value(message("client_rating.link.never_contracted")));

    assertThat(clientRatings.count()).isEqualTo(1);
  }

  @Test
  void anInactiveLinkWhoseContractEndedCanRateTheClient() throws Exception {
    String formerClientToken =
        linkTheDriverToANewClient(RelationshipStatus.INACTIVE, ContractStatus.ENDED);

    mockMvc
        .perform(
            post("/api/client-ratings")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ratingBody(formerClientToken)))
        .andExpect(status().isCreated());

    assertThat(clientRatings.count()).isEqualTo(2);
  }

  private String linkTheDriverToANewClient(
      RelationshipStatus status, ContractStatus... contractStatuses) {
    UserModel clientUser = new UserModel();
    clientUser.setType(UserType.CLIENT);
    clientUser.setName("Linked Client");
    clientUser.setEmail("linked" + (++documentSequence) + "@vanep.com");
    clientUser.setDocument(String.format("5%010d", documentSequence));
    clientUser.setVerified(true);
    clientUser.setTermsAcceptedAt(Instant.now());
    clientUser = users.save(clientUser);

    ClientModel linkedClient = new ClientModel();
    linkedClient.setUser(clientUser);
    linkedClient = clients.save(linkedClient);

    ClientDriverModel link = new ClientDriverModel();
    link.setClient(linkedClient);
    link.setDriver(driver);
    link.setStatus(status);
    link = links.save(link);
    for (ContractStatus contractStatus : contractStatuses) {
      giveTheLinkAContract(link, contractStatus);
    }
    return linkedClient.getToken();
  }

  private ContractModel giveTheLinkAContract(ClientDriverModel link, ContractStatus status) {
    ContractModel contract = new ContractModel();
    contract.setClientDriver(link);
    contract.setStatus(status);
    contract.setStartsOn(LocalDate.of(2027, 2, 1));
    contract.setEndsOn(LocalDate.of(2027, 12, 1));
    contract.setTotalAmount(new BigDecimal("4200.00"));
    contract.setInstallments(12);
    contract.setDueDay(5);
    return contracts.save(contract);
  }

  private String ratingBody(String clientToken) {
    return """
        {
          "clientToken": "%s",
          "rating": 4.00,
          "comment": "On time"
        }
        """
        .formatted(clientToken);
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
