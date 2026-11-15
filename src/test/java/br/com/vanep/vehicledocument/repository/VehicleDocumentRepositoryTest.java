package br.com.vanep.vehicledocument.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.media.enums.MediaVisibility;
import br.com.vanep.media.enums.StorageProvider;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.repository.MediaFileRepository;
import br.com.vanep.shared.SqlStatementCounter;
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
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class VehicleDocumentRepositoryTest {

  @Autowired private VehicleDocumentRepository documents;
  @Autowired private VehicleRepository vehicles;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;
  @Autowired private MediaFileRepository mediaFiles;
  @Autowired private TransactionTemplate transactions;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private UserModel driverUser;
  private DriverModel driver;
  private VehicleModel van;

  @BeforeEach
  void setUp() {
    driverUser = saveUser("carlos@vanep.com", "52998224725", UserType.DRIVER);
    driver = new DriverModel();
    driver.setUser(driverUser);
    driver.setBasePrice(new BigDecimal("100.00"));
    driver = drivers.save(driver);
    van = saveVehicle("VAN1A23");
  }

  @Test
  void storesAndReadsBackTheDocumentWithItsEmbeddedReview() {
    UserModel admin = saveUser("admin@vanep.com", "11144477735", UserType.ADMIN);
    VehicleDocumentModel document =
        newDocument(
            van, VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION, LocalDate.of(2027, 3, 31));
    document.setDocumentNumber("AUT-2026-0042");
    document.setFile(saveFile());
    document.getReview().setStatus(DocumentStatus.APPROVED);
    document.getReview().setReviewMethod(ReviewMethod.MANUAL);
    document.getReview().setReviewedBy(admin);
    document.getReview().setReviewedAt(Instant.parse("2026-11-10T12:00:00Z"));
    String token = documents.save(document).getToken();

    VehicleDocumentModel stored =
        transactions.execute(status -> documents.findByToken(token).orElseThrow());

    assertThat(stored.getVehicle().getId()).isEqualTo(van.getId());
    assertThat(stored.getVehicle().getDriver().getId()).isEqualTo(driver.getId());
    assertThat(stored.getDocumentType())
        .isEqualTo(VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION);
    assertThat(stored.getDocumentNumber()).isEqualTo("AUT-2026-0042");
    assertThat(stored.getExpiresAt()).isEqualTo(LocalDate.of(2027, 3, 31));
    assertThat(stored.getFile()).isNotNull();
    assertThat(stored.getReview().getStatus()).isEqualTo(DocumentStatus.APPROVED);
    assertThat(stored.getReview().getReviewMethod()).isEqualTo(ReviewMethod.MANUAL);
    assertThat(stored.getReview().getReviewedBy().getId()).isEqualTo(admin.getId());
    assertThat(stored.getReview().getReviewedAt()).isEqualTo(Instant.parse("2026-11-10T12:00:00Z"));
  }

  @Test
  void aNewDocumentIsPendingWithoutTheDataTheReviewerFills() {
    VehicleDocumentModel saved =
        documents.save(newDocument(van, VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION, null));

    VehicleDocumentModel stored = documents.findByToken(saved.getToken()).orElseThrow();
    assertThat(stored.getReview().getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(stored.getToken()).hasSize(25);
    assertThat(stored.getExpiresAt()).isNull();
    assertThat(stored.getDocumentNumber()).isNull();
  }

  @Test
  void aRemovedDocumentIsHiddenAndComesBackWhenRestored() {
    VehicleDocumentModel saved =
        documents.save(newDocument(van, VehicleDocumentType.CRLV, LocalDate.of(2027, 1, 31)));
    documents.delete(saved);

    assertThat(documents.findByToken(saved.getToken())).isEmpty();
    assertThat(documents.existsDeletedByToken(saved.getToken())).isTrue();

    transactions.executeWithoutResult(status -> documents.restoreByToken(saved.getToken()));

    assertThat(documents.findByToken(saved.getToken())).isPresent();
    assertThat(documents.existsDeletedByToken(saved.getToken())).isFalse();
  }

  @Test
  void listsTheVansDocumentsLatestExpiryFirst() {
    VehicleDocumentModel older =
        documents.save(
            newDocument(van, VehicleDocumentType.VEHICLE_INSPECTION, LocalDate.of(2026, 12, 31)));
    VehicleDocumentModel newer =
        documents.save(
            newDocument(van, VehicleDocumentType.VEHICLE_INSPECTION, LocalDate.of(2027, 6, 30)));
    VehicleDocumentModel crlv =
        documents.save(newDocument(van, VehicleDocumentType.CRLV, LocalDate.of(2026, 11, 30)));
    documents.save(
        newDocument(saveVehicle("VAN2B34"), VehicleDocumentType.CRLV, LocalDate.of(2027, 1, 31)));

    assertThat(
            documents.findUnsupersededByVehicleToken(
                van.getToken(), null, null, Pageable.unpaged()))
        .extracting(document -> document.getToken())
        .containsExactly(newer.getToken(), older.getToken(), crlv.getToken());
  }

  @Test
  void filtersTheVansDocumentsByTypeAndByStatus() {
    documents.save(newDocument(van, VehicleDocumentType.CRLV, LocalDate.of(2027, 1, 31)));
    VehicleDocumentModel approvedInspection =
        newDocument(van, VehicleDocumentType.VEHICLE_INSPECTION, LocalDate.of(2027, 6, 30));
    approvedInspection.getReview().setStatus(DocumentStatus.APPROVED);
    String approvedToken = documents.save(approvedInspection).getToken();
    documents.save(
        newDocument(van, VehicleDocumentType.VEHICLE_INSPECTION, LocalDate.of(2026, 12, 31)));

    assertThat(
            documents.findUnsupersededByVehicleToken(
                van.getToken(), VehicleDocumentType.VEHICLE_INSPECTION, null, Pageable.unpaged()))
        .hasSize(2);
    assertThat(
            documents.findUnsupersededByVehicleToken(
                van.getToken(), null, DocumentStatus.APPROVED, Pageable.unpaged()))
        .extracting(document -> document.getToken())
        .containsExactly(approvedToken);
  }

  @Test
  void findsTheDocumentsOfSeveralVansAtOnce() {
    VehicleModel other = saveVehicle("VAN2B34");
    documents.save(newDocument(van, VehicleDocumentType.CRLV, LocalDate.of(2027, 1, 31)));
    documents.save(newDocument(other, VehicleDocumentType.CRLV, LocalDate.of(2027, 1, 31)));
    documents.save(
        newDocument(saveVehicle("VAN3C45"), VehicleDocumentType.CRLV, LocalDate.of(2027, 1, 31)));

    assertThat(documents.findUnsupersededByVehicleIdIn(List.of(van.getId(), other.getId())))
        .extracting(document -> document.getVehicle().getId())
        .containsExactlyInAnyOrder(van.getId(), other.getId());
  }

  @Test
  void theDocumentsOfARemovedVanAreHidden() {
    VehicleDocumentModel saved =
        documents.save(newDocument(van, VehicleDocumentType.CRLV, LocalDate.of(2027, 1, 31)));
    vehicles.delete(van);

    assertThat(documents.findByToken(saved.getToken())).isEmpty();
    assertThat(
            documents.findUnsupersededByVehicleToken(
                van.getToken(), null, null, Pageable.unpaged()))
        .isEmpty();
  }

  @Test
  void theOwnerOfARemovedVanIsStillResolvedSoTheApiAnswersNotFound() {
    VehicleDocumentModel saved =
        documents.save(newDocument(van, VehicleDocumentType.CRLV, LocalDate.of(2027, 1, 31)));
    vehicles.delete(van);

    assertThat(documents.findDriverUserTokenByDocumentToken(saved.getToken()))
        .contains(driverUser.getToken());
  }

  @Test
  void resolvesTheUserTokenOfTheVansDriver() {
    VehicleDocumentModel saved =
        documents.save(newDocument(van, VehicleDocumentType.CRLV, LocalDate.of(2027, 1, 31)));

    assertThat(documents.findDriverUserTokenByDocumentToken(saved.getToken()))
        .contains(driverUser.getToken());
  }

  @Test
  void storesAndReadsBackTheVersionTheReasonCodeTheSupersededDateAndTheNotice() {
    VehicleDocumentModel document =
        newDocument(van, VehicleDocumentType.CRLV, LocalDate.of(2027, 1, 31));
    document.getReview().setStatus(DocumentStatus.REJECTED);
    document.getReview().setRejectionReasonCode(DocumentRejectionReason.EXPIRED);
    document.setSupersededAt(Instant.parse("2026-11-12T10:00:00Z"));
    document.setNotifiedAt(Instant.parse("2026-11-11T10:00:00Z"));
    VehicleDocumentModel saved = documents.save(document);
    saved.setDocumentNumber("changed");
    documents.save(saved);

    VehicleDocumentModel stored = documents.findByToken(saved.getToken()).orElseThrow();

    assertThat(stored.getVersion()).isEqualTo(1L);
    assertThat(stored.getReview().getRejectionReasonCode())
        .isEqualTo(DocumentRejectionReason.EXPIRED);
    assertThat(stored.getSupersededAt()).isEqualTo(Instant.parse("2026-11-12T10:00:00Z"));
    assertThat(stored.getNotifiedAt()).isEqualTo(Instant.parse("2026-11-11T10:00:00Z"));
  }

  @Test
  void storesAndReadsBackTheLastDataCorrectionAndTheNotEligibleReason() {
    VehicleDocumentModel document =
        newDocument(van, VehicleDocumentType.CRLV, LocalDate.of(2027, 1, 31));
    document.getReview().setStatus(DocumentStatus.APPROVED);
    document.getReview().setDataCorrectedById(42L);
    document.getReview().setDataCorrectedAt(Instant.parse("2026-11-21T10:00:00Z"));
    VehicleDocumentModel corrected = documents.save(document);
    VehicleDocumentModel rejected =
        newDocument(van, VehicleDocumentType.VEHICLE_INSPECTION, LocalDate.of(2027, 1, 31));
    rejected.getReview().setStatus(DocumentStatus.REJECTED);
    rejected.getReview().setRejectionReasonCode(DocumentRejectionReason.NOT_ELIGIBLE);
    documents.save(rejected);

    VehicleDocumentModel stored = documents.findByToken(corrected.getToken()).orElseThrow();
    assertThat(stored.getReview().getDataCorrectedById()).isEqualTo(42L);
    assertThat(stored.getReview().getDataCorrectedAt())
        .isEqualTo(Instant.parse("2026-11-21T10:00:00Z"));
    assertThat(
            documents
                .findByToken(rejected.getToken())
                .orElseThrow()
                .getReview()
                .getRejectionReasonCode())
        .isEqualTo(DocumentRejectionReason.NOT_ELIGIBLE);
  }

  @Test
  void theListsAndTheCredentialQueriesIgnoreSupersededSubmissions() {
    VehicleDocumentModel superseded =
        newDocument(van, VehicleDocumentType.VEHICLE_INSPECTION, LocalDate.of(2027, 6, 30));
    superseded.getReview().setStatus(DocumentStatus.APPROVED);
    superseded.setSupersededAt(Instant.now());
    documents.save(superseded);
    String liveToken =
        documents
            .save(
                newDocument(
                    van, VehicleDocumentType.VEHICLE_INSPECTION, LocalDate.of(2027, 12, 31)))
            .getToken();

    assertThat(
            documents.findUnsupersededByVehicleToken(
                van.getToken(), null, null, Pageable.unpaged()))
        .extracting(document -> document.getToken())
        .containsExactly(liveToken);
    assertThat(
            documents
                .findUnsupersededByVehicleToken(van.getToken(), null, null, Pageable.unpaged())
                .getTotalElements())
        .isEqualTo(1);
    assertThat(documents.findUnsupersededByVehicleIdIn(List.of(van.getId())))
        .extracting(document -> document.getToken())
        .containsExactly(liveToken);
    assertThat(documents.findUnsupersededOfActiveVehiclesByDriverId(driver.getId()))
        .extracting(document -> document.getToken())
        .containsExactly(liveToken);
    assertThat(documents.findUnsupersededOfActiveVehicle(driver.getId(), van.getId()))
        .extracting(document -> document.getToken())
        .containsExactly(liveToken);
  }

  @Test
  void theVansListLoadsEachReviewerWithoutOneQueryPerDocument() {
    SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);
    saveReviewedDocuments(0, 2);
    long withTwo = counter.countStatements(this::readTheReviewersOfTheFirstPage);
    saveReviewedDocuments(2, 6);
    long withSix = counter.countStatements(this::readTheReviewersOfTheFirstPage);

    assertThat(withSix).isEqualTo(withTwo);
  }

  private void readTheReviewersOfTheFirstPage() {
    documents
        .findUnsupersededByVehicleToken(van.getToken(), null, null, PageRequest.of(0, 20))
        .forEach(
            document -> {
              document.getReview().getReviewedBy().getEmail();
              document.getFile().getToken();
            });
  }

  private void saveReviewedDocuments(int from, int to) {
    for (int index = from; index < to; index++) {
      VehicleDocumentModel document =
          newDocument(van, VehicleDocumentType.CRLV, LocalDate.of(2027, 1, 1).plusDays(index));
      document.setFile(saveFile());
      document.getReview().setStatus(DocumentStatus.APPROVED);
      document
          .getReview()
          .setReviewedBy(
              saveUser("reviewer" + index + "@vanep.com", "0000000000" + index, UserType.ADMIN));
      documents.save(document);
    }
  }

  private VehicleDocumentModel newDocument(
      VehicleModel vehicle, VehicleDocumentType type, LocalDate expiresAt) {
    VehicleDocumentModel document = new VehicleDocumentModel();
    document.setVehicle(vehicle);
    document.setDocumentType(type);
    document.setExpiresAt(expiresAt);
    return document;
  }

  private VehicleModel saveVehicle(String plate) {
    VehicleModel vehicle = new VehicleModel();
    vehicle.setDriver(driver);
    vehicle.setPlate(plate);
    vehicle.setBrand("Renault");
    vehicle.setModel("Master");
    vehicle.setManufactureYear(2022);
    vehicle.setColor("Branca");
    vehicle.setCapacity(15);
    return vehicles.save(vehicle);
  }

  private UserModel saveUser(String email, String document, UserType type) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName(email);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }

  private MediaFileModel saveFile() {
    MediaFileModel file = new MediaFileModel();
    file.setToken("media" + System.nanoTime());
    file.setProvider(StorageProvider.LOCAL);
    file.setObjectKey("vehicle-document/x/file/" + System.nanoTime() + ".pdf");
    file.setMimeType("application/pdf");
    file.setSizeBytes(1024L);
    file.setVisibility(MediaVisibility.PRIVATE);
    return mediaFiles.save(file);
  }
}
