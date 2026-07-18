package br.com.vanep.driver.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
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
class DriverControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;

  private MockMvc mockMvc;

  private String driverToken;
  private String ownerUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    UserModel user = new UserModel();
    user.setType(UserType.DRIVER);
    user.setName("Test Driver");
    user.setEmail("driver@vanep.com");
    user.setDocument("66666666666");
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    user = users.save(user);

    ownerUid = user.getToken();

    DriverModel driver = new DriverModel();
    driver.setUser(user);
    driver.setCnpj("11222333000181");
    driver.setBasePrice(BigDecimal.valueOf(50));
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    driver = drivers.save(driver);

    driverToken = driver.getToken();
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(t -> t.claim("uid", "admin-uid").claim("roles", List.of("ROLE_ADMIN")))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("list_drivers"),
            new SimpleGrantedAuthority("show_driver"),
            new SimpleGrantedAuthority("update_driver"),
            new SimpleGrantedAuthority("delete_driver"),
            new SimpleGrantedAuthority("restore_driver"));
  }

  private JwtRequestPostProcessor clientJwt() {
    return jwt()
        .jwt(t -> t.claim("uid", "client-uid").claim("roles", List.of("ROLE_CLIENT")))
        .authorities(
            new SimpleGrantedAuthority("ROLE_CLIENT"),
            new SimpleGrantedAuthority("list_drivers"),
            new SimpleGrantedAuthority("show_driver"));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(t -> t.claim("uid", ownerUid).claim("roles", List.of("ROLE_DRIVER")))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }

  private JwtRequestPostProcessor otherDriverJwt() {
    return jwt()
        .jwt(t -> t.claim("uid", "other-uid").claim("roles", List.of("ROLE_DRIVER")))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }

  @Test
  void listRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/drivers")).andExpect(status().isUnauthorized());
  }

  @Test
  void listForbidsNonAuthorizedDriver() throws Exception {
    mockMvc.perform(get("/api/drivers").with(ownerJwt())).andExpect(status().isForbidden());
  }

  @Test
  void listReturnsPageForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/drivers").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].token").value(driverToken));
  }

  @Test
  void listReturnsPageForClient() throws Exception {
    mockMvc
        .perform(get("/api/drivers").with(clientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].token").value(driverToken));
  }

  @Test
  void getByTokenRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/drivers/" + driverToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void getByTokenReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/drivers/" + driverToken).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(driverToken))
        .andExpect(jsonPath("$.email").value("driver@vanep.com"));
  }

  @Test
  void getByTokenReturns200ForClient() throws Exception {
    mockMvc
        .perform(get("/api/drivers/" + driverToken).with(clientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(driverToken));
  }

  @Test
  void getByTokenReturns200ForOwner() throws Exception {
    mockMvc
        .perform(get("/api/drivers/" + driverToken).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(driverToken));
  }

  @Test
  void getByTokenForbidsOtherDriver() throws Exception {
    mockMvc
        .perform(get("/api/drivers/" + driverToken).with(otherDriverJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            put("/api/drivers/" + driverToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void updateReturns200ForOwner() throws Exception {
    String requestBody =
        """
        {
          "photo": "http://photo.url",
          "bio": "Expert driver",
          "cnpj": "11222333000181",
          "experienceYears": 10,
          "city": "São Paulo",
          "basePrice": 120.50,
          "workStartTime": "08:00:00",
          "workEndTime": "18:00:00",
          "workDays": ["Monday", "Wednesday", "Friday"],
          "waitToleranceMinutes": 15,
          "serviceAreas": ["Centro", "Zona Sul"],
          "available": true
        }
        """;

    mockMvc
        .perform(
            put("/api/drivers/" + driverToken)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.photo").value("http://photo.url"))
        .andExpect(jsonPath("$.bio").value("Expert driver"))
        .andExpect(jsonPath("$.city").value("São Paulo"))
        .andExpect(jsonPath("$.basePrice").value(120.50))
        .andExpect(jsonPath("$.workDays[0]").value("Monday"))
        .andExpect(jsonPath("$.available").value(true));
  }

  @Test
  void updateReturns200ForAdmin() throws Exception {
    String requestBody =
        """
        {
          "city": "Rio de Janeiro",
          "basePrice": 150.00,
          "available": false
        }
        """;

    mockMvc
        .perform(
            put("/api/drivers/" + driverToken)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.city").value("Rio de Janeiro"))
        .andExpect(jsonPath("$.basePrice").value(150.00))
        .andExpect(jsonPath("$.available").value(false));
  }

  @Test
  void updateReturns400OnInvalidBody() throws Exception {
    String requestBody =
        """
        {
          "city": "",
          "basePrice": -10.00,
          "available": null
        }
        """;

    mockMvc
        .perform(
            put("/api/drivers/" + driverToken)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateForbidsOtherDriver() throws Exception {
    String requestBody =
        """
        {
          "city": "Curitiba",
          "basePrice": 100.00,
          "available": true
        }
        """;

    mockMvc
        .perform(
            put("/api/drivers/" + driverToken)
                .with(otherDriverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteReturns204ForOwner() throws Exception {
    mockMvc
        .perform(delete("/api/drivers/" + driverToken).with(ownerJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteReturns204ForAdmin() throws Exception {
    mockMvc
        .perform(delete("/api/drivers/" + driverToken).with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteForbidsOtherDriver() throws Exception {
    mockMvc
        .perform(delete("/api/drivers/" + driverToken).with(otherDriverJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void restoreReturns200ForAdmin() throws Exception {
    // Delete first
    mockMvc
        .perform(delete("/api/drivers/" + driverToken).with(adminJwt()))
        .andExpect(status().isNoContent());

    // Restore
    mockMvc
        .perform(post("/api/drivers/" + driverToken + "/restore").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(driverToken));
  }

  @Test
  void restoreForbidsNonAdmin() throws Exception {
    mockMvc
        .perform(post("/api/drivers/" + driverToken + "/restore").with(ownerJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void restoreReturns409WhenAlreadyActive() throws Exception {
    mockMvc
        .perform(post("/api/drivers/" + driverToken + "/restore").with(adminJwt()))
        .andExpect(status().isConflict());
  }
}
