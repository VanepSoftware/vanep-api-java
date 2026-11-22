package br.com.vanep.drivercompliance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.repository.MediaFileRepository;
import br.com.vanep.schooltransportcredential.SchoolTransportCredentialFixtures;
import br.com.vanep.shared.SqlStatementCounter;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import br.com.vanep.vehicledocument.repository.VehicleDocumentRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DriverCompliancePageControllerTest {

  @Autowired private WebApplicationContext webContext;
  @Autowired private ApplicationContext context;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private VehicleRepository vehicles;
  @Autowired private DriverCnhRepository cnhs;
  @Autowired private DriverDocumentRepository driverDocuments;
  @Autowired private VehicleDocumentRepository vehicleDocuments;
  @Autowired private MediaFileRepository mediaFiles;

  private MockMvc mockMvc;
  private SchoolTransportCredentialFixtures fixtures;
  private UserModel admin;
  private DriverModel driver;
  private VehicleModel vanA;
  private VehicleModel vanB;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
    fixtures = new SchoolTransportCredentialFixtures(context);
    admin = new UserModel();
    admin.setType(UserType.ADMIN);
    admin.setName("Ana Admin");
    admin.setEmail("admin@vanep.com");
    admin.setDocument("00000000000");
    admin.setToken("admin-uid");
    admin.setVerified(true);
    admin.setTermsAcceptedAt(Instant.now());
    admin = users.save(admin);

    driver = fixtures.createDriver("carlos@vanep.com", "12345678909");
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    driver.setSubmittedAt(Instant.parse("2026-11-01T10:00:00Z"));
    driver = drivers.save(driver);
    vanA = fixtures.createVehicle(driver, "AAA1A11");
    vanB = fixtures.createVehicle(driver, "BBB2B22");
  }

  @Test
  void thePageShowsTheRequirementsTheRenewalAndTheLastDecision() throws Exception {
    DriverCnhModel cnh = cnh(DocumentStatus.APPROVED, Instant.parse("2026-11-02T10:00:00Z"));
    driverDocument(DriverDocumentType.SCHOOL_DRIVER_COURSE, DocumentStatus.APPROVED, null);
    DriverDocumentModel rejected =
        driverDocument(
            DriverDocumentType.CRIMINAL_RECORD,
            DocumentStatus.REJECTED,
            Instant.parse("2026-11-03T10:00:00Z"));
    rejected.getReview().setRejectionReasonCode(DocumentRejectionReason.ILLEGIBLE);
    rejected.setSupersededAt(Instant.parse("2026-11-04T10:00:00Z"));
    rejected.setFile(null);
    driverDocuments.save(rejected);
    DriverDocumentModel resent =
        driverDocument(DriverDocumentType.CRIMINAL_RECORD, DocumentStatus.PENDING, null);
    vanDocument(vanA, VehicleDocumentType.CRLV, DocumentStatus.APPROVED);
    vanDocument(vanA, VehicleDocumentType.VEHICLE_INSPECTION, DocumentStatus.APPROVED);
    VehicleDocumentModel renewal =
        vanDocument(vanA, VehicleDocumentType.VEHICLE_INSPECTION, DocumentStatus.PENDING);
    vanDocument(vanA, VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION, DocumentStatus.APPROVED);

    page(driver.getToken())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asOf").exists())
        .andExpect(jsonPath("$.driver.document").value("12345678909"))
        .andExpect(jsonPath("$.driver.approvalStatus").value("UNDER_REVIEW"))
        .andExpect(jsonPath("$.decision.canApprove").value(false))
        .andExpect(jsonPath("$.decision.pendingReviewCount").value(2))
        .andExpect(jsonPath("$.driverRequirements[2].requirement").value("CRIMINAL_RECORD"))
        .andExpect(jsonPath("$.driverRequirements[2].state").value("PENDING"))
        .andExpect(jsonPath("$.driverRequirements[2].current.token").value(resent.getToken()))
        .andExpect(jsonPath("$.driverRequirements[2].lastDecision.status").value("REJECTED"))
        .andExpect(
            jsonPath("$.driverRequirements[2].lastDecision.rejectionReasonCode").value("ILLEGIBLE"))
        .andExpect(jsonPath("$.driverRequirements[2].lastDecision.fileUrl").doesNotExist())
        .andExpect(
            jsonPath("$.driverRequirements[0].current.expiresAt")
                .value(cnh.getValidUntil().toString()))
        .andExpect(
            jsonPath("$.driverRequirements[0].current.fileUrl")
                .value(startsWith("/api/driver-cnhs/" + cnh.getToken() + "/file?v=")))
        .andExpect(
            jsonPath("$.driverRequirements[0].current.registrationNumber").value("01234567890"))
        .andExpect(jsonPath("$.driverRequirements[0].current.documentNumber").doesNotExist())
        .andExpect(jsonPath("$.driverRequirements[0].current.category").value("D"))
        .andExpect(jsonPath("$.driverRequirements[0].current.reviewedBy.name").value("Ana Admin"))
        .andExpect(
            jsonPath("$.driverRequirements[0].current.file.mimeType").value("application/pdf"))
        .andExpect(jsonPath("$.driverRequirements[0].current.file.sizeBytes").value(10))
        .andExpect(jsonPath("$.vehicles.length()").value(2))
        .andExpect(jsonPath("$.vehicles[0].requirements[1].state").value("APPROVED"))
        .andExpect(
            jsonPath("$.vehicles[0].requirements[1].pending.token").value(renewal.getToken()))
        .andExpect(jsonPath("$.vehicles[0].qualified").value(true));
  }

  @Test
  void theReviewQueueFollowsTheChecklist() throws Exception {
    DriverCnhModel cnh = cnh(DocumentStatus.PENDING, null);
    VehicleDocumentModel vanBCrlv =
        vanDocument(vanB, VehicleDocumentType.CRLV, DocumentStatus.PENDING);
    VehicleDocumentModel vanAInspection =
        vanDocument(vanA, VehicleDocumentType.VEHICLE_INSPECTION, DocumentStatus.PENDING);

    page(driver.getToken())
        .andExpect(jsonPath("$.reviewQueue.length()").value(3))
        .andExpect(jsonPath("$.reviewQueue[0].token").value(cnh.getToken()))
        .andExpect(jsonPath("$.reviewQueue[0].kind").value("DRIVER_CNH"))
        .andExpect(jsonPath("$.reviewQueue[1].token").value(vanAInspection.getToken()))
        .andExpect(jsonPath("$.reviewQueue[1].vehicleToken").value(vanA.getToken()))
        .andExpect(jsonPath("$.reviewQueue[2].token").value(vanBCrlv.getToken()));
  }

  @Test
  void theBlockersNameOnlyTheVanClosestToCompleteAndInactiveVansAreLeftOut() throws Exception {
    vanDocument(vanA, VehicleDocumentType.CRLV, DocumentStatus.APPROVED);
    vanDocument(vanA, VehicleDocumentType.VEHICLE_INSPECTION, DocumentStatus.APPROVED);
    VehicleModel inactive = fixtures.createVehicle(driver, "CCC3C33");
    inactive.setActive(false);
    vehicles.save(inactive);

    page(driver.getToken())
        .andExpect(jsonPath("$.vehicles.length()").value(2))
        .andExpect(
            jsonPath("$.decision.blockers[?(@.kind == 'VEHICLE_REQUIREMENT')].plate")
                .value(everyItem(is("AAA1A11"))))
        .andExpect(
            jsonPath("$.decision.blockers[?(@.kind == 'VEHICLE_REQUIREMENT')].requirement")
                .value(contains("SCHOOL_VEHICLE_AUTHORIZATION")));
  }

  @Test
  void theHistoryHasTheSendingsTheDecisionsAndTheOnboardingNewestFirst() throws Exception {
    DriverDocumentModel rejected =
        driverDocument(
            DriverDocumentType.CRIMINAL_RECORD,
            DocumentStatus.REJECTED,
            Instant.parse("2026-11-03T10:00:00Z"));
    rejected.getReview().setRejectionReasonCode(DocumentRejectionReason.ILLEGIBLE);
    rejected.setSupersededAt(Instant.parse("2026-11-04T10:00:00Z"));
    driverDocuments.save(rejected);
    driverDocument(DriverDocumentType.CRIMINAL_RECORD, DocumentStatus.PENDING, null);

    page(driver.getToken())
        .andExpect(jsonPath("$.history[0].kind").value("SUBMISSION_SENT"))
        .andExpect(jsonPath("$.history[0].actor").doesNotExist())
        .andExpect(
            jsonPath("$.history[?(@.kind == 'SUBMISSION_REJECTED')].rejectionReasonCode")
                .value(contains("ILLEGIBLE")))
        .andExpect(
            jsonPath("$.history[?(@.kind == 'SUBMISSION_REJECTED')].actor.name")
                .value(contains("Ana Admin")))
        .andExpect(
            jsonPath("$.history[?(@.kind == 'ONBOARDING_SUBMITTED')].at")
                .value(contains("2026-11-01T10:00:00Z")));
  }

  @Test
  void aCorrectedSubmissionNamesWhoCorrectedItInThePageAndInTheHistory() throws Exception {
    DriverDocumentModel course =
        driverDocument(
            DriverDocumentType.SCHOOL_DRIVER_COURSE,
            DocumentStatus.APPROVED,
            Instant.parse("2026-11-02T10:00:00Z"));
    course.getReview().setDataCorrectedById(admin.getId());
    course.getReview().setDataCorrectedAt(Instant.parse("2026-11-05T10:00:00Z"));
    driverDocuments.save(course);

    page(driver.getToken())
        .andExpect(
            jsonPath("$.driverRequirements[1].current.dataCorrectedAt")
                .value("2026-11-05T10:00:00Z"))
        .andExpect(
            jsonPath("$.driverRequirements[1].current.dataCorrectedBy.name").value("Ana Admin"))
        .andExpect(
            jsonPath("$.history[?(@.kind == 'SUBMISSION_DATA_CORRECTED')].actor.token")
                .value(contains("admin-uid")))
        .andExpect(
            jsonPath("$.history[?(@.kind == 'SUBMISSION_DATA_CORRECTED')].requirement")
                .value(contains("SCHOOL_DRIVER_COURSE")));
  }

  @Test
  void aPendingCnhHasNoDataYet() throws Exception {
    DriverCnhModel cnh = fixtures.createCnh(driver, null, null);
    cnh.setIssueDate(null);
    cnh.setValidUntil(null);
    cnh.getReview().setStatus(DocumentStatus.PENDING);
    cnh.setFile(saveMedia());
    cnhs.save(cnh);

    page(driver.getToken())
        .andExpect(jsonPath("$.driverRequirements[0].state").value("PENDING"))
        .andExpect(jsonPath("$.driverRequirements[0].current.registrationNumber").doesNotExist())
        .andExpect(jsonPath("$.driverRequirements[0].current.category").doesNotExist())
        .andExpect(jsonPath("$.driverRequirements[0].current.expiresAt").doesNotExist())
        .andExpect(jsonPath("$.decision.blockers[?(@.kind == 'CNH_CATEGORY')]").isEmpty());
  }

  @Test
  void theHistoryKeepsTheFiftyNewestEvents() throws Exception {
    for (int index = 0; index < 30; index++) {
      DriverDocumentModel document =
          driverDocument(
              DriverDocumentType.CRIMINAL_RECORD, DocumentStatus.APPROVED, Instant.now());
      document.setSupersededAt(Instant.now());
      driverDocuments.save(document);
    }

    page(driver.getToken()).andExpect(jsonPath("$.history.length()").value(50));
  }

  @Test
  void aRejectionNextToTheApprovedSubmissionIsTheLastDecision() throws Exception {
    DriverDocumentModel approved =
        driverDocument(
            DriverDocumentType.CRIMINAL_RECORD,
            DocumentStatus.APPROVED,
            Instant.parse("2026-11-02T10:00:00Z"));
    DriverDocumentModel rejected =
        driverDocument(
            DriverDocumentType.CRIMINAL_RECORD,
            DocumentStatus.REJECTED,
            Instant.parse("2026-11-03T10:00:00Z"));
    rejected.getReview().setRejectionReasonCode(DocumentRejectionReason.ILLEGIBLE);
    driverDocuments.save(rejected);

    page(driver.getToken())
        .andExpect(jsonPath("$.driverRequirements[2].current.token").value(approved.getToken()))
        .andExpect(jsonPath("$.driverRequirements[2].lastDecision.status").value("REJECTED"))
        .andExpect(
            jsonPath("$.driverRequirements[2].lastDecision.rejectionReasonCode")
                .value("ILLEGIBLE"));
  }

  @Test
  void theReviewQueueFollowsTheVansInTheOrderOfThePage() throws Exception {
    VehicleModel vanC = fixtures.createVehicle(driver, "AAA0A00");
    VehicleDocumentModel ofVanB =
        vanDocument(vanB, VehicleDocumentType.CRLV, DocumentStatus.PENDING);
    VehicleDocumentModel ofVanC =
        vanDocument(vanC, VehicleDocumentType.CRLV, DocumentStatus.PENDING);

    page(driver.getToken())
        .andExpect(jsonPath("$.vehicles[2].token").value(vanC.getToken()))
        .andExpect(jsonPath("$.reviewQueue[0].token").value(ofVanB.getToken()))
        .andExpect(jsonPath("$.reviewQueue[1].token").value(ofVanC.getToken()));
  }

  @Test
  void aDecisionAtTheSameInstantAsTheSendingComesFirstInTheHistory() throws Exception {
    DriverDocumentModel document =
        driverDocument(DriverDocumentType.SCHOOL_DRIVER_COURSE, DocumentStatus.APPROVED, null);
    document.getReview().setReviewedAt(document.getCreatedAt());
    driverDocuments.save(document);

    page(driver.getToken())
        .andExpect(jsonPath("$.history[0].kind").value("SUBMISSION_APPROVED"))
        .andExpect(jsonPath("$.history[1].kind").value("SUBMISSION_SENT"));
  }

  @Test
  void anInactiveCnhIsNotInTheHistory() throws Exception {
    DriverCnhModel inactive = cnh(DocumentStatus.APPROVED, Instant.now());
    inactive.setActive(false);
    cnhs.save(inactive);

    page(driver.getToken())
        .andExpect(jsonPath("$.history[?(@.requirement == 'DRIVER_LICENSE')]").isEmpty());
  }

  @Test
  void thePageTakesTheSameNumberOfStatementsWhateverTheVansAndDocuments() throws Exception {
    cnh(DocumentStatus.APPROVED, Instant.now());
    vanDocument(vanA, VehicleDocumentType.CRLV, DocumentStatus.APPROVED);
    SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);
    long few = counter.countStatements(this::readThePage);
    for (int index = 0; index < 4; index++) {
      VehicleModel van =
          fixtures.createVehicle(driver, "QA%sA%02d".formatted((char) ('D' + index), index));
      for (VehicleDocumentType type : VehicleDocumentType.values()) {
        vanDocument(van, type, DocumentStatus.APPROVED);
      }
      DriverDocumentModel superseded =
          driverDocument(
              DriverDocumentType.CRIMINAL_RECORD, DocumentStatus.REJECTED, Instant.now());
      superseded.getReview().setRejectionReasonCode(DocumentRejectionReason.OTHER);
      superseded.setSupersededAt(Instant.now());
      superseded.setFile(null);
      driverDocuments.save(superseded);
    }
    long many = counter.countStatements(this::readThePage);

    assertThat(many).isEqualTo(few);
  }

  @Test
  void anUnknownOrRemovedDriverIsNotFound() throws Exception {
    page("unknown")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("driver.not_found"));
    drivers.delete(driver);
    page(driver.getToken()).andExpect(status().isNotFound());
  }

  @Test
  void onlyReviewersAndApproversReadThePage() throws Exception {
    mockMvc
        .perform(
            get("/api/drivers/" + driver.getToken() + "/compliance")
                .with(jwt().authorities(new SimpleGrantedAuthority("approve_driver"))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/drivers/" + driver.getToken() + "/compliance")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DRIVER"))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/drivers/" + driver.getToken() + "/compliance"))
        .andExpect(status().isUnauthorized());
  }

  private void readThePage() {
    try {
      page(driver.getToken()).andExpect(status().isOk());
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  private ResultActions page(String token) throws Exception {
    return mockMvc.perform(get("/api/drivers/" + token + "/compliance").with(reviewerJwt()));
  }

  private DriverCnhModel cnh(DocumentStatus status, Instant reviewedAt) {
    DriverCnhModel cnh = fixtures.createCnh(driver, "01234567890", "D");
    cnh.setFile(saveMedia());
    decide(cnh.getReview(), status, reviewedAt);
    return cnhs.save(cnh);
  }

  private DriverDocumentModel driverDocument(
      DriverDocumentType type, DocumentStatus status, Instant reviewedAt) {
    DriverDocumentModel document = fixtures.createDriverDocument(driver, type, "N-1", status);
    document.setFile(saveMedia());
    decide(document.getReview(), status, reviewedAt);
    return driverDocuments.save(document);
  }

  private VehicleDocumentModel vanDocument(
      VehicleModel van, VehicleDocumentType type, DocumentStatus status) {
    VehicleDocumentModel document = fixtures.createVehicleDocument(van, type, "N-1", status);
    document.setFile(saveMedia());
    decide(document.getReview(), status, status == DocumentStatus.PENDING ? null : Instant.now());
    return vehicleDocuments.save(document);
  }

  private void decide(DocumentReview review, DocumentStatus status, Instant reviewedAt) {
    review.setStatus(status);
    if (status != DocumentStatus.PENDING) {
      review.setReviewedBy(admin);
      review.setReviewedAt(reviewedAt != null ? reviewedAt : Instant.now());
    }
  }

  private MediaFileModel saveMedia() {
    MediaFileModel file = new MediaFileModel();
    file.setObjectKey("documents/" + System.nanoTime() + ".pdf");
    file.setMimeType("application/pdf");
    file.setSizeBytes(10L);
    file.setOriginalName("documento.pdf");
    return mediaFiles.save(file);
  }

  private static JwtRequestPostProcessor reviewerJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("review_documents"));
  }
}
