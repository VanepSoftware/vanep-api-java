package br.com.vanep.drivercompliance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.shared.SqlStatementCounter;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentStatus;
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
import java.time.LocalDate;
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
class DriverMeComplianceControllerTest {

  private static final LocalDate TODAY = SchoolTransportCredentialService.today();

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

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
    fixtures = new SchoolTransportCredentialFixtures(context);
    admin = new UserModel();
    admin.setType(UserType.ADMIN);
    admin.setName("Ana Admin");
    admin.setEmail("admin@vanep.com");
    admin.setDocument("00000000000");
    admin.setVerified(true);
    admin.setTermsAcceptedAt(Instant.now());
    admin = users.save(admin);

    driver = fixtures.createDriver("carlos@vanep.com", "12345678909");
    driver.setApprovalStatus(DriverApprovalStatus.PENDING);
    driver = drivers.save(driver);
  }

  @Test
  void aNewDriverWithNothingSentSeesEveryRequirementMissing() throws Exception {
    mine()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asOf").value(TODAY.toString()))
        .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
        .andExpect(
            jsonPath("$.driverRequirements[*].requirement")
                .value(
                    contains(
                        "DRIVER_LICENSE",
                        "SCHOOL_DRIVER_COURSE",
                        "CRIMINAL_RECORD",
                        "MUNICIPAL_AUTHORIZATION")))
        .andExpect(
            jsonPath("$.driverRequirements[*].state")
                .value(contains("MISSING", "MISSING", "MISSING", "MISSING")))
        .andExpect(jsonPath("$.driverRequirements[0].current").doesNotExist())
        .andExpect(jsonPath("$.driverRequirements[0].pending").doesNotExist())
        .andExpect(jsonPath("$.driverRequirements[0].optional").value(false))
        .andExpect(jsonPath("$.driverRequirements[3].optional").value(true))
        .andExpect(jsonPath("$.vehicles").isEmpty());
  }

  @Test
  void eachActiveVanComesWithItsRequirementsAndAnInactiveOneIsLeftOut() throws Exception {
    VehicleModel van = fixtures.createVehicle(driver, "AAA1A11");
    fixtures.approveTheVansDocuments(van);
    VehicleModel inactive = fixtures.createVehicle(driver, "BBB2B22");
    inactive.setActive(false);
    vehicles.save(inactive);

    mine()
        .andExpect(jsonPath("$.vehicles.length()").value(1))
        .andExpect(jsonPath("$.vehicles[0].token").value(van.getToken()))
        .andExpect(jsonPath("$.vehicles[0].plate").value("AAA1A11"))
        .andExpect(jsonPath("$.vehicles[0].brand").value("Renault"))
        .andExpect(jsonPath("$.vehicles[0].model").value("Master"))
        .andExpect(jsonPath("$.vehicles[0].qualified").value(true))
        .andExpect(
            jsonPath("$.vehicles[0].requirements[*].requirement")
                .value(
                    contains(
                        "VEHICLE_REGISTRATION",
                        "VEHICLE_INSPECTION",
                        "SCHOOL_VEHICLE_AUTHORIZATION")))
        .andExpect(
            jsonPath("$.vehicles[0].requirements[*].state")
                .value(contains("APPROVED", "APPROVED", "APPROVED")));
  }

  @Test
  void anApprovedInspectionExpiringWithinThirtyDaysIsExpiringSoon() throws Exception {
    VehicleModel van = fixtures.createVehicle(driver, "AAA1A11");
    vanDocument(van, VehicleDocumentType.CRLV, DocumentStatus.APPROVED, TODAY.plusDays(31));
    vanDocument(
        van, VehicleDocumentType.VEHICLE_INSPECTION, DocumentStatus.APPROVED, TODAY.plusDays(30));
    vanDocument(
        van,
        VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION,
        DocumentStatus.PENDING,
        TODAY.plusDays(10));

    mine()
        .andExpect(jsonPath("$.vehicles[0].requirements[0].expiringSoon").value(false))
        .andExpect(jsonPath("$.vehicles[0].requirements[1].state").value("APPROVED"))
        .andExpect(jsonPath("$.vehicles[0].requirements[1].satisfied").value(true))
        .andExpect(jsonPath("$.vehicles[0].requirements[1].expiringSoon").value(true))
        .andExpect(jsonPath("$.vehicles[0].requirements[2].state").value("PENDING"))
        .andExpect(jsonPath("$.vehicles[0].requirements[2].expiringSoon").value(false));
  }

  @Test
  void aDocumentJustSentIsPendingWithItsFileAndWithoutData() throws Exception {
    VehicleModel van = fixtures.createVehicle(driver, "AAA1A11");
    VehicleDocumentModel sent =
        vanDocument(van, VehicleDocumentType.VEHICLE_INSPECTION, DocumentStatus.PENDING, null);
    sent.setDocumentNumber(null);
    vehicleDocuments.save(sent);

    mine()
        .andExpect(jsonPath("$.vehicles[0].requirements[1].state").value("PENDING"))
        .andExpect(jsonPath("$.vehicles[0].requirements[1].current.file.mimeType").exists())
        .andExpect(jsonPath("$.vehicles[0].requirements[1].current.expiresAt").doesNotExist())
        .andExpect(jsonPath("$.vehicles[0].requirements[1].current.documentNumber").doesNotExist());
  }

  @Test
  void aRejectionShowsItsReasonWithoutTheReviewer() throws Exception {
    DriverDocumentModel course =
        fixtures.createDriverDocument(
            driver, DriverDocumentType.SCHOOL_DRIVER_COURSE, "CURSO-1", DocumentStatus.REJECTED);
    course.setFile(saveMedia());
    course.getReview().setRejectionReasonCode(DocumentRejectionReason.ILLEGIBLE);
    course.getReview().setRejectionReason("A foto saiu escura.");
    course.getReview().setReviewedBy(admin);
    course.getReview().setReviewedAt(Instant.now());
    driverDocuments.save(course);

    mine()
        .andExpect(jsonPath("$.driverRequirements[1].state").value("REJECTED"))
        .andExpect(jsonPath("$.driverRequirements[1].current.token").value(course.getToken()))
        .andExpect(jsonPath("$.driverRequirements[1].current.kind").value("DRIVER_DOCUMENT"))
        .andExpect(jsonPath("$.driverRequirements[1].current.documentNumber").value("CURSO-1"))
        .andExpect(
            jsonPath("$.driverRequirements[1].current.rejectionReasonCode").value("ILLEGIBLE"))
        .andExpect(
            jsonPath("$.driverRequirements[1].current.rejectionReason")
                .value("A foto saiu escura."))
        .andExpect(jsonPath("$.driverRequirements[1].current.reviewedAt").exists())
        .andExpect(jsonPath("$.driverRequirements[1].current.reviewedBy").doesNotExist())
        .andExpect(
            jsonPath("$.driverRequirements[1].current.file.mimeType").value("application/pdf"))
        .andExpect(content().string(not(containsString("Ana Admin"))))
        .andExpect(content().string(not(containsString("12345678909"))))
        .andExpect(content().string(not(containsString("carlos@vanep.com"))));
  }

  @Test
  void theCnhShowsItsWholeNumberAndARenewalWaitsAsPending() throws Exception {
    DriverCnhModel approved = fixtures.createCnh(driver, "01234567890", "D");
    approved.setFile(saveMedia());
    approved.getReview().setReviewedBy(admin);
    approved.getReview().setReviewedAt(Instant.now());
    cnhs.save(approved);
    DriverCnhModel renewal = fixtures.createCnh(driver, "01234567890", "D");
    renewal.getReview().setStatus(DocumentStatus.PENDING);
    cnhs.save(renewal);

    mine()
        .andExpect(jsonPath("$.driverRequirements[0].state").value("APPROVED"))
        .andExpect(jsonPath("$.driverRequirements[0].current.token").value(approved.getToken()))
        .andExpect(jsonPath("$.driverRequirements[0].current.kind").value("DRIVER_CNH"))
        .andExpect(
            jsonPath("$.driverRequirements[0].current.registrationNumber").value("01234567890"))
        .andExpect(jsonPath("$.driverRequirements[0].current.category").value("D"))
        .andExpect(jsonPath("$.driverRequirements[0].current.issueDate").value("2015-03-10"))
        .andExpect(jsonPath("$.driverRequirements[0].current.documentNumber").doesNotExist())
        .andExpect(jsonPath("$.driverRequirements[0].pending.token").value(renewal.getToken()))
        .andExpect(jsonPath("$.driverRequirements[0].pending.file").doesNotExist())
        .andExpect(jsonPath("$.driverRequirements[0].pending.fileUrl").doesNotExist());
  }

  @Test
  void theDriversComplianceTakesTheSameNumberOfStatementsWhateverTheVans() throws Exception {
    DriverCnhModel cnh = fixtures.createCnh(driver, "01234567890", "D");
    cnh.setFile(saveMedia());
    cnhs.save(cnh);
    vanDocument(
        fixtures.createVehicle(driver, "AAA1A11"),
        VehicleDocumentType.CRLV,
        DocumentStatus.APPROVED,
        TODAY.plusYears(1));
    SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);
    long few = counter.countStatements(this::readMine);
    for (int index = 0; index < 2; index++) {
      VehicleModel van =
          fixtures.createVehicle(driver, "QA%sA%02d".formatted((char) ('D' + index), index));
      for (VehicleDocumentType type : VehicleDocumentType.values()) {
        vanDocument(van, type, DocumentStatus.APPROVED, TODAY.plusMonths(6));
      }
    }
    long many = counter.countStatements(this::readMine);

    assertThat(many).isEqualTo(few);
  }

  @Test
  void aClientOrAnAdminIsRefusedAndAnAnonymousCallerIsUnauthorized() throws Exception {
    UserModel client = new UserModel();
    client.setType(UserType.CLIENT);
    client.setName("Cliente");
    client.setEmail("cliente@vanep.com");
    client.setDocument("11144477735");
    client.setVerified(true);
    client.setTermsAcceptedAt(Instant.now());
    client = users.save(client);

    mockMvc
        .perform(get("/api/drivers/me/compliance").with(callerJwt(client, "ROLE_CLIENT")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/drivers/me/compliance").with(callerJwt(admin, "ROLE_ADMIN")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/drivers/me/compliance")).andExpect(status().isUnauthorized());
  }

  private void readMine() {
    try {
      mine().andExpect(status().isOk());
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  private ResultActions mine() throws Exception {
    return mockMvc.perform(
        get("/api/drivers/me/compliance").with(callerJwt(driver.getUser(), "ROLE_DRIVER")));
  }

  private static JwtRequestPostProcessor callerJwt(UserModel user, String role) {
    return jwt()
        .jwt(token -> token.claim("uid", user.getToken()))
        .authorities(new SimpleGrantedAuthority(role));
  }

  private VehicleDocumentModel vanDocument(
      VehicleModel van, VehicleDocumentType type, DocumentStatus status, LocalDate expiresAt) {
    VehicleDocumentModel document = fixtures.createVehicleDocument(van, type, "N-1", status);
    document.setFile(saveMedia());
    document.setExpiresAt(expiresAt);
    if (status != DocumentStatus.PENDING) {
      document.getReview().setReviewedBy(admin);
      document.getReview().setReviewedAt(Instant.now());
    }
    return vehicleDocuments.save(document);
  }

  private MediaFileModel saveMedia() {
    MediaFileModel file = new MediaFileModel();
    file.setObjectKey("documents/" + System.nanoTime() + ".pdf");
    file.setMimeType("application/pdf");
    file.setSizeBytes(10L);
    file.setOriginalName("documento.pdf");
    return mediaFiles.save(file);
  }
}
