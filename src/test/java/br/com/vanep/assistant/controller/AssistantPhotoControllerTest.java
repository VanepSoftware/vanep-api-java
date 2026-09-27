package br.com.vanep.assistant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
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
class AssistantPhotoControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private AssistantRepository assistants;
  @Autowired private MediaFileRepository mediaFiles;
  @Autowired private StorageService storage;

  private MockMvc mockMvc;
  private String assistantToken;
  private String assistantUid;
  private String driverUid;
  private String driverEmail;
  private String strangerUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    UserModel driverUser = createUser(UserType.DRIVER, "carlos@vanep.com", "12345678909");
    driverUid = driverUser.getToken();
    driverEmail = driverUser.getEmail();
    DriverModel driver = createDriver(driverUser);

    AssistantModel assistant = createAssistant("bruna@vanep.com", "15350946056");
    assistant.setDriver(driver);
    assistant.setStatus(AssistantStatus.ACTIVE);
    assistant.setActivatedAt(Instant.now());
    assistant = assistants.save(assistant);

    assistantToken = assistant.getToken();
    assistantUid = assistant.getUser().getToken();
    strangerUid = createUser(UserType.DRIVER, "estranho@vanep.com", "01234567890").getToken();
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
            multipart("/api/assistants/" + assistantToken + "/photo")
                .file(new MockMultipartFile("file", name, "image/png", content))
                .with(who))
        .andExpect(status().isCreated());
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc
        .perform(
            multipart("/api/assistants/" + assistantToken + "/photo")
                .file(new MockMultipartFile("file", "foto.png", "image/png", png())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void theAssistantUploadsTheirOwnPhoto() throws Exception {
    upload(png(), "foto.png", assistantJwt());

    assertThat(mediaFiles.findAll()).hasSize(1);
    assertThat(assistants.findByToken(assistantToken).orElseThrow().getPhoto()).isNotNull();
  }

  @Test
  void theDriverAlsoUploadsThePhotoOfTheirAssistant() throws Exception {
    upload(png(), "foto.png", driverJwt());

    assertThat(assistants.findByToken(assistantToken).orElseThrow().getPhoto()).isNotNull();
  }

  @Test
  void listingTheAssistantsCarriesThePhotoWithoutAnotherCall() throws Exception {
    upload(png(), "foto.png", assistantJwt());

    mockMvc
        .perform(get("/api/assistants").with(driverWithListJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].photo").value("/api/assistants/" + assistantToken + "/photo"));
  }

  @Test
  void anAssistantWithoutPhotoCarriesNoUrl() throws Exception {
    mockMvc
        .perform(get("/api/assistants").with(driverWithListJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].photo").doesNotExist());
  }

  @Test
  void theStoredPathNeverCarriesTheOriginalName() throws Exception {
    upload(png(), "retrato da bruna.png", assistantJwt());

    var media = mediaFiles.findAll().get(0);
    assertThat(media.getOriginalName()).isEqualTo("retrato da bruna.png");
    assertThat(media.getObjectKey()).doesNotContain("retrato");
    assertThat(media.getObjectKey()).startsWith("assistant/" + assistantToken + "/photo/");
  }

  @Test
  void replacingThePhotoReleasesThePreviousOne() throws Exception {
    upload(png(), "primeira.png", assistantJwt());
    var first = mediaFiles.findAll().get(0);
    String firstKey = first.getObjectKey();
    assertThat(storage.exists(firstKey)).isTrue();

    upload(png(), "segunda.png", assistantJwt());

    assertThat(mediaFiles.findAll()).hasSize(1);
    assertThat(mediaFiles.findByToken(first.getToken())).isEmpty();
    assertThat(storage.exists(firstKey)).isFalse();
  }

  @Test
  void refusesAFileLyingAboutItsType() throws Exception {
    byte[] notAnImage = "MZ nao e imagem".getBytes(StandardCharsets.UTF_8);

    mockMvc
        .perform(
            multipart("/api/assistants/" + assistantToken + "/photo")
                .file(new MockMultipartFile("file", "foto.png", "image/png", notAnImage))
                .with(assistantJwt()))
        .andExpect(status().isBadRequest());

    assertThat(mediaFiles.findAll()).isEmpty();
  }

  @Test
  void theAssistantDownloadsTheirOwnPhoto() throws Exception {
    upload(png(), "foto.png", assistantJwt());

    MvcResult result =
        mockMvc
            .perform(get("/api/assistants/" + assistantToken + "/photo").with(assistantJwt()))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(png());
  }

  @Test
  void anUnrelatedUserIsRefusedWithoutReceivingAnyContent() throws Exception {
    upload(png(), "foto.png", assistantJwt());

    MvcResult result =
        mockMvc
            .perform(get("/api/assistants/" + assistantToken + "/photo").with(strangerJwt()))
            .andExpect(status().isForbidden())
            .andReturn();

    assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
  }

  @Test
  void downloadingAPhotoThatDoesNotExistIsNotFound() throws Exception {
    mockMvc
        .perform(get("/api/assistants/" + assistantToken + "/photo").with(assistantJwt()))
        .andExpect(status().isNotFound());
  }

  private UserModel createUser(UserType type, String email, String document) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName("Pessoa");
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }

  private DriverModel createDriver(UserModel user) {
    DriverModel driver = new DriverModel();
    driver.setUser(user);
    driver.setBasePrice(new BigDecimal("100.00"));
    return drivers.save(driver);
  }

  private AssistantModel createAssistant(String email, String document) {
    AssistantModel assistant = new AssistantModel();
    assistant.setUser(createUser(UserType.ASSISTANT, email, document));
    return assistants.save(assistant);
  }

  private JwtRequestPostProcessor assistantJwt() {
    return jwt()
        .jwt(token -> token.claim("uid", assistantUid).claim("roles", List.of("ROLE_ASSISTANT")))
        .authorities(new SimpleGrantedAuthority("ROLE_ASSISTANT"));
  }

  private JwtRequestPostProcessor driverJwt() {
    return jwt()
        .jwt(token -> token.claim("uid", driverUid).claim("roles", List.of("ROLE_DRIVER")))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }

  private JwtRequestPostProcessor driverWithListJwt() {
    return jwt()
        .jwt(
            token ->
                token
                    .claim("uid", driverUid)
                    .claim("roles", List.of("ROLE_DRIVER"))
                    .subject(driverEmail))
        .authorities(
            new SimpleGrantedAuthority("ROLE_DRIVER"),
            new SimpleGrantedAuthority("list_assistants"));
  }

  private JwtRequestPostProcessor strangerJwt() {
    return jwt()
        .jwt(token -> token.claim("uid", strangerUid).claim("roles", List.of("ROLE_DRIVER")))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }
}
