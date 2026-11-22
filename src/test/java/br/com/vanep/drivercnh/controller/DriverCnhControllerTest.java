package br.com.vanep.drivercnh.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DriverCnhControllerTest {

  private static final LocalDate TODAY = SchoolTransportCredentialService.today();
  private static final LocalDate ISSUED = TODAY.minusYears(1);
  private static final LocalDate VALID_UNTIL = ISSUED.plusYears(9);
  private static final LocalDate EDITED_VALID_UNTIL = ISSUED.plusYears(8);
  private static final LocalDate RENEWAL_ISSUED = TODAY.minusMonths(1);
  private static final LocalDate RENEWAL_VALID_UNTIL = RENEWAL_ISSUED.plusYears(10);

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private DriverCnhRepository cnhs;
  @Autowired private MessageSource messages;

  private MockMvc mockMvc;
  private String driverEmail;
  private String ownerUid;
  private String cnhToken;

  private String otherDriverToken;
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
    driver2 = drivers.save(driver2);
    otherDriverToken = driver2.getToken();

    UserModel admin = new UserModel();
    admin.setType(UserType.ADMIN);
    admin.setName("Admin User");
    admin.setEmail("admin@vanep.com");
    admin.setDocument("00000000000");
    admin.setToken("admin-uid");
    admin.setVerified(true);
    admin.setTermsAcceptedAt(Instant.now());
    users.save(admin);

    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver);
    cnh.setRegistrationNumber("11111111111");
    cnh.setCategory("D");
    cnh.setIssueDate(LocalDate.of(2020, 1, 15));
    cnh.setValidUntil(LocalDate.of(2030, 1, 15));
    cnh = cnhs.save(cnh);
    cnhToken = cnh.getToken();
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
            new SimpleGrantedAuthority("list_driver_cnhs"),
            new SimpleGrantedAuthority("show_driver_cnh"),
            new SimpleGrantedAuthority("create_driver_cnh"),
            new SimpleGrantedAuthority("update_driver_cnh"),
            new SimpleGrantedAuthority("delete_driver_cnh"),
            new SimpleGrantedAuthority("restore_driver_cnh"),
            new SimpleGrantedAuthority("review_documents"));
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
            new SimpleGrantedAuthority("list_driver_cnhs"),
            new SimpleGrantedAuthority("create_driver_cnh"));
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
            new SimpleGrantedAuthority("list_driver_cnhs"),
            new SimpleGrantedAuthority("create_driver_cnh"));
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
            multipart("/api/driver-cnhs")
                .file(new MockMultipartFile("file", "cnh.png", "image/png", png())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createForbidsClient() throws Exception {
    mockMvc.perform(send(clientJwt())).andExpect(status().isForbidden());
  }

  @Test
  void createReturns201ForDriverWithTheFileAndWithoutData() throws Exception {
    mockMvc
        .perform(send(otherDriverJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").exists())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.driverToken").value(otherDriverToken))
        .andExpect(jsonPath("$.registrationNumber").doesNotExist())
        .andExpect(jsonPath("$.category").doesNotExist())
        .andExpect(jsonPath("$.validUntil").doesNotExist())
        .andExpect(jsonPath("$.fileUrl").exists())
        .andExpect(jsonPath("$.file.mimeType").value("image/png"));
  }

  @Test
  void aCnhWithoutFileIsRefused() throws Exception {
    long before = cnhs.count();

    mockMvc
        .perform(multipart("/api/driver-cnhs").with(otherDriverJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.field").value("file"));

    assertThat(cnhs.count()).isEqualTo(before);
  }

  @Test
  void thereIsNoPutOfTheCnhData() throws Exception {
    mockMvc
        .perform(
            put("/api/driver-cnhs/" + cnhToken)
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(approval("11111111111", "D", ISSUED, VALID_UNTIL)))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void theReviewerApprovesTheCnhWithItsData() throws Exception {
    uploadFile(cnhToken);

    decide(cnhToken, approval("11111111111", " ad ", ISSUED, VALID_UNTIL), adminJwt())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.registrationNumber").value("11111111111"))
        .andExpect(jsonPath("$.category").value("AD"))
        .andExpect(jsonPath("$.issueDate").value(ISSUED.toString()))
        .andExpect(jsonPath("$.validUntil").value(VALID_UNTIL.toString()))
        .andExpect(jsonPath("$.reviewedByUserToken").value("admin-uid"))
        .andExpect(jsonPath("$.version").exists());
  }

  @ParameterizedTest
  @ValueSource(strings = {"registrationNumber", "category", "issueDate", "validUntil"})
  void approvingWithoutARequiredFieldNamesIt(String field) throws Exception {
    uploadFile(cnhToken);
    Map<String, String> data = new LinkedHashMap<>();
    data.put("status", "APPROVED");
    data.put("registrationNumber", "11111111111");
    data.put("category", "D");
    data.put("issueDate", ISSUED.toString());
    data.put("validUntil", VALID_UNTIL.toString());
    data.remove(field);
    String body =
        data.entrySet().stream()
            .map(entry -> "\"%s\": \"%s\"".formatted(entry.getKey(), entry.getValue()))
            .collect(Collectors.joining(", ", "{", "}"));

    decide(cnhToken, body, adminJwt())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("document.review.field_required"))
        .andExpect(jsonPath("$.field").value(field));
    assertThat(cnhs.findByToken(cnhToken).orElseThrow().getReview().getStatus())
        .isEqualTo(DocumentStatus.PENDING);
  }

  @ParameterizedTest
  @ValueSource(strings = {"A", "B", "C", "AB", "AC", "B EAR", "DE", "ACC", "F"})
  void approvingACategoryThatDoesNotAllowSchoolTransportIsRefused(String category)
      throws Exception {
    uploadFile(cnhToken);

    decide(cnhToken, approval("11111111111", category, ISSUED, VALID_UNTIL), adminJwt())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.field").value("category"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    messages.getMessage(
                        "driver_cnh.category.invalid", null, Locale.forLanguageTag("pt-BR"))));
    assertThat(cnhs.findByToken(cnhToken).orElseThrow().getCategory()).isNull();
  }

  @Test
  void approvingARegistrationOutOfFormatIsRefused() throws Exception {
    uploadFile(cnhToken);

    decide(cnhToken, approval("abc", "D", ISSUED, VALID_UNTIL), adminJwt())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.field").value("registrationNumber"));
  }

  @Test
  void approvingACnhIssuedTomorrowIsRefused() throws Exception {
    uploadFile(cnhToken);

    decide(
            cnhToken,
            approval("11111111111", "D", TODAY.plusDays(1), TODAY.plusYears(5)),
            adminJwt())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("driver_cnh.issue_date.future"))
        .andExpect(jsonPath("$.field").value("issueDate"));
  }

  @Test
  void approvingACnhThatExpiredYesterdayIsRefused() throws Exception {
    uploadFile(cnhToken);

    decide(
            cnhToken,
            approval("11111111111", "D", ISSUED.minusYears(9), TODAY.minusDays(1)),
            adminJwt())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("document.expires_at.past"))
        .andExpect(jsonPath("$.field").value("validUntil"));
  }

  @Test
  void aCnhIsApprovedForTenYearsFromItsIssueAtMost() throws Exception {
    uploadFile(cnhToken);

    decide(
            cnhToken,
            approval("11111111111", "D", ISSUED, ISSUED.plusYears(10).plusDays(1)),
            adminJwt())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("document.expires_at.too_far"))
        .andExpect(jsonPath("$.field").value("validUntil"));
    decide(cnhToken, approval("11111111111", "D", ISSUED, ISSUED.plusYears(10)), adminJwt())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validUntil").value(ISSUED.plusYears(10).toString()));
  }

  @Test
  void approvingWithTheNumberOfAnotherDriverIsRefused() throws Exception {
    uploadFile(cnhToken);
    String other = sendAndReadToken(otherDriverJwt());

    decide(other, approval("11111111111", "D", ISSUED, VALID_UNTIL), adminJwt())
        .andExpect(status().isOk());
    decide(cnhToken, approval("11111111111", "D", ISSUED, VALID_UNTIL), adminJwt())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("driver_cnh.registration.duplicate"));
    assertThat(cnhs.findByToken(cnhToken).orElseThrow().getReview().getStatus())
        .isEqualTo(DocumentStatus.PENDING);
  }

  @Test
  void aRejectionAsNotEligibleDoesNotStoreTheDataOfItsBody() throws Exception {
    uploadFile(cnhToken);

    decide(
            cnhToken,
            """
            {"status": "REJECTED", "rejectionReasonCode": "NOT_ELIGIBLE", "registrationNumber": "11111111111",
             "category": "D", "issueDate": "%s", "validUntil": "%s"}
            """
                .formatted(ISSUED, VALID_UNTIL),
            adminJwt())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.rejectionReasonCode").value("NOT_ELIGIBLE"))
        .andExpect(jsonPath("$.registrationNumber").doesNotExist());
  }

  @Test
  void theCnhDescribesItsFile() throws Exception {
    uploadFile(cnhToken);

    mockMvc
        .perform(get("/api/driver-cnhs/" + cnhToken).with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.file.mimeType").value("image/png"))
        .andExpect(jsonPath("$.file.sizeBytes").value(png().length))
        .andExpect(jsonPath("$.file.originalName").value("cnh.png"));
  }

  @Test
  void aSecondPendingCnhIsRefused() throws Exception {
    mockMvc
        .perform(send(driverJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("document.submission.pending_exists"));
  }

  @Test
  void theOwnerCannotApproveTheirOwnCnh() throws Exception {
    uploadFile(cnhToken);

    decide(cnhToken, approval("11111111111", "D", ISSUED, VALID_UNTIL), driverJwt())
        .andExpect(status().isForbidden());
  }

  @Test
  void approvingACnhWithoutFileIsUnprocessable() throws Exception {
    decide(cnhToken, approval("11111111111", "D", ISSUED, VALID_UNTIL), adminJwt())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("document.review.file_required"));
  }

  @Test
  void aDecisionOnAnOldVersionOfTheCnhIsStale() throws Exception {
    long opened = cnhs.findByToken(cnhToken).orElseThrow().getVersion();
    uploadFile(cnhToken);

    mockMvc
        .perform(
            put("/api/driver-cnhs/" + cnhToken + "/status")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(withVersion(approval("11111111111", "D", ISSUED, VALID_UNTIL), opened)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("document.review.stale"));
  }

  @Test
  void anApprovedCnhIsFinal() throws Exception {
    approve(cnhToken);

    mockMvc
        .perform(
            multipart("/api/driver-cnhs/" + cnhToken + "/file")
                .file(new MockMultipartFile("file", "cnh.png", "image/png", png()))
                .with(driverJwt()))
        .andExpect(status().isConflict());
    mockMvc
        .perform(delete("/api/driver-cnhs/" + cnhToken).with(driverJwt()))
        .andExpect(status().isConflict());
  }

  @Test
  void theReviewerCorrectsTheDataOfTheApprovedCnh() throws Exception {
    approve(cnhToken);
    long seen = cnhs.findByToken(cnhToken).orElseThrow().getVersion();

    mockMvc
        .perform(
            put("/api/driver-cnhs/" + cnhToken + "/data")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    withVersion(
                        """
                        {"registrationNumber": "11111111112", "category": "E", "issueDate": "%s",
                         "validUntil": "%s", "issuingState": "SP"}
                        """
                            .formatted(ISSUED, EDITED_VALID_UNTIL),
                        seen)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.registrationNumber").value("11111111112"))
        .andExpect(jsonPath("$.category").value("E"))
        .andExpect(jsonPath("$.validUntil").value(EDITED_VALID_UNTIL.toString()))
        .andExpect(jsonPath("$.issuingState").value("SP"))
        .andExpect(jsonPath("$.dataCorrectedAt").exists());
    assertThat(cnhs.findByToken(cnhToken).orElseThrow().getReview().getDataCorrectedById())
        .isEqualTo(users.findByToken("admin-uid").orElseThrow().getId());
  }

  @Test
  void aPendingCnhIsNotCorrectedAndTheOwnerCannotCorrect() throws Exception {
    uploadFile(cnhToken);
    long seen = cnhs.findByToken(cnhToken).orElseThrow().getVersion();
    String correction =
        withVersion(
            """
            {"registrationNumber": "11111111111", "category": "D", "issueDate": "%s", "validUntil": "%s"}
            """
                .formatted(ISSUED, VALID_UNTIL),
            seen);

    mockMvc
        .perform(
            put("/api/driver-cnhs/" + cnhToken + "/data")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(correction))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("document.data.not_approved"));
    mockMvc
        .perform(
            put("/api/driver-cnhs/" + cnhToken + "/data")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(correction))
        .andExpect(status().isForbidden());
  }

  @Test
  void aRenewalWithTheSameNumberLivesNextToTheApprovedCnhAndReplacesItWhenApproved()
      throws Exception {
    approve(cnhToken);

    String renewal = sendAndReadToken(driverJwt());
    decide(renewal, approval("11111111111", "D", RENEWAL_ISSUED, RENEWAL_VALID_UNTIL), adminJwt())
        .andExpect(status().isOk());

    DriverCnhModel previous = cnhs.findByToken(cnhToken).orElseThrow();
    assertThat(previous.getSupersededAt()).isNotNull();
    assertThat(previous.getFile()).isNull();
    assertThat(previous.getReview().getStatus()).isEqualTo(DocumentStatus.APPROVED);
    mockMvc
        .perform(get("/api/driver-cnhs").with(driverJwt()))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].token").value(renewal));
  }

  private void approve(String token) throws Exception {
    uploadFile(token);
    decide(token, approval("11111111111", "D", ISSUED, VALID_UNTIL), adminJwt())
        .andExpect(status().isOk());
  }

  private ResultActions decide(String token, String body, JwtRequestPostProcessor who)
      throws Exception {
    long version = cnhs.findByToken(token).orElseThrow().getVersion();
    return mockMvc.perform(
        put("/api/driver-cnhs/" + token + "/status")
            .with(who)
            .contentType(MediaType.APPLICATION_JSON)
            .content(withVersion(body, version)));
  }

  private static String withVersion(String body, long version) {
    return body.substring(0, body.lastIndexOf('}')) + ", \"version\": " + version + "}";
  }

  private static String approval(
      String number, String category, LocalDate issueDate, LocalDate validUntil) {
    return """
        {"status": "APPROVED", "registrationNumber": "%s", "category": "%s", "issueDate": "%s", "validUntil": "%s"}
        """
        .formatted(number, category, issueDate, validUntil);
  }

  private MockMultipartHttpServletRequestBuilder send(JwtRequestPostProcessor who) {
    return multipart("/api/driver-cnhs")
        .file(new MockMultipartFile("file", "cnh.png", "image/png", png()))
        .with(who);
  }

  private String sendAndReadToken(JwtRequestPostProcessor who) throws Exception {
    return JsonPath.read(
        mockMvc
            .perform(send(who))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.token");
  }

  private void uploadFile(String token) throws Exception {
    mockMvc
        .perform(
            multipart("/api/driver-cnhs/" + token + "/file")
                .file(new MockMultipartFile("file", "cnh.png", "image/png", png()))
                .with(driverJwt()))
        .andExpect(status().isCreated());
  }

  private static byte[] png() {
    return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01};
  }

  @Test
  void listReturnsOnlyDriverCnhForDriver() throws Exception {
    mockMvc
        .perform(get("/api/driver-cnhs").with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].token").value(cnhToken));
  }

  @Test
  void listReturnsEmptyForOtherDriver() throws Exception {
    mockMvc
        .perform(get("/api/driver-cnhs").with(otherDriverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void listReturnsAllForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/driver-cnhs").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void getByTokenReturns200ForOwner() throws Exception {
    mockMvc
        .perform(get("/api/driver-cnhs/" + cnhToken).with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(cnhToken));
  }

  @Test
  void getByTokenReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/driver-cnhs/" + cnhToken).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(cnhToken));
  }

  @Test
  void getByTokenReturns403ForOtherDriver() throws Exception {
    mockMvc
        .perform(get("/api/driver-cnhs/" + cnhToken).with(otherDriverJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getByTokenReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(get("/api/driver-cnhs/non-existent-token").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteReturns204ForOwner() throws Exception {
    mockMvc
        .perform(delete("/api/driver-cnhs/" + cnhToken).with(driverJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/driver-cnhs/" + cnhToken).with(driverJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void restoreReturns200ForOwner() throws Exception {
    mockMvc
        .perform(delete("/api/driver-cnhs/" + cnhToken).with(driverJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(post("/api/driver-cnhs/" + cnhToken + "/restore").with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(cnhToken))
        .andExpect(jsonPath("$.active").value(true));

    mockMvc
        .perform(get("/api/driver-cnhs/" + cnhToken).with(driverJwt()))
        .andExpect(status().isOk());
  }

  @Test
  void onlyTheAdminRestoresAReviewedCnh() throws Exception {
    approve(cnhToken);
    mockMvc
        .perform(delete("/api/driver-cnhs/" + cnhToken).with(adminJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(post("/api/driver-cnhs/" + cnhToken + "/restore").with(driverJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("document.submission.reviewed"));
    mockMvc
        .perform(get("/api/driver-cnhs/" + cnhToken).with(driverJwt()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(post("/api/driver-cnhs/" + cnhToken + "/restore").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  @Test
  void restoreReturns409ForActiveCnh() throws Exception {
    mockMvc
        .perform(post("/api/driver-cnhs/" + cnhToken + "/restore").with(driverJwt()))
        .andExpect(status().isConflict());
  }
}
