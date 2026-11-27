package br.com.vanep.vehicledocument.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import br.com.vanep.vehicledocument.repository.VehicleDocumentRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class VehicleDocumentControllerTest {

  private static final LocalDate TODAY = SchoolTransportCredentialService.today();
  private static final LocalDate IN_FOUR_MONTHS = TODAY.plusMonths(4);
  private static final LocalDate IN_FIVE_MONTHS = TODAY.plusMonths(5);

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private VehicleRepository vehicles;
  @Autowired private VehicleDocumentRepository documents;
  @Autowired private MessageSource messages;

  private MockMvc mockMvc;
  private String ownerUid;
  private String strangerUid;
  private String vehicleToken;
  private String documentToken;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    DriverModel owner = createDriver("dono@vanep.com", "12345678909");
    ownerUid = owner.getUser().getToken();
    strangerUid = createDriver("estranho@vanep.com", "15350946056").getUser().getToken();

    UserModel admin = new UserModel();
    admin.setType(UserType.ADMIN);
    admin.setName("Admin");
    admin.setEmail("admin@vanep.com");
    admin.setDocument("00000000000");
    admin.setToken("admin-uid");
    admin.setVerified(true);
    admin.setTermsAcceptedAt(Instant.now());
    users.save(admin);

    VehicleModel vehicle = createVehicle(owner, "ABC1D23");
    vehicleToken = vehicle.getToken();

    VehicleDocumentModel document = new VehicleDocumentModel();
    document.setVehicle(vehicle);
    document.setDocumentType(VehicleDocumentType.CRLV);
    document.setExpiresAt(LocalDate.of(2027, 3, 31));
    documentToken = documents.save(document).getToken();
  }

  @Test
  void theOwnerSendsTheInspectionOfOneVanWithItsFile() throws Exception {
    mockMvc
        .perform(send("VEHICLE_INSPECTION", ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").exists())
        .andExpect(jsonPath("$.vehicleToken").value(vehicleToken))
        .andExpect(jsonPath("$.documentType").value("VEHICLE_INSPECTION"))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.documentNumber").doesNotExist())
        .andExpect(jsonPath("$.expiresAt").doesNotExist())
        .andExpect(jsonPath("$.fileUrl").value(startsWith("/api/vehicle-documents/")))
        .andExpect(jsonPath("$.file.mimeType").value("application/pdf"))
        .andExpect(jsonPath("$.file.originalName").value("crlv.pdf"));
  }

  @Test
  void aDriverUnderReviewStillSendsAndReplacesDocuments() throws Exception {
    DriverModel owner = vehicles.findByToken(vehicleToken).orElseThrow().getDriver();
    owner.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    drivers.save(owner);

    mockMvc.perform(send("VEHICLE_INSPECTION", ownerJwt())).andExpect(status().isCreated());
    uploadPdf(ownerJwt());
  }

  @Test
  void theDataSentWithTheFileIsNotRead() throws Exception {
    mockMvc
        .perform(
            send("SCHOOL_VEHICLE_AUTHORIZATION", ownerJwt())
                .param("documentNumber", "AUT-1")
                .param("expiresAt", IN_FIVE_MONTHS.toString()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.documentNumber").doesNotExist())
        .andExpect(jsonPath("$.expiresAt").doesNotExist());
  }

  @Test
  void theAdminSendsADocumentForAnyVan() throws Exception {
    mockMvc.perform(send("VEHICLE_INSPECTION", adminJwt())).andExpect(status().isCreated());
  }

  @Test
  void aDocumentWithoutFileIsRefused() throws Exception {
    mockMvc
        .perform(
            multipart("/api/vehicles/" + vehicleToken + "/documents")
                .param("documentType", "VEHICLE_INSPECTION")
                .with(ownerJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.field").value("file"));

    assertThat(documents.count()).isEqualTo(1);
  }

  @Test
  void aPersonsDocumentTypeIsRefused() throws Exception {
    mockMvc.perform(send("CRIMINAL_RECORD", ownerJwt())).andExpect(status().isBadRequest());

    assertThat(documents.count()).isEqualTo(1);
  }

  @Test
  void theOwnerCannotEditTheData() throws Exception {
    mockMvc
        .perform(
            putJson(
                "/api/vehicle-documents/" + documentToken,
                """
                {"documentType": "CRLV", "expiresAt": "%s"}
                """
                    .formatted(IN_FOUR_MONTHS),
                ownerJwt()))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void theAdminApprovesWithTheNumberAndTheExpiry() throws Exception {
    String inspection = sendAndReadToken("VEHICLE_INSPECTION");

    mockMvc
        .perform(
            decideOn(
                inspection,
                """
                {"status": "APPROVED", "documentNumber": " VIST-1 ", "expiresAt": "%s"}
                """
                    .formatted(IN_FIVE_MONTHS),
                adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.documentNumber").value("VIST-1"))
        .andExpect(jsonPath("$.expiresAt").value(IN_FIVE_MONTHS.toString()));
  }

  @Test
  void approvingWithoutTheExpiryIsRefused() throws Exception {
    String inspection = sendAndReadToken("VEHICLE_INSPECTION");

    mockMvc
        .perform(decideOn(inspection, "{\"status\": \"APPROVED\"}", adminJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("document.review.field_required"))
        .andExpect(jsonPath("$.field").value("expiresAt"));

    assertThat(documents.findByToken(inspection).orElseThrow().getReview().getStatus())
        .isEqualTo(DocumentStatus.PENDING);
  }

  @Test
  void approvingAnAuthorizationWithoutNumberIsRefused() throws Exception {
    String authorization = sendAndReadToken("SCHOOL_VEHICLE_AUTHORIZATION");

    mockMvc
        .perform(decideOn(authorization, approvalWithNumber("  "), adminJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("document.review.field_required"))
        .andExpect(jsonPath("$.field").value("documentNumber"));

    assertThat(documents.findByToken(authorization).orElseThrow().getExpiresAt()).isNull();
  }

  @Test
  void anAuthorizationIsApprovedWithItsNumberTrimmed() throws Exception {
    String authorization = sendAndReadToken("SCHOOL_VEHICLE_AUTHORIZATION");

    mockMvc
        .perform(decideOn(authorization, approvalWithNumber("  AUT-118/2026 "), adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentNumber").value("AUT-118/2026"));
  }

  @Test
  void aNumberOfSixtyFourCharactersIsAccepted() throws Exception {
    String authorization = sendAndReadToken("SCHOOL_VEHICLE_AUTHORIZATION");

    mockMvc
        .perform(decideOn(authorization, approvalWithNumber("A".repeat(64)), adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentNumber").value("A".repeat(64)));
  }

  @Test
  void aNumberOfSixtyFiveCharactersIsRefused() throws Exception {
    String authorization = sendAndReadToken("SCHOOL_VEHICLE_AUTHORIZATION");

    mockMvc
        .perform(decideOn(authorization, approvalWithNumber("A".repeat(65)), adminJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.field").value("documentNumber"))
        .andExpect(
            jsonPath("$.detail").value(message("document.number.too_long").replace("{max}", "64")));
  }

  @Test
  void aRejectionDoesNotStoreTheDataOfItsBody() throws Exception {
    String inspection = sendAndReadToken("VEHICLE_INSPECTION");

    mockMvc
        .perform(
            decideOn(
                inspection,
                """
                {"status": "REJECTED", "rejectionReasonCode": "ILLEGIBLE", "expiresAt": "%s"}
                """
                    .formatted(IN_FIVE_MONTHS),
                adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.expiresAt").doesNotExist());
  }

  @Test
  void aDocumentThatDoesNotAllowSchoolTransportIsRejectedAsNotEligible() throws Exception {
    String inspection = sendAndReadToken("VEHICLE_INSPECTION");

    mockMvc
        .perform(
            decideOn(
                inspection,
                "{\"status\": \"REJECTED\", \"rejectionReasonCode\": \"NOT_ELIGIBLE\"}",
                adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rejectionReasonCode").value("NOT_ELIGIBLE"));
  }

  @Test
  void anotherDriverIsRefused() throws Exception {
    mockMvc
        .perform(get("/api/vehicles/" + vehicleToken + "/documents").with(strangerJwt()))
        .andExpect(status().isForbidden());
    mockMvc.perform(send("CRLV", strangerJwt())).andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/vehicle-documents/" + documentToken).with(strangerJwt()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete("/api/vehicle-documents/" + documentToken).with(strangerJwt()))
        .andExpect(status().isForbidden());

    assertThat(documents.count()).isEqualTo(1);
  }

  @Test
  void aClientIsRefused() throws Exception {
    mockMvc
        .perform(get("/api/vehicles/" + vehicleToken + "/documents").with(clientJwt()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/vehicle-documents/" + documentToken).with(clientJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void withoutTokenIsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/vehicles/" + vehicleToken + "/documents"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/vehicle-documents/" + documentToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void anUnknownVanOrDocumentIsNotFound() throws Exception {
    mockMvc
        .perform(get("/api/vehicles/unknown/documents").with(adminJwt()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/vehicle-documents/unknown").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void theRemovedVanHidesItsDocuments() throws Exception {
    vehicles.delete(vehicles.findByToken(vehicleToken).orElseThrow());

    mockMvc
        .perform(get("/api/vehicles/" + vehicleToken + "/documents").with(ownerJwt()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/vehicle-documents/" + documentToken).with(ownerJwt()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/vehicle-documents/" + documentToken).with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void listingReturnsTheHistoryNewestFirstAndFilters() throws Exception {
    VehicleModel vehicle = vehicles.findByToken(vehicleToken).orElseThrow();
    VehicleDocumentModel older = inspection(vehicle, LocalDate.of(2026, 12, 31));
    older.getReview().setStatus(DocumentStatus.APPROVED);
    documents.save(older);
    String newer = documents.save(inspection(vehicle, LocalDate.of(2027, 6, 30))).getToken();

    mockMvc
        .perform(
            get("/api/vehicles/" + vehicleToken + "/documents")
                .param("documentType", "VEHICLE_INSPECTION")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].token").value(newer))
        .andExpect(jsonPath("$.content[1].token").value(older.getToken()));

    mockMvc
        .perform(
            get("/api/vehicles/" + vehicleToken + "/documents")
                .param("status", "APPROVED")
                .with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].token").value(older.getToken()));
  }

  @Test
  void theOwnerReadsAndRemovesTheDocument() throws Exception {
    mockMvc
        .perform(get("/api/vehicle-documents/" + documentToken).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(documentToken))
        .andExpect(jsonPath("$.vehicleToken").value(vehicleToken));

    mockMvc
        .perform(delete("/api/vehicle-documents/" + documentToken).with(ownerJwt()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/vehicle-documents/" + documentToken).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void onlyTheAdminRemovesAReviewedDocumentAndRestoresIt() throws Exception {
    approveWithFile();
    mockMvc
        .perform(delete("/api/vehicle-documents/" + documentToken).with(ownerJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("document.submission.reviewed"));
    mockMvc
        .perform(delete("/api/vehicle-documents/" + documentToken).with(adminJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(post("/api/vehicle-documents/" + documentToken + "/restore").with(ownerJwt()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post("/api/vehicle-documents/" + documentToken + "/restore").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
    mockMvc
        .perform(post("/api/vehicle-documents/" + documentToken + "/restore").with(adminJwt()))
        .andExpect(status().isConflict());
    mockMvc
        .perform(post("/api/vehicle-documents/unknown/restore").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void anApprovedDocumentRemovedByMistakeComesBackNextToAPendingRenewal() throws Exception {
    approveWithFile();
    mockMvc
        .perform(delete("/api/vehicle-documents/" + documentToken).with(adminJwt()))
        .andExpect(status().isNoContent());
    String renewal = token(createCrlv(ownerJwt()).andExpect(status().isCreated()));

    mockMvc
        .perform(post("/api/vehicle-documents/" + documentToken + "/restore").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
    assertThat(documents.findByToken(renewal).orElseThrow().getReview().getStatus())
        .isEqualTo(DocumentStatus.PENDING);
  }

  @Test
  void restoringAnApprovedDocumentNextToAnotherApprovedOneIsRefused() throws Exception {
    approveWithFile();
    mockMvc
        .perform(delete("/api/vehicle-documents/" + documentToken).with(adminJwt()))
        .andExpect(status().isNoContent());
    VehicleDocumentModel renewal =
        documents
            .findByToken(token(createCrlv(ownerJwt()).andExpect(status().isCreated())))
            .orElseThrow();
    renewal.getReview().setStatus(DocumentStatus.APPROVED);
    documents.save(renewal);

    mockMvc
        .perform(post("/api/vehicle-documents/" + documentToken + "/restore").with(adminJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("document.submission.restore_conflict"));
    assertThat(documents.findByToken(documentToken)).isEmpty();
  }

  @Test
  void restoringAPendingDocumentNextToAnotherPendingOneIsRefused() throws Exception {
    mockMvc
        .perform(delete("/api/vehicle-documents/" + documentToken).with(ownerJwt()))
        .andExpect(status().isNoContent());
    createCrlv(ownerJwt()).andExpect(status().isCreated());

    mockMvc
        .perform(post("/api/vehicle-documents/" + documentToken + "/restore").with(adminJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("document.submission.pending_exists"));
    assertThat(documents.findByToken(documentToken)).isEmpty();
  }

  @Test
  void theOwnerCannotApproveTheirOwnDocument() throws Exception {
    uploadPdf(ownerJwt());

    mockMvc
        .perform(decide("{\"status\": \"APPROVED\"}", ownerJwt()))
        .andExpect(status().isForbidden());

    assertThat(reloadStatus()).isEqualTo(DocumentStatus.PENDING);
  }

  @Test
  void aCallerThatCanEditButNotReviewIsRefused() throws Exception {
    uploadPdf(ownerJwt());

    mockMvc
        .perform(decide("{\"status\": \"APPROVED\"}", editorJwt()))
        .andExpect(status().isForbidden());

    assertThat(reloadStatus()).isEqualTo(DocumentStatus.PENDING);
  }

  @Test
  void theAdminApprovesADocumentWithFile() throws Exception {
    uploadPdf(ownerJwt());
    long seen = reloadVersion();

    mockMvc
        .perform(
            decide(
                "{\"status\": \"APPROVED\", \"reviewMethod\": \"MANUAL\", \"expiresAt\": \"%s\"}"
                    .formatted(IN_FIVE_MONTHS),
                adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.reviewMethod").value("MANUAL"))
        .andExpect(jsonPath("$.reviewedByUserToken").value("admin-uid"))
        .andExpect(jsonPath("$.reviewedAt").exists())
        .andExpect(jsonPath("$.rejectionReasonCode").doesNotExist())
        .andExpect(jsonPath("$.version").value(seen + 1));
  }

  @Test
  void approvingWithoutFileIsUnprocessable() throws Exception {
    mockMvc
        .perform(decide("{\"status\": \"APPROVED\"}", adminJwt()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("document.review.file_required"))
        .andExpect(jsonPath("$.detail").value(message("document.review.file_required")));

    assertThat(reloadStatus()).isEqualTo(DocumentStatus.PENDING);
  }

  @Test
  void rejectingWithoutACodeIsRefused() throws Exception {
    mockMvc
        .perform(
            decide("{\"status\": \"REJECTED\", \"rejectionReason\": \"Ilegível\"}", adminJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("document.review.reason_code_required"));

    assertThat(reloadStatus()).isEqualTo(DocumentStatus.PENDING);
  }

  @Test
  void rejectingForAnotherReasonWithoutAMessageIsRefused() throws Exception {
    mockMvc
        .perform(
            decide(
                "{\"status\": \"REJECTED\", \"rejectionReasonCode\": \"OTHER\", \"rejectionReason\": \" \"}",
                adminJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("document.review.reason_required"))
        .andExpect(jsonPath("$.detail").value(message("document.review.reason_required")));
  }

  @Test
  void rejectingAnUnreadableFileNeedsNoMessage() throws Exception {
    mockMvc
        .perform(
            decide(
                "{\"status\": \"REJECTED\", \"rejectionReasonCode\": \"ILLEGIBLE\"}", adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.rejectionReasonCode").value("ILLEGIBLE"))
        .andExpect(jsonPath("$.rejectionReason").doesNotExist());
  }

  @Test
  void aDecisionOnAnOldVersionIsStaleAndChangesNothing() throws Exception {
    long opened = reloadVersion();
    uploadPdf(ownerJwt());

    mockMvc
        .perform(
            putJson(
                "/api/vehicle-documents/" + documentToken + "/status",
                "{\"status\": \"APPROVED\", \"version\": %d}".formatted(opened),
                adminJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("document.review.stale"))
        .andExpect(jsonPath("$.detail").value(message("document.review.stale")));

    assertThat(reloadStatus()).isEqualTo(DocumentStatus.PENDING);
  }

  @Test
  void aDecisionWithoutVersionIsRefused() throws Exception {
    mockMvc
        .perform(
            putJson(
                "/api/vehicle-documents/" + documentToken + "/status",
                "{\"status\": \"APPROVED\"}",
                adminJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.field").value("version"));
  }

  @Test
  void aDecidedDocumentCannotBeDecidedAgain() throws Exception {
    approveWithFile();

    mockMvc
        .perform(
            decide("{\"status\": \"REJECTED\", \"rejectionReasonCode\": \"EXPIRED\"}", adminJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("document.submission.reviewed"));
  }

  @Test
  void approvingAnInspectionThatExpiredYesterdayIsRefused() throws Exception {
    String inspection = sendAndReadToken("VEHICLE_INSPECTION");

    mockMvc
        .perform(decideOn(inspection, approvalExpiringOn(TODAY.minusDays(1)), adminJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("document.expires_at.past"))
        .andExpect(jsonPath("$.field").value("expiresAt"));

    VehicleDocumentModel stored = documents.findByToken(inspection).orElseThrow();
    assertThat(stored.getReview().getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(stored.getExpiresAt()).isNull();
  }

  @Test
  void anInspectionIsApprovedUntilTheLastDayOfItsLimit() throws Exception {
    LocalDate latest = VehicleDocumentType.VEHICLE_INSPECTION.latestExpiryOn(TODAY);
    String inspection = sendAndReadToken("VEHICLE_INSPECTION");

    mockMvc
        .perform(decideOn(inspection, approvalExpiringOn(latest.plusDays(1)), adminJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("document.expires_at.too_far"))
        .andExpect(jsonPath("$.field").value("expiresAt"));
    mockMvc
        .perform(decideOn(inspection, approvalExpiringOn(latest), adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expiresAt").value(latest.toString()));
  }

  @Test
  void anApprovedDocumentCannotGetAnotherFile() throws Exception {
    approveWithFile();
    long version = reloadVersion();

    mockMvc
        .perform(
            multipart(filePath())
                .file(new MockMultipartFile("file", "crlv.pdf", "application/pdf", pdf()))
                .with(ownerJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("document.submission.reviewed"));

    VehicleDocumentModel stored = documents.findByToken(documentToken).orElseThrow();
    assertThat(stored.getReview().getStatus()).isEqualTo(DocumentStatus.APPROVED);
    assertThat(stored.getExpiresAt()).isEqualTo(IN_FIVE_MONTHS);
    assertThat(stored.getVersion()).isEqualTo(version);
  }

  @Test
  void aPendingDocumentIsFixedInPlaceAndStaysPending() throws Exception {
    uploadPdf(ownerJwt());
    String firstFile = documents.findByToken(documentToken).orElseThrow().getFile().getToken();

    uploadPdf(ownerJwt());

    VehicleDocumentModel stored = documents.findByToken(documentToken).orElseThrow();
    assertThat(stored.getReview().getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(stored.getReview().getReviewedBy()).isNull();
    assertThat(stored.getFile().getToken()).isNotEqualTo(firstFile);
  }

  @Test
  void aSecondPendingDocumentOfTheSameTypeIsRefused() throws Exception {
    createCrlv(ownerJwt())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("document.submission.pending_exists"));
  }

  @Test
  void resendingAfterARejectionSupersedesTheRejectedOne() throws Exception {
    uploadPdf(ownerJwt());
    mockMvc
        .perform(
            decide(
                "{\"status\": \"REJECTED\", \"rejectionReasonCode\": \"ILLEGIBLE\"}", adminJwt()))
        .andExpect(status().isOk());

    String resent = token(createCrlv(ownerJwt()).andExpect(status().isCreated()));

    VehicleDocumentModel rejected = documents.findByToken(documentToken).orElseThrow();
    assertThat(rejected.getSupersededAt()).isNotNull();
    assertThat(rejected.getFile()).isNull();
    assertThat(rejected.getReview().getStatus()).isEqualTo(DocumentStatus.REJECTED);
    assertThat(rejected.getReview().getRejectionReasonCode())
        .isEqualTo(DocumentRejectionReason.ILLEGIBLE);
    assertThat(rejected.getReview().getReviewedBy()).isNotNull();
    mockMvc
        .perform(get("/api/vehicles/" + vehicleToken + "/documents").with(ownerJwt()))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].token").value(resent));
  }

  @Test
  void approvingTheRenewalSupersedesThePreviousApproval() throws Exception {
    approveWithFile();
    String renewal = token(createCrlv(ownerJwt()).andExpect(status().isCreated()));
    String previousFile = filePath();
    mockMvc
        .perform(
            multipart("/api/vehicle-documents/" + renewal + "/file")
                .file(new MockMultipartFile("file", "crlv.pdf", "application/pdf", pdf()))
                .with(ownerJwt()))
        .andExpect(status().isCreated());
    long seen = documents.findByToken(renewal).orElseThrow().getVersion();

    mockMvc
        .perform(
            putJson(
                "/api/vehicle-documents/" + renewal + "/status",
                "{\"status\": \"APPROVED\", \"expiresAt\": \"%s\", \"version\": %d}"
                    .formatted(IN_FIVE_MONTHS, seen),
                adminJwt()))
        .andExpect(status().isOk());

    VehicleDocumentModel previous = documents.findByToken(documentToken).orElseThrow();
    assertThat(previous.getSupersededAt()).isNotNull();
    assertThat(previous.getFile()).isNull();
    assertThat(previous.getReview().getStatus()).isEqualTo(DocumentStatus.APPROVED);
    mockMvc.perform(get(previousFile).with(ownerJwt())).andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/vehicles/" + vehicleToken + "/documents").with(ownerJwt()))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].token").value(renewal));
  }

  @Test
  void theReviewerCorrectsTheDataOfAnApprovedDocument() throws Exception {
    approveWithFile();
    long seen = reloadVersion();

    mockMvc
        .perform(
            putJson(
                dataPath(documentToken),
                """
                {"documentNumber": " RENAVAM-9 ", "expiresAt": "%s", "version": %d}
                """
                    .formatted(IN_FOUR_MONTHS, seen),
                adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.documentNumber").value("RENAVAM-9"))
        .andExpect(jsonPath("$.expiresAt").value(IN_FOUR_MONTHS.toString()))
        .andExpect(jsonPath("$.reviewedByUserToken").value("admin-uid"))
        .andExpect(jsonPath("$.dataCorrectedAt").exists())
        .andExpect(jsonPath("$.version").value(seen + 1));

    VehicleDocumentModel stored = documents.findByToken(documentToken).orElseThrow();
    assertThat(stored.getReview().getDataCorrectedById())
        .isEqualTo(users.findByToken("admin-uid").orElseThrow().getId());
    assertThat(stored.getFile()).isNotNull();
  }

  @Test
  void anExpiryBeforeTodayCanBeCorrectedButNotBeyondTheLimit() throws Exception {
    approveWithFile();

    mockMvc
        .perform(putJson(dataPath(documentToken), correction(TODAY.minusDays(3)), adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expiresAt").value(TODAY.minusDays(3).toString()));
    mockMvc
        .perform(
            putJson(
                dataPath(documentToken),
                correction(VehicleDocumentType.CRLV.latestExpiryOn(TODAY).plusDays(1)),
                adminJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("document.expires_at.too_far"));
  }

  @Test
  void correctingWithoutTheRequiredExpiryIsRefused() throws Exception {
    approveWithFile();

    mockMvc
        .perform(
            putJson(
                dataPath(documentToken),
                "{\"version\": %d}".formatted(reloadVersion()),
                adminJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("document.review.field_required"))
        .andExpect(jsonPath("$.field").value("expiresAt"));
  }

  @Test
  void onlyAnApprovedDocumentIsCorrected() throws Exception {
    mockMvc
        .perform(putJson(dataPath(documentToken), correction(IN_FOUR_MONTHS), adminJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("document.data.not_approved"));
  }

  @Test
  void aCorrectionOnAnOldVersionIsStale() throws Exception {
    approveWithFile();
    long old = reloadVersion() - 1;

    mockMvc
        .perform(
            putJson(
                dataPath(documentToken),
                """
                {"expiresAt": "%s", "version": %d}
                """
                    .formatted(IN_FOUR_MONTHS, old),
                adminJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("document.review.stale"));
  }

  @Test
  void theOwnerAndAnEditorCannotCorrect() throws Exception {
    approveWithFile();

    mockMvc
        .perform(putJson(dataPath(documentToken), correction(IN_FOUR_MONTHS), ownerJwt()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(putJson(dataPath(documentToken), correction(IN_FOUR_MONTHS), editorJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void theFileIsCachedOnlyForTheVersionInItsUrl() throws Exception {
    uploadPdf(ownerJwt());
    String fileUrl = documents.findByToken(documentToken).orElseThrow().getFile().getToken();

    mockMvc
        .perform(get(filePath()).param("v", fileUrl).with(ownerJwt()))
        .andExpect(header().string("Cache-Control", "max-age=86400, private, immutable"));
    mockMvc
        .perform(get(filePath()).with(ownerJwt()))
        .andExpect(header().string("Cache-Control", "no-cache, private"));
    mockMvc
        .perform(get(filePath()).param("v", "old").with(ownerJwt()))
        .andExpect(header().string("Cache-Control", "no-cache, private"));
  }

  @Test
  void theOwnerUploadsTheCrlvAsPdfAndReadsItBack() throws Exception {
    uploadPdf(ownerJwt());

    mockMvc
        .perform(get("/api/vehicle-documents/" + documentToken).with(ownerJwt()))
        .andExpect(jsonPath("$.fileUrl").value(startsWith(filePath() + "?v=")))
        .andExpect(jsonPath("$.file.mimeType").value("application/pdf"))
        .andExpect(jsonPath("$.file.sizeBytes").value(pdf().length))
        .andExpect(jsonPath("$.file.originalName").value("crlv.pdf"));
    mockMvc
        .perform(get(filePath()).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", startsWith("application/pdf")))
        .andExpect(content().bytes(pdf()));
    mockMvc.perform(get(filePath()).with(adminJwt())).andExpect(status().isOk());
  }

  @Test
  void aThirdPartyCannotReadOrReplaceTheFile() throws Exception {
    uploadPdf(ownerJwt());

    mockMvc
        .perform(get(filePath()).with(strangerJwt()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("auth.forbidden"));
    mockMvc
        .perform(
            multipart(filePath())
                .file(new MockMultipartFile("file", "x.pdf", "application/pdf", pdf()))
                .with(strangerJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void aDocumentWithoutFileHasNothingToDownload() throws Exception {
    mockMvc.perform(get(filePath()).with(ownerJwt())).andExpect(status().isNotFound());
  }

  private void approveWithFile() throws Exception {
    uploadPdf(ownerJwt());
    VehicleDocumentModel stored = documents.findByToken(documentToken).orElseThrow();
    stored.getReview().setStatus(DocumentStatus.APPROVED);
    stored.getReview().setReviewMethod(ReviewMethod.MANUAL);
    stored.getReview().setReviewedBy(users.findByToken("admin-uid").orElseThrow());
    stored.getReview().setReviewedAt(Instant.now());
    stored.setExpiresAt(IN_FIVE_MONTHS);
    documents.save(stored);
  }

  private void uploadPdf(JwtRequestPostProcessor who) throws Exception {
    mockMvc
        .perform(
            multipart(filePath())
                .file(new MockMultipartFile("file", "crlv.pdf", "application/pdf", pdf()))
                .with(who))
        .andExpect(status().isCreated());
  }

  private RequestBuilder decide(String body, JwtRequestPostProcessor who) {
    String withVersion =
        body.substring(0, body.lastIndexOf('}')) + ", \"version\": " + reloadVersion() + "}";
    return putJson("/api/vehicle-documents/" + documentToken + "/status", withVersion, who);
  }

  private ResultActions createCrlv(JwtRequestPostProcessor who) throws Exception {
    return mockMvc.perform(send("CRLV", who));
  }

  private MockMultipartHttpServletRequestBuilder send(String type, JwtRequestPostProcessor who) {
    return multipart("/api/vehicles/" + vehicleToken + "/documents")
        .file(new MockMultipartFile("file", "crlv.pdf", "application/pdf", pdf()))
        .param("documentType", type)
        .with(who);
  }

  private String sendAndReadToken(String type) throws Exception {
    return token(mockMvc.perform(send(type, ownerJwt())).andExpect(status().isCreated()));
  }

  private RequestBuilder decideOn(String token, String body, JwtRequestPostProcessor who) {
    long version = documents.findByToken(token).orElseThrow().getVersion();
    String withVersion =
        body.substring(0, body.lastIndexOf('}')) + ", \"version\": " + version + "}";
    return putJson("/api/vehicle-documents/" + token + "/status", withVersion, who);
  }

  private static String approvalWithNumber(String number) {
    return """
        {"status": "APPROVED", "documentNumber": "%s", "expiresAt": "%s"}
        """
        .formatted(number, IN_FIVE_MONTHS);
  }

  private static String approvalExpiringOn(LocalDate expiresAt) {
    return """
        {"status": "APPROVED", "expiresAt": "%s"}
        """
        .formatted(expiresAt);
  }

  private String correction(LocalDate expiresAt) {
    return """
        {"expiresAt": "%s", "version": %d}
        """
        .formatted(expiresAt, reloadVersion());
  }

  private static String dataPath(String token) {
    return "/api/vehicle-documents/" + token + "/data";
  }

  private static String token(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.token");
  }

  private long reloadVersion() {
    return documents.findByToken(documentToken).orElseThrow().getVersion();
  }

  private DocumentStatus reloadStatus() {
    return documents.findByToken(documentToken).orElseThrow().getReview().getStatus();
  }

  private String filePath() {
    return "/api/vehicle-documents/" + documentToken + "/file";
  }

  private static byte[] pdf() {
    return "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n%%EOF\n"
        .getBytes(StandardCharsets.UTF_8);
  }

  private static VehicleDocumentModel inspection(VehicleModel vehicle, LocalDate expiresAt) {
    VehicleDocumentModel document = new VehicleDocumentModel();
    document.setVehicle(vehicle);
    document.setDocumentType(VehicleDocumentType.VEHICLE_INSPECTION);
    document.setExpiresAt(expiresAt);
    return document;
  }

  private RequestBuilder putJson(String path, String body, JwtRequestPostProcessor who) {
    return put(path).with(who).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private String message(String key) {
    return messages.getMessage(key, null, Locale.forLanguageTag("pt-BR"));
  }

  private VehicleModel createVehicle(DriverModel driver, String plate) {
    VehicleModel vehicle = new VehicleModel();
    vehicle.setDriver(driver);
    vehicle.setPlate(plate);
    vehicle.setBrand("Ford");
    vehicle.setModel("Transit");
    vehicle.setManufactureYear(2022);
    vehicle.setColor("Branca");
    vehicle.setCapacity(15);
    return vehicles.save(vehicle);
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
        .jwt(token -> token.claim("uid", "admin-uid").subject("admin@vanep.com"))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("list_vehicle_documents"),
            new SimpleGrantedAuthority("show_vehicle_document"),
            new SimpleGrantedAuthority("update_vehicle_document"),
            new SimpleGrantedAuthority("delete_vehicle_document"),
            new SimpleGrantedAuthority("restore_vehicle_document"),
            new SimpleGrantedAuthority("review_documents"));
  }

  private JwtRequestPostProcessor editorJwt() {
    return jwt()
        .jwt(token -> token.claim("uid", "admin-uid"))
        .authorities(
            new SimpleGrantedAuthority("show_vehicle_document"),
            new SimpleGrantedAuthority("update_vehicle_document"));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(token -> token.claim("uid", ownerUid).claim("roles", List.of("ROLE_DRIVER")))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }

  private JwtRequestPostProcessor strangerJwt() {
    return jwt()
        .jwt(token -> token.claim("uid", strangerUid).claim("roles", List.of("ROLE_DRIVER")))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }

  private JwtRequestPostProcessor clientJwt() {
    return jwt()
        .jwt(token -> token.claim("uid", "client-uid"))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }
}
