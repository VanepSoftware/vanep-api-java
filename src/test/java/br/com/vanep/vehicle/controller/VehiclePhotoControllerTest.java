package br.com.vanep.vehicle.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.media.repository.MediaFileRepository;
import br.com.vanep.media.storage.StorageService;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class VehiclePhotoControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private VehicleRepository vehicles;
  @Autowired private MediaFileRepository mediaFiles;
  @Autowired private StorageService storage;

  private MockMvc mockMvc;
  private String vehicleToken;
  private String ownerUid;
  private String strangerUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    DriverModel driver = createDriver("dono@vanep.com", "12345678909");
    ownerUid = driver.getUser().getToken();
    strangerUid = createDriver("estranho@vanep.com", "15350946056").getUser().getToken();

    VehicleModel vehicle = new VehicleModel();
    vehicle.setDriver(driver);
    vehicle.setPlate("ABC1D23");
    vehicle.setBrand("Ford");
    vehicle.setModel("Transit");
    vehicle.setManufactureYear(2022);
    vehicle.setColor("Branca");
    vehicle.setCapacity(15);
    vehicleToken = vehicles.save(vehicle).getToken();
  }

  private static byte[] png() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int value : new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02}) {
      out.write(value);
    }
    return out.toByteArray();
  }

  private String path(String slot) {
    return "/api/vehicles/" + vehicleToken + "/" + slot;
  }

  private void upload(String slot, String name, JwtRequestPostProcessor who) throws Exception {
    mockMvc
        .perform(
            multipart(path(slot))
                .file(new MockMultipartFile("file", name, "image/png", png()))
                .with(who))
        .andExpect(status().isCreated());
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc
        .perform(
            multipart(path("photo-front"))
                .file(new MockMultipartFile("file", "frente.png", "image/png", png())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void theOwnerUploadsEachOfTheThreeSlots() throws Exception {
    upload("photo-front", "frente.png", ownerJwt());
    upload("photo-side", "lateral.png", ownerJwt());
    upload("photo-document", "documento.png", ownerJwt());

    VehicleModel stored = vehicles.findByToken(vehicleToken).orElseThrow();
    assertThat(stored.getPhotoFront()).isNotNull();
    assertThat(stored.getPhotoSide()).isNotNull();
    assertThat(stored.getPhotoDocument()).isNotNull();
    assertThat(mediaFiles.findAll()).hasSize(3);
  }

  @Test
  void uploadingTheSideDoesNotTouchTheFront() throws Exception {
    upload("photo-front", "frente.png", ownerJwt());
    var front = vehicles.findByToken(vehicleToken).orElseThrow().getPhotoFront();

    upload("photo-side", "lateral.png", ownerJwt());

    VehicleModel stored = vehicles.findByToken(vehicleToken).orElseThrow();
    assertThat(stored.getPhotoFront().getToken()).isEqualTo(front.getToken());
    assertThat(stored.getPhotoSide().getToken()).isNotEqualTo(front.getToken());
    assertThat(storage.exists(front.getObjectKey())).isTrue();
  }

  @Test
  void replacingOneSlotReleasesOnlyItsOwnPreviousFile() throws Exception {
    upload("photo-front", "frente.png", ownerJwt());
    upload("photo-side", "lateral.png", ownerJwt());
    VehicleModel before = vehicles.findByToken(vehicleToken).orElseThrow();
    String oldFrontKey = before.getPhotoFront().getObjectKey();
    String sideKey = before.getPhotoSide().getObjectKey();

    upload("photo-front", "frente-nova.png", ownerJwt());

    assertThat(storage.exists(oldFrontKey)).isFalse();
    assertThat(storage.exists(sideKey)).isTrue();
    assertThat(mediaFiles.findAll()).hasSize(2);
  }

  @Test
  void readingTheVehicleCarriesTheThreeUrlsWithoutAnotherCall() throws Exception {
    upload("photo-front", "frente.png", ownerJwt());
    upload("photo-side", "lateral.png", ownerJwt());
    upload("photo-document", "documento.png", ownerJwt());

    mockMvc
        .perform(get("/api/vehicles/" + vehicleToken).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.photoFrontUrl").value(path("photo-front")))
        .andExpect(jsonPath("$.photoSideUrl").value(path("photo-side")))
        .andExpect(jsonPath("$.photoDocumentUrl").value(path("photo-document")));
  }

  @Test
  void aSlotWithoutFileCarriesNoUrl() throws Exception {
    upload("photo-front", "frente.png", ownerJwt());

    mockMvc
        .perform(get("/api/vehicles/" + vehicleToken).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.photoFrontUrl").value(path("photo-front")))
        .andExpect(jsonPath("$.photoSideUrl").doesNotExist())
        .andExpect(jsonPath("$.photoDocumentUrl").doesNotExist());
  }

  @Test
  void eachSlotHasItsOwnFolderInTheStoredPath() throws Exception {
    upload("photo-front", "frente.png", ownerJwt());
    upload("photo-side", "lateral.png", ownerJwt());

    VehicleModel stored = vehicles.findByToken(vehicleToken).orElseThrow();
    assertThat(stored.getPhotoFront().getObjectKey())
        .startsWith("vehicle/" + vehicleToken + "/photo-front/");
    assertThat(stored.getPhotoSide().getObjectKey())
        .startsWith("vehicle/" + vehicleToken + "/photo-side/");
  }

  @Test
  void refusesAFileLyingAboutItsType() throws Exception {
    byte[] notAnImage = "MZ nao e imagem".getBytes(StandardCharsets.UTF_8);

    mockMvc
        .perform(
            multipart(path("photo-front"))
                .file(new MockMultipartFile("file", "frente.png", "image/png", notAnImage))
                .with(ownerJwt()))
        .andExpect(status().isBadRequest());

    assertThat(mediaFiles.findAll()).isEmpty();
  }

  @Test
  void theOwnerDownloadsEachSlot() throws Exception {
    upload("photo-document", "documento.png", ownerJwt());

    MvcResult result =
        mockMvc
            .perform(get(path("photo-document")).with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(png());
  }

  @Test
  void anUnrelatedDriverIsRefusedWithoutReceivingAnyContent() throws Exception {
    upload("photo-document", "documento.png", ownerJwt());

    MvcResult result =
        mockMvc
            .perform(get(path("photo-document")).with(strangerJwt()))
            .andExpect(status().isForbidden())
            .andReturn();

    assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
  }

  @Test
  void downloadingASlotThatWasNeverFilledIsNotFound() throws Exception {
    upload("photo-front", "frente.png", ownerJwt());

    mockMvc.perform(get(path("photo-side")).with(adminJwt())).andExpect(status().isNotFound());
  }

  private DriverModel createDriver(String email, String document) {
    UserModel user = new UserModel();
    user.setType(UserType.DRIVER);
    user.setName("Motorista");
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    user = users.save(user);

    DriverModel driver = new DriverModel();
    driver.setUser(user);
    driver.setBasePrice(new BigDecimal("100.00"));
    return drivers.save(driver);
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(
            token ->
                token
                    .claim("uid", "admin-uid")
                    .claim("roles", List.of("ROLE_ADMIN"))
                    .subject("admin@vanep.com"))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("show_vehicle"),
            new SimpleGrantedAuthority("update_vehicle"));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            token ->
                token
                    .claim("uid", ownerUid)
                    .claim("roles", List.of("ROLE_DRIVER"))
                    .subject("dono@vanep.com"))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }

  private JwtRequestPostProcessor strangerJwt() {
    return jwt()
        .jwt(
            token ->
                token
                    .claim("uid", strangerUid)
                    .claim("roles", List.of("ROLE_DRIVER"))
                    .subject("estranho@vanep.com"))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }
}
