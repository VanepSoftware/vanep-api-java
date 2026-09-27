package br.com.vanep.driverdocument.controller;

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
import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.media.enums.MediaVisibility;
import br.com.vanep.media.repository.MediaFileRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DriverDocumentFileControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private DriverDocumentRepository documents;
  @Autowired private MediaFileRepository mediaFiles;

  private MockMvc mockMvc;
  private String documentToken;
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

    DriverDocumentModel document = new DriverDocumentModel();
    document.setDriver(driver);
    document.setDocumentType(DocumentTypeEnum.CRLV);
    documentToken = documents.save(document).getToken();
  }

  private static byte[] pdf() {
    return "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n%%EOF\n"
        .getBytes(StandardCharsets.UTF_8);
  }

  private String path() {
    return "/api/driver-documents/" + documentToken + "/file";
  }

  private void upload(byte[] content, String name, String type, JwtRequestPostProcessor who)
      throws Exception {
    mockMvc
        .perform(
            multipart(path()).file(new MockMultipartFile("file", name, type, content)).with(who))
        .andExpect(status().isCreated());
  }

  @Test
  void aDocumentIsCreatedWithoutAFile() throws Exception {
    String body =
        """
        {
          "documentType": "CRLV"
        }
        """;

    mockMvc
        .perform(
            post("/api/driver-documents")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").exists())
        .andExpect(jsonPath("$.fileUrl").doesNotExist())
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc
        .perform(
            multipart(path())
                .file(new MockMultipartFile("file", "crlv.pdf", "application/pdf", pdf())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void theDocumentAcceptsAPdfAndItIsBornPrivate() throws Exception {
    upload(pdf(), "crlv.pdf", "application/pdf", ownerJwt());

    var media = mediaFiles.findAll().get(0);
    assertThat(media.getMimeType()).isEqualTo("application/pdf");
    assertThat(media.getVisibility()).isEqualTo(MediaVisibility.PRIVATE);
    assertThat(media.getObjectKey()).endsWith(".pdf");
    assertThat(media.getObjectKey()).startsWith("driver-document/" + documentToken + "/file/");
  }

  @Test
  void theDocumentAlsoAcceptsAnImage() throws Exception {
    byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01};

    upload(png, "foto-do-documento.png", "image/png", ownerJwt());

    assertThat(mediaFiles.findAll()).hasSize(1);
  }

  @Test
  void uploadingTheFileFillsTheUrlOnTheDocument() throws Exception {
    upload(pdf(), "crlv.pdf", "application/pdf", ownerJwt());

    mockMvc
        .perform(get("/api/driver-documents/" + documentToken).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fileUrl").value(path()));
  }

  @Test
  void theOwnerReadsTheirOwnDocument() throws Exception {
    upload(pdf(), "crlv.pdf", "application/pdf", ownerJwt());

    MvcResult result =
        mockMvc.perform(get(path()).with(ownerJwt())).andExpect(status().isOk()).andReturn();

    assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(pdf());
  }

  @Test
  void anUnrelatedDriverIsRefusedWithoutReceivingAnyContent() throws Exception {
    upload(pdf(), "crlv.pdf", "application/pdf", ownerJwt());

    MvcResult result =
        mockMvc
            .perform(get(path()).with(strangerJwt()))
            .andExpect(status().isForbidden())
            .andReturn();

    assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
  }

  @Test
  void theAdminReadsTheDocument() throws Exception {
    upload(pdf(), "crlv.pdf", "application/pdf", ownerJwt());

    mockMvc.perform(get(path()).with(adminJwt())).andExpect(status().isOk());
  }

  @Test
  void refusesAFileThatIsNeitherImageNorPdf() throws Exception {
    byte[] executable = new byte[] {0x4D, 0x5A, (byte) 0x90, 0x00, 0x03};

    mockMvc
        .perform(
            multipart(path())
                .file(new MockMultipartFile("file", "crlv.pdf", "application/pdf", executable))
                .with(ownerJwt()))
        .andExpect(status().isBadRequest());

    assertThat(mediaFiles.findAll()).isEmpty();
  }

  @Test
  void aDocumentWithoutFileIsALegitimateStateAndDownloadingIsNotFound() throws Exception {
    mockMvc
        .perform(get("/api/driver-documents/" + documentToken).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fileUrl").doesNotExist());

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
            new SimpleGrantedAuthority("show_driver_document"),
            new SimpleGrantedAuthority("update_driver_document"));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            token ->
                token
                    .claim("uid", ownerUid)
                    .claim("roles", List.of("ROLE_DRIVER"))
                    .subject(ownerEmail))
        .authorities(
            new SimpleGrantedAuthority("ROLE_DRIVER"),
            new SimpleGrantedAuthority("create_driver_document"));
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
