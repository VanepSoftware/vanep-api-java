package br.com.vanep.drivercnh.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.media.enums.MediaVisibility;
import br.com.vanep.media.repository.MediaFileRepository;
import br.com.vanep.media.storage.StorageService;
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
class DriverCnhPhotoControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private DriverCnhRepository cnhs;
  @Autowired private MediaFileRepository mediaFiles;
  @Autowired private StorageService storage;

  private MockMvc mockMvc;
  private String cnhToken;
  private String ownerUid;
  private String ownerEmail;
  private String strangerUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    DriverModel driver = createDriver("dono@vanep.com", "12345678909");
    ownerUid = driver.getUser().getToken();
    ownerEmail = driver.getUser().getEmail();
    strangerUid = createDriver("estranho@vanep.com", "15350946056").getUser().getToken();

    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver);
    cnh.setRegistrationNumber("11111111111");
    cnh.setCategory("D");
    cnh.setIssueDate(LocalDate.of(2020, 1, 15));
    cnh.setValidUntil(LocalDate.of(2030, 1, 15));
    cnhToken = cnhs.save(cnh).getToken();
  }

  private static byte[] png() {
    return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02};
  }

  private String path() {
    return "/api/driver-cnhs/" + cnhToken + "/photo";
  }

  private void upload(JwtRequestPostProcessor who) throws Exception {
    mockMvc
        .perform(
            multipart(path())
                .file(new MockMultipartFile("file", "cnh.png", "image/png", png()))
                .with(who))
        .andExpect(status().isCreated());
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc
        .perform(
            multipart(path()).file(new MockMultipartFile("file", "cnh.png", "image/png", png())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void theOwnerUploadsTheirOwnCnhPhotoAndItIsBornPrivate() throws Exception {
    upload(ownerJwt());

    var media = mediaFiles.findAll().get(0);
    assertThat(media.getVisibility()).isEqualTo(MediaVisibility.PRIVATE);
    assertThat(media.getObjectKey()).startsWith("driver-cnh/" + cnhToken + "/photo/");
    assertThat(cnhs.findByToken(cnhToken).orElseThrow().getPhoto()).isNotNull();
  }

  @Test
  void readingTheCnhCarriesThePhotoWithoutAnotherCall() throws Exception {
    upload(ownerJwt());

    mockMvc
        .perform(get("/api/driver-cnhs/" + cnhToken).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.photoUrl").value(path()));
  }

  @Test
  void aCnhWithoutPhotoCarriesNoUrl() throws Exception {
    mockMvc
        .perform(get("/api/driver-cnhs/" + cnhToken).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.photoUrl").doesNotExist());
  }

  @Test
  void replacingThePhotoReleasesThePreviousOne() throws Exception {
    upload(ownerJwt());
    var first = mediaFiles.findAll().get(0);
    String firstKey = first.getObjectKey();

    upload(ownerJwt());

    assertThat(mediaFiles.findAll()).hasSize(1);
    assertThat(storage.exists(firstKey)).isFalse();
  }

  @Test
  void theOwnerDownloadsTheirOwnCnhPhoto() throws Exception {
    upload(ownerJwt());

    MvcResult result =
        mockMvc.perform(get(path()).with(ownerJwt())).andExpect(status().isOk()).andReturn();

    assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(png());
  }

  @Test
  void anUnrelatedDriverIsRefusedWithoutReceivingAnyContent() throws Exception {
    upload(ownerJwt());

    MvcResult result =
        mockMvc
            .perform(get(path()).with(strangerJwt()))
            .andExpect(status().isForbidden())
            .andReturn();

    assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
  }

  @Test
  void theAdminReadsTheCnhPhoto() throws Exception {
    upload(ownerJwt());

    mockMvc.perform(get(path()).with(adminJwt())).andExpect(status().isOk());
  }

  @Test
  void downloadingAPhotoThatDoesNotExistIsNotFound() throws Exception {
    mockMvc.perform(get(path()).with(ownerJwt())).andExpect(status().isNotFound());
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
            new SimpleGrantedAuthority("show_driver_cnh"),
            new SimpleGrantedAuthority("update_driver_cnh"));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            token ->
                token
                    .claim("uid", ownerUid)
                    .claim("roles", List.of("ROLE_DRIVER"))
                    .subject(ownerEmail))
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
