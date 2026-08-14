package br.com.vanep.drivercnh.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
class DriverCnhControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private DriverCnhRepository cnhs;

  private MockMvc mockMvc;
  private String driverEmail;
  private String ownerUid;
  private String cnhToken;

  private String otherDriverToken;
  private String otherDriverEmail;
  private String otherOwnerUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    UserModel user = new UserModel();
    user.setType(UserType.DRIVER);
    user.setName("Test Driver");
    user.setEmail("driver@vanep.com");
    user.setDocument("12345678909");
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    user = users.save(user);
    ownerUid = user.getToken();
    driverEmail = user.getEmail();

    DriverModel driver = new DriverModel();
    driver.setUser(user);
    driver.setBasePrice(new BigDecimal("100.00"));
    driver = drivers.save(driver);

    UserModel user2 = new UserModel();
    user2.setType(UserType.DRIVER);
    user2.setName("Other Driver");
    user2.setEmail("otherdriver@vanep.com");
    user2.setDocument("98765432109");
    user2.setVerified(true);
    user2.setTermsAcceptedAt(Instant.now());
    user2 = users.save(user2);
    otherOwnerUid = user2.getToken();
    otherDriverEmail = user2.getEmail();

    DriverModel driver2 = new DriverModel();
    driver2.setUser(user2);
    driver2.setBasePrice(new BigDecimal("150.00"));
    driver2 = drivers.save(driver2);
    otherDriverToken = driver2.getToken();

    UserModel admin = new UserModel();
    admin.setType(UserType.ADMIN);
    admin.setName("Admin User");
    admin.setEmail("admin@vanep.com");
    admin.setDocument("00000000000");
    admin.setToken("admin-uid");
    admin.setVerified(true);
    admin.setTermsAcceptedAt(Instant.now());
    users.save(admin);

    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver);
    cnh.setRegistrationNumber("11111111111");
    cnh.setCategory("D");
    cnh.setIssueDate(LocalDate.of(2020, 1, 15));
    cnh.setValidUntil(LocalDate.of(2030, 1, 15));
    cnh = cnhs.save(cnh);
    cnhToken = cnh.getToken();
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", "admin-uid")
                    .claim("roles", List.of("ROLE_ADMIN"))
                    .subject("admin@vanep.com"))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("list_driver_cnhs"),
            new SimpleGrantedAuthority("show_driver_cnh"),
            new SimpleGrantedAuthority("create_driver_cnh"),
            new SimpleGrantedAuthority("update_driver_cnh"),
            new SimpleGrantedAuthority("delete_driver_cnh"),
            new SimpleGrantedAuthority("restore_driver_cnh"));
  }

  private JwtRequestPostProcessor driverJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", ownerUid)
                    .claim("roles", List.of("ROLE_DRIVER"))
                    .subject(driverEmail))
        .authorities(
            new SimpleGrantedAuthority("ROLE_DRIVER"),
            new SimpleGrantedAuthority("list_driver_cnhs"),
            new SimpleGrantedAuthority("create_driver_cnh"));
  }

  private JwtRequestPostProcessor otherDriverJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", otherOwnerUid)
                    .claim("roles", List.of("ROLE_DRIVER"))
                    .subject(otherDriverEmail))
        .authorities(
            new SimpleGrantedAuthority("ROLE_DRIVER"),
            new SimpleGrantedAuthority("list_driver_cnhs"),
            new SimpleGrantedAuthority("create_driver_cnh"));
  }

  private JwtRequestPostProcessor clientJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", "client-uid")
                    .claim("roles", List.of("ROLE_CLIENT"))
                    .subject("client@vanep.com"))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  @Test
  void createRequiresAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/driver-cnhs").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createForbidsClient() throws Exception {
    String body =
        """
        {
          "registrationNumber": "22222222222",
          "category": "B",
          "issueDate": "2021-06-10",
          "validUntil": "2031-06-10"
        }
        """;
    mockMvc
        .perform(
            post("/api/driver-cnhs")
                .with(clientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void createReturns201ForDriver() throws Exception {
    String body =
        """
        {
          "registrationNumber": "22222222222",
          "category": "B",
          "issueDate": "2021-06-10",
          "validUntil": "2031-06-10",
          "issuingState": "SP",
          "photoUrl": "http://img.com/cnh.jpg"
        }
        """;

    mockMvc
        .perform(
            post("/api/driver-cnhs")
                .with(otherDriverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").exists())
        .andExpect(jsonPath("$.registrationNumber").value("22222222222"))
        .andExpect(jsonPath("$.category").value("B"))
        .andExpect(jsonPath("$.driverToken").value(otherDriverToken))
        .andExpect(jsonPath("$.photoUrl").value("http://img.com/cnh.jpg"));
  }

  @Test
  void createReturns400ForInvalidBody() throws Exception {
    String body =
        """
        {
          "registrationNumber": "abc",
          "category": "",
          "validUntil": "2031-06-10"
        }
        """;

    mockMvc
        .perform(
            post("/api/driver-cnhs")
                .with(otherDriverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createReturns409ForDuplicateRegistration() throws Exception {
    String body =
        """
        {
          "registrationNumber": "11111111111",
          "category": "B",
          "issueDate": "2021-06-10",
          "validUntil": "2031-06-10"
        }
        """;

    mockMvc
        .perform(
            post("/api/driver-cnhs")
                .with(otherDriverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict());
  }

  @Test
  void createReturns409WhenDriverAlreadyHasCnh() throws Exception {
    String body =
        """
        {
          "registrationNumber": "33333333333",
          "category": "B",
          "issueDate": "2021-06-10",
          "validUntil": "2031-06-10"
        }
        """;

    mockMvc
        .perform(
            post("/api/driver-cnhs")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict());
  }

  @Test
  void listReturnsOnlyDriverCnhForDriver() throws Exception {
    mockMvc
        .perform(get("/api/driver-cnhs").with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].token").value(cnhToken));
  }

  @Test
  void listReturnsEmptyForOtherDriver() throws Exception {
    mockMvc
        .perform(get("/api/driver-cnhs").with(otherDriverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void listReturnsAllForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/driver-cnhs").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void getByTokenReturns200ForOwner() throws Exception {
    mockMvc
        .perform(get("/api/driver-cnhs/" + cnhToken).with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(cnhToken));
  }

  @Test
  void getByTokenReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/driver-cnhs/" + cnhToken).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(cnhToken));
  }

  @Test
  void getByTokenReturns403ForOtherDriver() throws Exception {
    mockMvc
        .perform(get("/api/driver-cnhs/" + cnhToken).with(otherDriverJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getByTokenReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(get("/api/driver-cnhs/non-existent-token").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateReturns200ForOwner() throws Exception {
    String body =
        """
        {
          "registrationNumber": "11111111111",
          "category": "E",
          "issueDate": "2020-01-15",
          "validUntil": "2032-01-15",
          "issuingState": "DF"
        }
        """;

    mockMvc
        .perform(
            put("/api/driver-cnhs/" + cnhToken)
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category").value("E"))
        .andExpect(jsonPath("$.validUntil").value("2032-01-15"));
  }

  @Test
  void updateReturns403ForOtherDriver() throws Exception {
    String body =
        """
        {
          "registrationNumber": "11111111111",
          "category": "E",
          "issueDate": "2020-01-15",
          "validUntil": "2032-01-15"
        }
        """;
    mockMvc
        .perform(
            put("/api/driver-cnhs/" + cnhToken)
                .with(otherDriverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteReturns204ForOwner() throws Exception {
    mockMvc
        .perform(delete("/api/driver-cnhs/" + cnhToken).with(driverJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/driver-cnhs/" + cnhToken).with(driverJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void restoreReturns200ForOwner() throws Exception {
    mockMvc
        .perform(delete("/api/driver-cnhs/" + cnhToken).with(driverJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(post("/api/driver-cnhs/" + cnhToken + "/restore").with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(cnhToken))
        .andExpect(jsonPath("$.active").value(true));

    mockMvc
        .perform(get("/api/driver-cnhs/" + cnhToken).with(driverJwt()))
        .andExpect(status().isOk());
  }

  @Test
  void restoreReturns409ForActiveCnh() throws Exception {
    mockMvc
        .perform(post("/api/driver-cnhs/" + cnhToken + "/restore").with(driverJwt()))
        .andExpect(status().isConflict());
  }
}
