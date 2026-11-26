package br.com.vanep.driver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.vanep.auth.mail.MailService;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.dto.DriverCnhStatusUpdateRequestDTO;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.drivercnh.service.DriverCnhService;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.driverdocument.service.DriverDocumentService;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.repository.MediaFileRepository;
import br.com.vanep.schooltransportcredential.SchoolTransportCredentialFixtures;
import br.com.vanep.shared.document.dto.DocumentStatusUpdateRequestDTO;
import br.com.vanep.shared.document.enums.DocumentDecisionStatus;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import br.com.vanep.vehicledocument.repository.VehicleDocumentRepository;
import br.com.vanep.vehicledocument.service.VehicleDocumentService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DocumentNotificationServiceTest {

  @MockitoBean private MailService mailService;

  @Autowired private ApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private MediaFileRepository mediaFiles;
  @Autowired private DriverDocumentRepository driverDocuments;
  @Autowired private VehicleDocumentRepository vehicleDocuments;
  @Autowired private DriverCnhRepository cnhs;
  @Autowired private DriverDocumentService driverDocumentService;
  @Autowired private VehicleDocumentService vehicleDocumentService;
  @Autowired private DriverCnhService cnhService;
  @Autowired private DriverOnboardingService onboardingService;
  @Autowired private TransactionTemplate transactions;

  private SchoolTransportCredentialFixtures fixtures;
  private DriverModel driver;
  private VehicleModel van;

  @BeforeEach
  void setUp() {
    fixtures = new SchoolTransportCredentialFixtures(context);
    driver = fixtures.createDriver("carlos@vanep.com", "12345678909");
    van = fixtures.createVehicle(driver, "VAN1A23");
    UserModel admin = new UserModel();
    admin.setType(UserType.ADMIN);
    admin.setName("Admin");
    admin.setEmail("admin@vanep.com");
    admin.setDocument("00000000000");
    admin.setToken("admin-uid");
    admin.setVerified(true);
    admin.setTermsAcceptedAt(Instant.now());
    users.save(admin);
  }

  @Test
  void aRejectedVanDocumentIsMailedWithThePlateTheReasonAndTheMessage() {
    VehicleDocumentModel inspection =
        withFile(
            fixtures.createVehicleDocument(
                van, VehicleDocumentType.VEHICLE_INSPECTION, null, DocumentStatus.PENDING));

    vehicleDocumentService.updateStatus(
        inspection.getToken(),
        rejection(
            DocumentRejectionReason.ILLEGIBLE, "A foto está escura.", inspection.getVersion()),
        "admin-uid");

    Map<String, Object> variables = mailedVariables("email/document-rejected");
    assertThat(variables.get("name")).isEqualTo("Motorista");
    assertThat((String) variables.get("intro")).contains("Vistoria da van").contains("VAN1A23");
    assertThat((String) variables.get("reason")).startsWith("Não conseguimos ler o documento.");
    assertThat(variables.get("details")).isEqualTo("A foto está escura.");
    assertThat(vehicleDocuments.findByToken(inspection.getToken()).orElseThrow().getNotifiedAt())
        .isNotNull();
  }

  @Test
  void aRejectedDriverDocumentIsMailedWithItsName() {
    DriverDocumentModel record =
        withFile(
            fixtures.createDriverDocument(
                driver, DriverDocumentType.CRIMINAL_RECORD, "CERT-1", DocumentStatus.PENDING));

    driverDocumentService.updateStatus(
        record.getToken(),
        rejection(DocumentRejectionReason.OTHER, "Certidão de outro estado.", record.getVersion()),
        "admin-uid");

    Map<String, Object> variables = mailedVariables("email/document-rejected");
    assertThat((String) variables.get("intro")).contains("Certidão de antecedentes criminais");
    assertThat(variables.get("reason")).isEqualTo("Certidão de outro estado.");
    assertThat(variables).doesNotContainKey("details");
    assertThat(driverDocuments.findByToken(record.getToken()).orElseThrow().getNotifiedAt())
        .isNotNull();
  }

  @Test
  void aRejectedCnhIsMailed() {
    DriverCnhModel cnh = fixtures.createCnh(driver, "01234567890", "D");
    cnh.getReview().setStatus(DocumentStatus.PENDING);
    cnh.setFile(saveMedia());
    cnh = cnhs.save(cnh);

    cnhService.updateStatus(
        cnh.getToken(),
        new DriverCnhStatusUpdateRequestDTO(
            DocumentDecisionStatus.REJECTED,
            null,
            null,
            DocumentRejectionReason.EXPIRED,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            cnh.getVersion()),
        "admin-uid");

    assertThat((String) mailedVariables("email/document-rejected").get("intro")).contains("CNH");
    assertThat(cnhs.findByToken(cnh.getToken()).orElseThrow().getNotifiedAt()).isNotNull();
  }

  @Test
  void anEmailThatCannotBeQueuedIsLoggedAndLeavesNotifiedAtEmpty() {
    doThrow(new IllegalStateException("queue down"))
        .when(mailService)
        .send(anyString(), any(), anyString(), any());
    VehicleDocumentModel crlv =
        withFile(
            fixtures.createVehicleDocument(
                van, VehicleDocumentType.CRLV, "RENAVAM-1", DocumentStatus.PENDING));

    vehicleDocumentService.updateStatus(
        crlv.getToken(),
        rejection(DocumentRejectionReason.WRONG_TYPE, null, crlv.getVersion()),
        "admin-uid");

    VehicleDocumentModel stored = vehicleDocuments.findByToken(crlv.getToken()).orElseThrow();
    assertThat(stored.getReview().getStatus()).isEqualTo(DocumentStatus.REJECTED);
    assertThat(stored.getNotifiedAt()).isNull();
  }

  @Test
  void aRejectionThatRollsBackIsNotMailed() {
    VehicleDocumentModel crlv =
        withFile(
            fixtures.createVehicleDocument(
                van, VehicleDocumentType.CRLV, "RENAVAM-1", DocumentStatus.PENDING));

    transactions.executeWithoutResult(
        status -> {
          vehicleDocumentService.updateStatus(
              crlv.getToken(),
              rejection(DocumentRejectionReason.WRONG_TYPE, null, crlv.getVersion()),
              "admin-uid");
          status.setRollbackOnly();
        });

    verify(mailService, never()).send(anyString(), any(), eq("email/document-rejected"), any());
  }

  @Test
  void theDriversApprovalIsMailedOnlyAfterTheCommit() {
    fixtures.approveTheDriversDocuments(driver, "09876543210");
    fixtures.approveTheVansDocuments(van);
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    drivers.save(driver);

    transactions.executeWithoutResult(
        status -> {
          onboardingService.approve(driver.getToken(), "admin-uid");
          status.setRollbackOnly();
        });
    verify(mailService, never()).send(anyString(), any(), eq("email/driver-approved"), any());

    onboardingService.approve(driver.getToken(), "admin-uid");
    verify(mailService).send(eq("carlos@vanep.com"), any(), eq("email/driver-approved"), any());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> mailedVariables(String template) {
    ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
    verify(mailService).send(eq("carlos@vanep.com"), any(), eq(template), variables.capture());
    return variables.getValue();
  }

  private static DocumentStatusUpdateRequestDTO rejection(
      DocumentRejectionReason code, String message, Long version) {
    return new DocumentStatusUpdateRequestDTO(
        DocumentDecisionStatus.REJECTED, null, null, code, message, null, null, version);
  }

  private DriverDocumentModel withFile(DriverDocumentModel document) {
    document.setFile(saveMedia());
    return driverDocuments.save(document);
  }

  private VehicleDocumentModel withFile(VehicleDocumentModel document) {
    document.setFile(saveMedia());
    return vehicleDocuments.save(document);
  }

  private MediaFileModel saveMedia() {
    MediaFileModel file = new MediaFileModel();
    file.setObjectKey("documents/" + System.nanoTime() + ".pdf");
    file.setMimeType("application/pdf");
    file.setSizeBytes(10L);
    return mediaFiles.save(file);
  }
}
