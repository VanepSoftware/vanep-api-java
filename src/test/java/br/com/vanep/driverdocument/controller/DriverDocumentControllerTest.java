package br.com.vanep.driverdocument.controller;

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
import br.com.vanep.driverdocument.enums.DocumentStatusEnum;
import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
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
class DriverDocumentControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private DriverDocumentRepository documents;

  private MockMvc mockMvc;
  private String driverEmail;
  private String ownerUid;
  private String docToken;

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
    drivers.save(driver2);

    UserModel admin = new UserModel();
    admin.setType(UserType.ADMIN);
    admin.setName("Admin User");
    admin.setEmail("admin@vanep.com");
    admin.setDocument("00000000000");
    admin.setToken("admin-uid");
    admin.setVerified(true);
    admin.setTermsAcceptedAt(Instant.now());
    users.save(admin);

    DriverDocumentModel doc = new DriverDocumentModel();
    doc.setDriver(driver);
    doc.setDocumentType(DocumentTypeEnum.CRLV);
    doc.setFileUrl("https://storage.vanep.com.br/crlv.pdf");
    doc.setExpiresAt(LocalDate.of(2027, 1, 1));
    doc.setStatus(DocumentStatusEnum.PENDING);
    doc = documents.save(doc);
    docToken = doc.getToken();
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
            new SimpleGrantedAuthority("list_driver_documents"),
            new SimpleGrantedAuthority("show_driver_document"),
            new SimpleGrantedAuthority("create_driver_document"),
            new SimpleGrantedAuthority("update_driver_document"),
            new SimpleGrantedAuthority("delete_driver_document"),
            new SimpleGrantedAuthority("restore_driver_document"));
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
            new SimpleGrantedAuthority("list_driver_documents"),
            new SimpleGrantedAuthority("create_driver_document"));
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
            new SimpleGrantedAuthority("list_driver_documents"),
            new SimpleGrantedAuthority("create_driver_document"));
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
        .perform(
            post("/api/driver-documents").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createForbidsClient() throws Exception {
    String body =
        """
        {
          "documentType": "CRLV",
          "fileUrl": "https://storage.vanep.com.br/doc.pdf"
        }
        """;
    mockMvc
        .perform(
            post("/api/driver-documents")
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
          "documentType": "RESIDENCE_PROOF",
          "fileUrl": "https://storage.vanep.com.br/residence.pdf"
        }
        """;

    mockMvc
        .perform(
            post("/api/driver-documents")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").exists())
        .andExpect(jsonPath("$.documentType").value("RESIDENCE_PROOF"))
        .andExpect(jsonPath("$.fileUrl").value("https://storage.vanep.com.br/residence.pdf"))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void getByTokenReturns200ForOwner() throws Exception {
    mockMvc
        .perform(get("/api/driver-documents/" + docToken).with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(docToken));
  }

  @Test
  void getByTokenReturns403ForOtherDriver() throws Exception {
    mockMvc
        .perform(get("/api/driver-documents/" + docToken).with(otherDriverJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateStatusReturns200ForAdmin() throws Exception {
    String body =
        """
        {
          "status": "APPROVED",
          "reviewMethod": "MANUAL"
        }
        """;

    mockMvc
        .perform(
            put("/api/driver-documents/" + docToken + "/status")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.reviewMethod").value("MANUAL"));
  }

  @Test
  void updateResetsStatusToPending() throws Exception {
    DriverDocumentModel doc = documents.findByToken(docToken).orElseThrow();
    doc.setStatus(DocumentStatusEnum.APPROVED);
    documents.save(doc);

    String body =
        """
        {
          "documentType": "CRLV",
          "fileUrl": "https://storage.vanep.com.br/new-crlv.pdf",
          "expiresAt": "2028-01-01"
        }
        """;

    mockMvc
        .perform(
            put("/api/driver-documents/" + docToken)
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(docToken))
        .andExpect(jsonPath("$.fileUrl").value("https://storage.vanep.com.br/new-crlv.pdf"))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void deleteReturns204ForOwner() throws Exception {
    mockMvc
        .perform(delete("/api/driver-documents/" + docToken).with(driverJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/driver-documents/" + docToken).with(driverJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void restoreReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(delete("/api/driver-documents/" + docToken).with(driverJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(post("/api/driver-documents/" + docToken + "/restore").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(docToken))
        .andExpect(jsonPath("$.active").value(true));
  }
}
