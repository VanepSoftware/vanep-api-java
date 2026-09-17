package br.com.vanep.driverrating.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverrating.model.DriverRatingModel;
import br.com.vanep.driverrating.repository.DriverRatingRepository;
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
class DriverRatingControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private DriverRatingRepository driverRatings;

  private MockMvc mockMvc;

  private String driverToken;
  private String clientUserEmail;
  private String clientUserUid;
  private String ratingToken;

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

    DriverModel driver = new DriverModel();
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

    clientUserEmail = clientUser.getEmail();
    clientUserUid = clientUser.getToken();

    ClientModel client = new ClientModel();
    client.setUser(clientUser);
    client = clients.save(client);

    // Initial Rating
    DriverRatingModel rating = new DriverRatingModel();
    rating.setDriver(driver);
    rating.setClient(client);
    rating.setRating(BigDecimal.valueOf(5.00));
    rating.setComment("Great trip!");
    rating = driverRatings.save(rating);
    ratingToken = rating.getToken();
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(t -> t.claim("uid", "admin-uid").claim("roles", List.of("ROLE_ADMIN")))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("list_driver_ratings"),
            new SimpleGrantedAuthority("show_driver_rating"),
            new SimpleGrantedAuthority("create_driver_rating"),
            new SimpleGrantedAuthority("update_driver_rating"),
            new SimpleGrantedAuthority("delete_driver_rating"),
            new SimpleGrantedAuthority("restore_driver_rating"));
  }

  private JwtRequestPostProcessor clientJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", clientUserUid)
                    .subject(clientUserEmail)
                    .claim("roles", List.of("ROLE_CLIENT")))
        .authorities(
            new SimpleGrantedAuthority("ROLE_CLIENT"),
            new SimpleGrantedAuthority("create_driver_rating"),
            new SimpleGrantedAuthority("list_driver_ratings"),
            new SimpleGrantedAuthority("show_driver_rating"));
  }

  private JwtRequestPostProcessor otherClientJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", "other-client-uid")
                    .subject("other@vanep.com")
                    .claim("roles", List.of("ROLE_CLIENT")))
        .authorities(
            new SimpleGrantedAuthority("ROLE_CLIENT"),
            new SimpleGrantedAuthority("create_driver_rating"),
            new SimpleGrantedAuthority("list_driver_ratings"));
  }

  @Test
  void createRequiresAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/driver-ratings").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createReturns400OnInvalidRatingValue() throws Exception {
    String requestBody =
        """
        {
          "driverToken": "%s",
          "rating": 6.00,
          "comment": "Too high"
        }
        """
            .formatted(driverToken);

    mockMvc
        .perform(
            post("/api/driver-ratings")
                .with(clientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listReturnsPageOfRatings() throws Exception {
    mockMvc
        .perform(get("/api/driver-ratings").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].token").value(ratingToken));
  }

  @Test
  void listFilterByDriverToken() throws Exception {
    mockMvc
        .perform(get("/api/driver-ratings?driverToken=" + driverToken).with(clientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].driverToken").value(driverToken));
  }

  @Test
  void getByTokenReturnsRating() throws Exception {
    mockMvc
        .perform(get("/api/driver-ratings/" + ratingToken).with(clientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(ratingToken))
        .andExpect(jsonPath("$.rating").value(5.0));
  }

  @Test
  void updateReturns200ForOwner() throws Exception {
    String requestBody =
        """
        {
          "rating": 4.00,
          "comment": "Updated feedback"
        }
        """;

    mockMvc
        .perform(
            put("/api/driver-ratings/" + ratingToken)
                .with(clientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rating").value(4.0))
        .andExpect(jsonPath("$.comment").value("Updated feedback"));
  }

  @Test
  void updateForbidsOtherClient() throws Exception {
    String requestBody =
        """
        {
          "rating": 1.00,
          "comment": "Malicious edit"
        }
        """;

    mockMvc
        .perform(
            put("/api/driver-ratings/" + ratingToken)
                .with(otherClientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteReturns204ForOwner() throws Exception {
    mockMvc
        .perform(delete("/api/driver-ratings/" + ratingToken).with(clientJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void restoreReturns200ForAdmin() throws Exception {
    // Delete first
    mockMvc
        .perform(delete("/api/driver-ratings/" + ratingToken).with(clientJwt()))
        .andExpect(status().isNoContent());

    // Restore
    mockMvc
        .perform(post("/api/driver-ratings/" + ratingToken + "/restore").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(ratingToken));
  }

  @Test
  void restoreForbidsNonAdmin() throws Exception {
    mockMvc
        .perform(post("/api/driver-ratings/" + ratingToken + "/restore").with(clientJwt()))
        .andExpect(status().isForbidden());
  }
}
