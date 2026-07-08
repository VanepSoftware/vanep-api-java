package br.com.vanep.vehicle.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.Driver;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.vehicle.Vehicle;
import br.com.vanep.vehicle.repository.VehicleRepository;
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
class VehicleControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private VehicleRepository vehicles;

  private MockMvc mockMvc;
  private String driverToken;
  private String driverEmail;
  private String ownerUid;
  private String vehicleToken;

  private String otherDriverEmail;
  private String otherOwnerUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    // 1. Create main driver
    User user = new User();
    user.setType(UserType.DRIVER);
    user.setName("Test Driver");
    user.setEmail("driver@vanep.com");
    user.setDocument("12345678909");
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    user = users.save(user);

    ownerUid = user.getToken();
    driverEmail = user.getEmail();

    Driver driver = new Driver();
    driver.setUser(user);
    driver.setBasePrice(new BigDecimal("100.00"));
    driver = drivers.save(driver);
    driverToken = driver.getToken();

    // 2. Create another driver for ownership tests
    User user2 = new User();
    user2.setType(UserType.DRIVER);
    user2.setName("Other Driver");
    user2.setEmail("otherdriver@vanep.com");
    user2.setDocument("98765432109");
    user2.setVerified(true);
    user2.setTermsAcceptedAt(Instant.now());
    user2 = users.save(user2);

    otherOwnerUid = user2.getToken();
    otherDriverEmail = user2.getEmail();

    Driver driver2 = new Driver();
    driver2.setUser(user2);
    driver2.setBasePrice(new BigDecimal("150.00"));
    driver2 = drivers.save(driver2);

    // 3. Create admin user
    User admin = new User();
    admin.setType(UserType.ADMIN);
    admin.setName("Admin User");
    admin.setEmail("admin@vanep.com");
    admin.setDocument("00000000000");
    admin.setToken("admin-uid");
    admin.setVerified(true);
    admin.setTermsAcceptedAt(Instant.now());
    users.save(admin);

    // 4. Create a vehicle for the main driver
    Vehicle vehicle = new Vehicle();
    vehicle.setDriver(driver);
    vehicle.setPlate("ABC1D23");
    vehicle.setBrand("Ford");
    vehicle.setModel("Transit");
    vehicle.setManufactureYear(2022);
    vehicle.setColor("White");
    vehicle.setCapacity(15);
    vehicle = vehicles.save(vehicle);
    vehicleToken = vehicle.getToken();
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
            new SimpleGrantedAuthority("list_vehicles"),
            new SimpleGrantedAuthority("show_vehicle"),
            new SimpleGrantedAuthority("create_vehicle"),
            new SimpleGrantedAuthority("update_vehicle"),
            new SimpleGrantedAuthority("delete_vehicle"),
            new SimpleGrantedAuthority("restore_vehicle"));
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
            new SimpleGrantedAuthority("list_vehicles"),
            new SimpleGrantedAuthority("create_vehicle"));
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
            new SimpleGrantedAuthority("list_vehicles"),
            new SimpleGrantedAuthority("create_vehicle"));
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
        .perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createForbidsClient() throws Exception {
    String body =
        """
        {
          "plate": "XYZ-9876",
          "brand": "Mercedes-Benz",
          "model": "Sprinter",
          "manufactureYear": 2023,
          "color": "Silver",
          "capacity": 20
        }
        """;
    mockMvc
        .perform(
            post("/api/vehicles")
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
          "plate": "XYZ-9876",
          "brand": "Mercedes-Benz",
          "model": "Sprinter",
          "manufactureYear": 2023,
          "color": "Silver",
          "capacity": 20,
          "photoFrontUrl": "http://img.com/front.jpg",
          "photoSideUrl": "http://img.com/side.jpg",
          "photoDocumentUrl": "http://img.com/doc.jpg"
        }
        """;

    mockMvc
        .perform(
            post("/api/vehicles")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").exists())
        .andExpect(jsonPath("$.plate").value("XYZ-9876"))
        .andExpect(jsonPath("$.driverToken").value(driverToken))
        .andExpect(jsonPath("$.photoFrontUrl").value("http://img.com/front.jpg"))
        .andExpect(jsonPath("$.photoSideUrl").value("http://img.com/side.jpg"))
        .andExpect(jsonPath("$.photoDocumentUrl").value("http://img.com/doc.jpg"));
  }

  @Test
  void createReturns400ForInvalidBody() throws Exception {
    String body =
        """
        {
          "plate": "INVALID_PLATE",
          "brand": "",
          "model": "Sprinter",
          "manufactureYear": 2023,
          "color": "Silver",
          "capacity": 0
        }
        """;

    mockMvc
        .perform(
            post("/api/vehicles")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createReturns409ForDuplicatePlate() throws Exception {
    String body =
        """
        {
          "plate": "ABC1D23",
          "brand": "Ford",
          "model": "Transit",
          "manufactureYear": 2022,
          "color": "White",
          "capacity": 15
        }
        """;

    mockMvc
        .perform(
            post("/api/vehicles")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict());
  }

  @Test
  void listReturnsOnlyDriverVehiclesForDriver() throws Exception {
    mockMvc
        .perform(get("/api/vehicles").with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].token").value(vehicleToken));
  }

  @Test
  void listReturnsEmptyForOtherDriver() throws Exception {
    mockMvc
        .perform(get("/api/vehicles").with(otherDriverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void listReturnsAllForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/vehicles").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void getByTokenReturns200ForOwner() throws Exception {
    mockMvc
        .perform(get("/api/vehicles/" + vehicleToken).with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(vehicleToken));
  }

  @Test
  void getByTokenReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/vehicles/" + vehicleToken).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(vehicleToken));
  }

  @Test
  void getByTokenReturns403ForOtherDriver() throws Exception {
    mockMvc
        .perform(get("/api/vehicles/" + vehicleToken).with(otherDriverJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getByTokenReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(get("/api/vehicles/non-existent-token").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateReturns200ForOwner() throws Exception {
    String body =
        """
        {
          "plate": "ABC1D23",
          "brand": "Ford",
          "model": "Transit Updated",
          "manufactureYear": 2023,
          "color": "Blue",
          "capacity": 16,
          "photoFrontUrl": "http://img.com/front-up.jpg",
          "photoSideUrl": "http://img.com/side-up.jpg",
          "photoDocumentUrl": "http://img.com/doc-up.jpg"
        }
        """;

    mockMvc
        .perform(
            put("/api/vehicles/" + vehicleToken)
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.model").value("Transit Updated"))
        .andExpect(jsonPath("$.color").value("Blue"))
        .andExpect(jsonPath("$.capacity").value(16))
        .andExpect(jsonPath("$.photoFrontUrl").value("http://img.com/front-up.jpg"));
  }

  @Test
  void updateReturns403ForOtherDriver() throws Exception {
    String body =
        """
        {
          "plate": "ABC1D23",
          "brand": "Ford",
          "model": "Transit Updated",
          "manufactureYear": 2023,
          "color": "Blue",
          "capacity": 16
        }
        """;
    mockMvc
        .perform(
            put("/api/vehicles/" + vehicleToken)
                .with(otherDriverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteReturns204ForOwner() throws Exception {
    mockMvc
        .perform(delete("/api/vehicles/" + vehicleToken).with(driverJwt()))
        .andExpect(status().isNoContent());

    // After soft-delete, accessing should return 404
    mockMvc
        .perform(get("/api/vehicles/" + vehicleToken).with(driverJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void restoreReturns200ForOwner() throws Exception {
    // Delete first
    mockMvc
        .perform(delete("/api/vehicles/" + vehicleToken).with(driverJwt()))
        .andExpect(status().isNoContent());

    // Restore it
    mockMvc
        .perform(post("/api/vehicles/" + vehicleToken + "/restore").with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(vehicleToken))
        .andExpect(jsonPath("$.active").value(true));

    // Can get it again
    mockMvc
        .perform(get("/api/vehicles/" + vehicleToken).with(driverJwt()))
        .andExpect(status().isOk());
  }

  @Test
  void restoreReturns409ForActiveVehicle() throws Exception {
    mockMvc
        .perform(post("/api/vehicles/" + vehicleToken + "/restore").with(driverJwt()))
        .andExpect(status().isConflict());
  }
}
