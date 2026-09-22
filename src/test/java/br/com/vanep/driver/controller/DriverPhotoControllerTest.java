package br.com.vanep.driver.controller;

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
class DriverPhotoControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private MediaFileRepository mediaFiles;
  @Autowired private StorageService storage;

  private MockMvc mockMvc;
  private String driverToken;
  private String driverUid;
  private String strangerUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    DriverModel driver = createDriver("fabio@vanep.com", "15350946056");
    driverToken = driver.getToken();
    driverUid = driver.getUser().getToken();
    strangerUid = createDriver("gustavo@vanep.com", "01234567890").getUser().getToken();
  }

  private static byte[] png() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int value : new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02}) {
      out.write(value);
    }
    return out.toByteArray();
  }

  private void upload(byte[] content, String name, JwtRequestPostProcessor who) throws Exception {
    mockMvc
        .perform(
            multipart("/api/drivers/" + driverToken + "/photo")
                .file(new MockMultipartFile("file", name, "image/png", content))
                .with(who))
        .andExpect(status().isCreated());
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc
        .perform(
            multipart("/api/drivers/" + driverToken + "/photo")
                .file(new MockMultipartFile("file", "foto.png", "image/png", png())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void theOwnerUploadsTheirOwnPhoto() throws Exception {
    upload(png(), "foto.png", ownerJwt());

    assertThat(mediaFiles.findAll()).hasSize(1);
    assertThat(drivers.findByToken(driverToken).orElseThrow().getPhoto()).isNotNull();
  }

  @Test
  void readingTheDriverCarriesThePhotoWithoutAnotherCall() throws Exception {
    upload(png(), "foto.png", adminJwt());

    mockMvc
        .perform(get("/api/drivers/" + driverToken).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.photo").value("/api/drivers/" + driverToken + "/photo"));
  }

  @Test
  void aDriverWithoutPhotoCarriesNoUrl() throws Exception {
    mockMvc
        .perform(get("/api/drivers/" + driverToken).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.photo").doesNotExist());
  }

  @Test
  void theStoredPathNeverCarriesTheOriginalName() throws Exception {
    upload(png(), "retrato do fabio.png", adminJwt());

    var media = mediaFiles.findAll().get(0);
    assertThat(media.getOriginalName()).isEqualTo("retrato do fabio.png");
    assertThat(media.getObjectKey()).doesNotContain("retrato");
    assertThat(media.getObjectKey()).startsWith("driver/" + driverToken + "/photo/");
  }

  @Test
  void replacingThePhotoReleasesThePreviousOne() throws Exception {
    upload(png(), "primeira.png", adminJwt());
    var first = mediaFiles.findAll().get(0);
    String firstKey = first.getObjectKey();
    assertThat(storage.exists(firstKey)).isTrue();

    upload(png(), "segunda.png", adminJwt());

    assertThat(mediaFiles.findAll()).hasSize(1);
    assertThat(mediaFiles.findByToken(first.getToken())).isEmpty();
    assertThat(storage.exists(firstKey)).isFalse();
  }

  @Test
  void refusesAFileLyingAboutItsType() throws Exception {
    byte[] notAnImage = "MZ nao e imagem".getBytes(StandardCharsets.UTF_8);

    mockMvc
        .perform(
            multipart("/api/drivers/" + driverToken + "/photo")
                .file(new MockMultipartFile("file", "foto.png", "image/png", notAnImage))
                .with(adminJwt()))
        .andExpect(status().isBadRequest());

    assertThat(mediaFiles.findAll()).isEmpty();
  }

  @Test
  void theOwnerDownloadsTheirOwnPhoto() throws Exception {
    upload(png(), "foto.png", ownerJwt());

    MvcResult result =
        mockMvc
            .perform(get("/api/drivers/" + driverToken + "/photo").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(png());
  }

  @Test
  void anUnrelatedUserIsRefusedWithoutReceivingAnyContent() throws Exception {
    upload(png(), "foto.png", adminJwt());

    MvcResult result =
        mockMvc
            .perform(get("/api/drivers/" + driverToken + "/photo").with(strangerJwt()))
            .andExpect(status().isForbidden())
            .andReturn();

    assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
  }

  @Test
  void downloadingAPhotoThatDoesNotExistIsNotFound() throws Exception {
    mockMvc
        .perform(get("/api/drivers/" + driverToken + "/photo").with(adminJwt()))
        .andExpect(status().isNotFound());
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
        .jwt(token -> token.claim("uid", "admin-uid").claim("roles", List.of("ROLE_ADMIN")))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("show_driver"),
            new SimpleGrantedAuthority("update_driver"));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(token -> token.claim("uid", driverUid).claim("roles", List.of("ROLE_DRIVER")))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }

  private JwtRequestPostProcessor strangerJwt() {
    return jwt()
        .jwt(token -> token.claim("uid", strangerUid).claim("roles", List.of("ROLE_DRIVER")))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }
}
