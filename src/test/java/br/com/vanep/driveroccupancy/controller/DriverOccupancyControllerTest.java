package br.com.vanep.driveroccupancy.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.DriverApprovalStatus;
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
class DriverOccupancyControllerTest {
  private static final String OCCUPANCY = "/api/drivers/me/occupancy";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;

  private MockMvc mockMvc;
  private String driverUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    UserModel user = saveUser(UserType.DRIVER, "driver@vanep.com", "66666666666");
    DriverModel driver = new DriverModel();
    driver.setUser(user);
    driver.setBasePrice(BigDecimal.valueOf(320));
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    drivers.save(driver);
    driverUid = user.getToken();
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get(OCCUPANCY)).andExpect(status().isUnauthorized());
  }

  @Test
  void answersEveryShiftForTheDriver() throws Exception {
    mockMvc
        .perform(get(OCCUPANCY).with(callerJwt(driverUid, "ROLE_DRIVER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shifts.length()").value(3))
        .andExpect(jsonPath("$.shifts[0].shift").value("MORNING"))
        .andExpect(jsonPath("$.shifts[0].passengers").value(0))
        .andExpect(jsonPath("$.shifts[1].shift").value("AFTERNOON"))
        .andExpect(jsonPath("$.shifts[2].shift").value("NIGHT"))
        .andExpect(jsonPath("$.students").value(0))
        .andExpect(jsonPath("$.studentsWithFamily").value(0));
  }

  @Test
  void forbidsAClient() throws Exception {
    String clientUid = saveUser(UserType.CLIENT, "client@vanep.com", "11111111111").getToken();

    mockMvc
        .perform(get(OCCUPANCY).with(callerJwt(clientUid, "ROLE_CLIENT")))
        .andExpect(status().isForbidden());
  }

  @Test
  void forbidsAnAssistant() throws Exception {
    String assistantUid =
        saveUser(UserType.ASSISTANT, "assistant@vanep.com", "22222222222").getToken();

    mockMvc
        .perform(get(OCCUPANCY).with(callerJwt(assistantUid, "ROLE_ASSISTANT")))
        .andExpect(status().isForbidden());
  }

  private JwtRequestPostProcessor callerJwt(String uid, String role) {
    return jwt()
        .jwt(t -> t.claim("uid", uid).claim("roles", List.of(role)))
        .authorities(new SimpleGrantedAuthority(role));
  }

  private UserModel saveUser(UserType type, String email, String document) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName("User");
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }
}
