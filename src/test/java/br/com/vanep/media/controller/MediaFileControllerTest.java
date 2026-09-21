package br.com.vanep.media.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.media.enums.MediaPurpose;
import br.com.vanep.media.repository.MediaFileRepository;
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
class MediaFileControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private MediaFileRepository mediaFiles;

  private MockMvc mockMvc;
  private String driverToken;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    DriverModel driver = createDriver("fabio@vanep.com", "15350946056");
    driverToken = driver.getToken();
  }

  private static byte[] jpeg() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int value : new int[] {0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46}) {
      out.write(value);
    }
    return out.toByteArray();
  }

  private MockMultipartFile file(String name, byte[] content) {
    return new MockMultipartFile("file", name, "image/jpeg", content);
  }

  private String uploadPhoto() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/media")
                    .file(file("retrato.jpg", jpeg()))
                    .param("ownerType", "DRIVER")
                    .param("ownerToken", driverToken)
                    .param("purpose", MediaPurpose.PHOTO.name())
                    .with(adminJwt()))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc
        .perform(
            multipart("/api/media")
                .file(file("retrato.jpg", jpeg()))
                .param("ownerType", "DRIVER")
                .param("ownerToken", driverToken)
                .param("purpose", "PHOTO"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void storesAValidImage() throws Exception {
    mockMvc
        .perform(
            multipart("/api/media")
                .file(file("retrato.jpg", jpeg()))
                .param("ownerType", "DRIVER")
                .param("ownerToken", driverToken)
                .param("purpose", "PHOTO")
                .with(adminJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.mimeType").value("image/jpeg"))
        .andExpect(jsonPath("$.visibility").value("PUBLIC"))
        .andExpect(jsonPath("$.id").doesNotExist());

    assertThat(mediaFiles.findAll()).hasSize(1);
  }

  @Test
  void theOriginalNameNeverReachesTheStoredPath() throws Exception {
    String token = uploadPhoto();

    var stored = mediaFiles.findByToken(token).orElseThrow();
    assertThat(stored.getOriginalName()).isEqualTo("retrato.jpg");
    assertThat(stored.getObjectKey()).doesNotContain("retrato");
    assertThat(stored.getObjectKey())
        .isEqualTo("DRIVER/" + driverToken + "/PHOTO/" + token + ".jpg");
  }

  @Test
  void refusesAFileLyingAboutItsType() throws Exception {
    byte[] notAnImage = "MZ este nao e um jpeg".getBytes(StandardCharsets.UTF_8);

    mockMvc
        .perform(
            multipart("/api/media")
                .file(file("retrato.jpg", notAnImage))
                .param("ownerType", "DRIVER")
                .param("ownerToken", driverToken)
                .param("purpose", "PHOTO")
                .with(adminJwt()))
        .andExpect(status().isBadRequest());

    assertThat(mediaFiles.findAll()).isEmpty();
  }

  @Test
  void refusesAPdfWhereOnlyAnImageIsAllowed() throws Exception {
    byte[] pdf = "%PDF-1.7\ncorpo".getBytes(StandardCharsets.US_ASCII);

    mockMvc
        .perform(
            multipart("/api/media")
                .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", pdf))
                .param("ownerType", "DRIVER")
                .param("ownerToken", driverToken)
                .param("purpose", "PHOTO")
                .with(adminJwt()))
        .andExpect(status().isBadRequest());

    assertThat(mediaFiles.findAll()).isEmpty();
  }

  @Test
  void refusesAnUnknownOwner() throws Exception {
    mockMvc
        .perform(
            multipart("/api/media")
                .file(file("retrato.jpg", jpeg()))
                .param("ownerType", "DRIVER")
                .param("ownerToken", "naoexiste")
                .param("purpose", "PHOTO")
                .with(adminJwt()))
        .andExpect(status().isNotFound());

    assertThat(mediaFiles.findAll()).isEmpty();
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
            new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("create_media"));
  }
}
