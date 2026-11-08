package br.com.vanep.vehicle.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
class VehicleMeControllerTest {

  private static final String VAN =
      """
      {"plate":"ABC1D23","brand":"Mercedes-Benz","model":"Sprinter",
       "manufactureYear":2021,"color":"Branca","capacity":15}
      """;

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private VehicleRepository vehicles;

  private MockMvc mockMvc;
  private DriverModel driver;
  private DriverModel otherDriver;
  private String clientUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    driver = saveDriver("carlos@vanep.com", "12345678909");
    otherDriver = saveDriver("ana@vanep.com", "98765432109");
    clientUid = saveUser(UserType.CLIENT, "cliente@vanep.com", "11122233344").getToken();
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/vehicles/me")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/api/vehicles/me").contentType(MediaType.APPLICATION_JSON).content(VAN))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aDriverRegistersTheirOwnVanWithoutAnyPermission() throws Exception {
    mockMvc
        .perform(
            post("/api/vehicles/me")
                .with(as(driver.getUser().getToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VAN))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.driverToken").value(driver.getToken()))
        .andExpect(jsonPath("$.model").value("Sprinter"))
        .andExpect(jsonPath("$.photoFrontUrl").doesNotExist());

    assertThat(vehicles.findByDriverId(driver.getId())).hasSize(1);
  }

  @Test
  void theDriverTokenInTheBodyCannotRegisterAVanForSomeoneElse() throws Exception {
    String body = VAN.replace("{", "{\"driverToken\":\"" + otherDriver.getToken() + "\",");

    mockMvc
        .perform(
            post("/api/vehicles/me")
                .with(as(driver.getUser().getToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.driverToken").value(driver.getToken()));

    assertThat(vehicles.findByDriverId(otherDriver.getId())).isEmpty();
  }

  @Test
  void rejectsAnInvalidVan() throws Exception {
    mockMvc
        .perform(
            post("/api/vehicles/me")
                .with(as(driver.getUser().getToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VAN.replace("ABC1D23", "placa")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsAPlateAlreadyRegistered() throws Exception {
    saveVehicle(otherDriver, "ABC1D23");

    mockMvc
        .perform(
            post("/api/vehicles/me")
                .with(as(driver.getUser().getToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VAN))
        .andExpect(status().isConflict());
  }

  @Test
  void aClientCannotRegisterAVan() throws Exception {
    mockMvc
        .perform(
            post("/api/vehicles/me")
                .with(as(clientUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VAN))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/vehicles/me").with(as(clientUid))).andExpect(status().isForbidden());
  }

  @Test
  void listsOnlyTheCallersVans() throws Exception {
    saveVehicle(driver, "ABC1D23");
    saveVehicle(otherDriver, "XYZ9Z99");

    mockMvc
        .perform(get("/api/vehicles/me").with(as(driver.getUser().getToken())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].plate").value("ABC1D23"));
  }

  @Test
  void replacingAPhotoChangesItsUrlSoClientsReloadIt() throws Exception {
    VehicleModel van = saveVehicle(driver, "ABC1D23");
    uploadFront(van);
    String firstUrl = readFrontUrl();

    uploadFront(van);

    assertThat(readFrontUrl()).startsWith("/api/vehicles/" + van.getToken() + "/photo-front?v=");
    assertThat(readFrontUrl()).isNotEqualTo(firstUrl);
  }

  private void uploadFront(VehicleModel van) throws Exception {
    mockMvc
        .perform(
            multipart("/api/vehicles/" + van.getToken() + "/photo-front")
                .file(new MockMultipartFile("file", "frente.png", "image/png", png()))
                .with(as(driver.getUser().getToken())))
        .andExpect(status().isCreated());
  }

  private String readFrontUrl() throws Exception {
    String json =
        mockMvc
            .perform(get("/api/vehicles/me").with(as(driver.getUser().getToken())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(json, "$[0].photoFrontUrl");
  }

  private DriverModel saveDriver(String email, String document) {
    DriverModel model = new DriverModel();
    model.setUser(saveUser(UserType.DRIVER, email, document));
    model.setBasePrice(new BigDecimal("100.00"));
    return drivers.save(model);
  }

  private UserModel saveUser(UserType type, String email, String document) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName(email);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }

  private VehicleModel saveVehicle(DriverModel owner, String plate) {
    VehicleModel vehicle = new VehicleModel();
    vehicle.setDriver(owner);
    vehicle.setPlate(plate);
    vehicle.setBrand("Ford");
    vehicle.setModel("Transit");
    vehicle.setManufactureYear(2022);
    vehicle.setColor("Branca");
    vehicle.setCapacity(15);
    return vehicles.save(vehicle);
  }

  private static byte[] png() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int value : new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02}) {
      out.write(value);
    }
    return out.toByteArray();
  }

  private JwtRequestPostProcessor as(String uid) {
    return jwt()
        .jwt(token -> token.claim("uid", uid).subject(uid).claim("roles", List.of("ROLE_USER")))
        .authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }
}
