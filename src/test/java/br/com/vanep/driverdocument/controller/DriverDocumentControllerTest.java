package br.com.vanep.driverdocument.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
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
  @Autowired private MessageSource messages;

  private MockMvc mockMvc;
  private String driverEmail;
  private String ownerUid;
  private String docToken;

  private String otherDriverEmail;
  private DriverModel ownerDriver;
  private DriverModel otherDriver;
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
    ownerDriver = driver;

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
    otherDriver = drivers.save(driver2);

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
    doc.setDocumentType(DriverDocumentType.CRIMINAL_RECORD);
    doc.setExpiresAt(LocalDate.of(2027, 1, 1));
    doc.getReview().setStatus(DocumentStatus.PENDING);
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
            multipart("/api/driver-documents")
                .file(pdfPart())
                .param("documentType", "CRIMINAL_RECORD"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createForbidsClient() throws Exception {
    mockMvc.perform(send("CRIMINAL_RECORD", clientJwt())).andExpect(status().isForbidden());
  }

  @Test
  void createReturns201ForDriverWithTheFileAndWithoutData() throws Exception {
    mockMvc
        .perform(send("SCHOOL_DRIVER_COURSE", driverJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").exists())
        .andExpect(jsonPath("$.documentType").value("SCHOOL_DRIVER_COURSE"))
        .andExpect(jsonPath("$.fileUrl").exists())
        .andExpect(jsonPath("$.file.originalName").value("doc.pdf"))
        .andExpect(jsonPath("$.documentNumber").doesNotExist())
        .andExpect(jsonPath("$.expiresAt").doesNotExist())
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void aDocumentWithoutFileIsRefused() throws Exception {
    mockMvc
        .perform(
            multipart("/api/driver-documents")
                .param("documentType", "CRIMINAL_RECORD")
                .with(driverJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.field").value("file"));

    assertThat(documents.count()).isEqualTo(1);
  }

  @Test
  void theOwnerCannotEditTheData() throws Exception {
    mockMvc
        .perform(
            put("/api/driver-documents/" + docToken)
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"documentType": "CRIMINAL_RECORD", "expiresAt": "2028-01-01"}
                    """))
        .andExpect(status().isMethodNotAllowed());
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
    uploadPdf();
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
        .andExpect(jsonPath("$.reviewMethod").value("MANUAL"))
        .andExpect(jsonPath("$.reviewedByUserToken").value("admin-uid"));
  }

  @Test
  void approvingWithoutFileIsUnprocessable() throws Exception {
    mockMvc
        .perform(
            put("/api/driver-documents/" + docToken + "/status")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"status": "APPROVED"}
                    """))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value(message("document.review.file_required")));

    assertThat(documents.findByToken(docToken).orElseThrow().getReview().getStatus())
        .isEqualTo(DocumentStatus.PENDING);
  }

  @Test
  void pendingIsNotADecision() throws Exception {
    uploadPdf();

    mockMvc
        .perform(
            put("/api/driver-documents/" + docToken + "/status")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"status": "PENDING"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void theOwnerCannotApproveTheirOwnDocument() throws Exception {
    uploadPdf();

    mockMvc
        .perform(
            put("/api/driver-documents/" + docToken + "/status")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"status": "APPROVED"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void aVanDocumentTypeIsRefused() throws Exception {
    mockMvc.perform(send("CRLV", driverJwt())).andExpect(status().isBadRequest());

    assertThat(documents.count()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"RESIDENCE_PROOF", "OTHER"})
  void aResidenceProofOrAFreeTypeIsRefused(String type) throws Exception {
    mockMvc.perform(send(type, driverJwt())).andExpect(status().isBadRequest());

    assertThat(documents.count()).isEqualTo(1);
  }

  @Test
  void approvingAMunicipalAuthorizationWithoutNumberIsRefused() throws Exception {
    String authorization = sendAndReadToken("MUNICIPAL_AUTHORIZATION");

    mockMvc
        .perform(approve(authorization, "{\"status\": \"APPROVED\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.field").value("documentNumber"))
        .andExpect(jsonPath("$.detail").value(message("document.review.field_required")));

    assertThat(documents.findByToken(authorization).orElseThrow().getReview().getStatus())
        .isEqualTo(DocumentStatus.PENDING);
  }

  @Test
  void theSpecializedCourseIsApprovedWithoutNumberNorExpiry() throws Exception {
    String course = sendAndReadToken("SCHOOL_DRIVER_COURSE");

    mockMvc
        .perform(approve(course, "{\"status\": \"APPROVED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.documentNumber").doesNotExist());
  }

  @Test
  void theApprovalStoresTheNumberTrimmed() throws Exception {
    String course = sendAndReadToken("SCHOOL_DRIVER_COURSE");

    mockMvc
        .perform(
            approve(
                course,
                """
                {"status": "APPROVED", "documentNumber": " CERT-2024-118 ", "expiresAt": "2029-01-31"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentNumber").value("CERT-2024-118"))
        .andExpect(jsonPath("$.expiresAt").value("2029-01-31"));
  }

  @Test
  void aNumberOfSixtyFiveCharactersIsRefused() throws Exception {
    String authorization = sendAndReadToken("MUNICIPAL_AUTHORIZATION");

    mockMvc
        .perform(
            approve(
                authorization,
                """
                {"status": "APPROVED", "documentNumber": "%s"}
                """
                    .formatted("A".repeat(65))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.field").value("documentNumber"));
  }

  @Test
  void theDriverFiltersByTypeAndStatusTogether() throws Exception {
    saveDocument(ownerDriver, DriverDocumentType.SCHOOL_DRIVER_COURSE, DocumentStatus.APPROVED);
    String rejectedCourse =
        saveDocument(ownerDriver, DriverDocumentType.SCHOOL_DRIVER_COURSE, DocumentStatus.REJECTED);
    saveDocument(ownerDriver, DriverDocumentType.CRIMINAL_RECORD, DocumentStatus.REJECTED);

    mockMvc
        .perform(
            get("/api/driver-documents")
                .param("documentType", "SCHOOL_DRIVER_COURSE")
                .param("status", "REJECTED")
                .with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].token").value(rejectedCourse));
  }

  @Test
  void theAdminFiltersEveryDriversDocuments() throws Exception {
    String otherRecord =
        saveDocument(otherDriver, DriverDocumentType.CRIMINAL_RECORD, DocumentStatus.APPROVED);
    String otherCourse =
        saveDocument(otherDriver, DriverDocumentType.SCHOOL_DRIVER_COURSE, DocumentStatus.PENDING);

    mockMvc
        .perform(
            get("/api/driver-documents").param("documentType", "CRIMINAL_RECORD").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].token", containsInAnyOrder(docToken, otherRecord)));
    mockMvc
        .perform(get("/api/driver-documents").param("status", "PENDING").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].token", containsInAnyOrder(docToken, otherCourse)));
    mockMvc
        .perform(
            get("/api/driver-documents")
                .param("documentType", "CRIMINAL_RECORD")
                .param("status", "PENDING")
                .with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].token", containsInAnyOrder(docToken)));
  }

  @Test
  void aDriverWhoseEmailChangedStillListsTheirDocuments() throws Exception {
    JwtRequestPostProcessor tokenIssuedBeforeTheChange =
        jwt()
            .jwt(t -> t.claim("uid", ownerUid).subject("old.address@vanep.com"))
            .authorities(
                new SimpleGrantedAuthority("ROLE_DRIVER"),
                new SimpleGrantedAuthority("list_driver_documents"));

    mockMvc
        .perform(get("/api/driver-documents").with(tokenIssuedBeforeTheChange))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].token").value(docToken));
  }

  @Test
  void replacingTheFileOfAnApprovedDocumentReturnsItToPendingWithoutData() throws Exception {
    uploadPdf();
    DriverDocumentModel doc = documents.findByToken(docToken).orElseThrow();
    doc.getReview().setStatus(DocumentStatus.APPROVED);
    doc.getReview().setReviewMethod(ReviewMethod.MANUAL);
    doc.getReview().setReviewedBy(users.findByToken("admin-uid").orElseThrow());
    doc.getReview().setReviewedAt(Instant.now());
    doc.setDocumentNumber("CERT-1");
    documents.save(doc);

    uploadPdf();

    mockMvc
        .perform(get("/api/driver-documents/" + docToken).with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.documentNumber").doesNotExist())
        .andExpect(jsonPath("$.reviewMethod").doesNotExist())
        .andExpect(jsonPath("$.reviewedByUserToken").doesNotExist())
        .andExpect(jsonPath("$.reviewedAt").doesNotExist());
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

  private void uploadPdf() throws Exception {
    byte[] pdf =
        "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n%%EOF\n"
            .getBytes(StandardCharsets.UTF_8);
    mockMvc
        .perform(
            multipart("/api/driver-documents/" + docToken + "/file")
                .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", pdf))
                .with(driverJwt()))
        .andExpect(status().isCreated());
  }

  private static MockMultipartFile pdfPart() {
    return new MockMultipartFile(
        "file",
        "doc.pdf",
        "application/pdf",
        "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n%%EOF\n"
            .getBytes(StandardCharsets.UTF_8));
  }

  private MockMultipartHttpServletRequestBuilder send(String type, JwtRequestPostProcessor who) {
    return multipart("/api/driver-documents").file(pdfPart()).param("documentType", type).with(who);
  }

  private String sendAndReadToken(String type) throws Exception {
    String body =
        mockMvc
            .perform(send(type, driverJwt()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.token");
  }

  private MockHttpServletRequestBuilder approve(String token, String body) {
    return put("/api/driver-documents/" + token + "/status")
        .with(adminJwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private String message(String key) {
    return messages.getMessage(key, null, Locale.forLanguageTag("pt-BR"));
  }

  private String saveDocument(DriverModel owner, DriverDocumentType type, DocumentStatus status) {
    DriverDocumentModel document = new DriverDocumentModel();
    document.setDriver(owner);
    document.setDocumentType(type);
    document.getReview().setStatus(status);
    return documents.save(document).getToken();
  }
}
