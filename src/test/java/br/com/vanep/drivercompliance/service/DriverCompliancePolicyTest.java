package br.com.vanep.drivercompliance.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO.BlockerDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO.RequirementDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceInputDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceInputDTO.VehicleInputDTO;
import br.com.vanep.drivercompliance.enums.BlockerKind;
import br.com.vanep.drivercompliance.enums.ComplianceState;
import br.com.vanep.drivercompliance.enums.NextAction;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.schooltransportcredential.enums.CredentialRequirement;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialPolicy;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverCompliancePolicyTest {

  private static final LocalDate DATE = LocalDate.of(2026, 11, 12);
  private static final Instant LONG_AGO = Instant.parse("2026-01-01T00:00:00Z");

  private final DriverCompliancePolicy policy =
      new DriverCompliancePolicy(new SchoolTransportCredentialPolicy());

  private DriverModel driver;
  private List<DriverCnhModel> cnhs;
  private List<DriverDocumentModel> driverDocuments;
  private List<VehicleInputDTO> vehicles;
  private VehicleInputDTO vanA;

  @BeforeEach
  void setUp() {
    driver = new DriverModel();
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    cnhs = new ArrayList<>(List.of(cnh("D", DATE.plusYears(3), DocumentStatus.APPROVED, LONG_AGO)));
    driverDocuments =
        new ArrayList<>(
            List.of(
                driverDocument(
                    DriverDocumentType.SCHOOL_DRIVER_COURSE, null, DocumentStatus.APPROVED),
                driverDocument(
                    DriverDocumentType.CRIMINAL_RECORD,
                    DATE.plusMonths(6),
                    DocumentStatus.APPROVED)));
    vanA = completeVan(1L, "VAN1A23");
    vehicles = new ArrayList<>(List.of(vanA));
  }

  @Test
  void everythingApprovedIsReadyToApprove() {
    DriverComplianceDTO compliance = evaluate();

    assertThat(compliance.canApprove()).isTrue();
    assertThat(compliance.nextAction()).isEqualTo(NextAction.READY_TO_APPROVE);
    assertThat(compliance.blockers()).isEmpty();
    assertThat(compliance.qualifiedVehicles()).hasSize(1);
    assertThat(compliance.driverRequirements())
        .filteredOn(entry -> !entry.optional())
        .allMatch(entry -> entry.state() == ComplianceState.APPROVED && entry.satisfied());
  }

  @Test
  void aMissingRequirementIsMissing() {
    driverDocuments.removeIf(
        document -> document.getDocumentType() == DriverDocumentType.CRIMINAL_RECORD);

    RequirementDTO entry = evaluate().driverRequirement(CredentialRequirement.CRIMINAL_RECORD);

    assertThat(entry.state()).isEqualTo(ComplianceState.MISSING);
    assertThat(entry.satisfied()).isFalse();
    assertThat(entry.current()).isNull();
  }

  @Test
  void anExpiredApprovalWithoutRenewalIsExpired() {
    criminalRecord().setExpiresAt(DATE.minusDays(1));

    RequirementDTO entry = evaluate().driverRequirement(CredentialRequirement.CRIMINAL_RECORD);

    assertThat(entry.state()).isEqualTo(ComplianceState.EXPIRED);
    assertThat(entry.satisfied()).isFalse();
  }

  @Test
  void aPendingSubmissionAlreadyExpiredIsExpired() {
    criminalRecord().getReview().setStatus(DocumentStatus.PENDING);
    criminalRecord().setExpiresAt(DATE.minusDays(1));

    assertThat(evaluate().driverRequirement(CredentialRequirement.CRIMINAL_RECORD).state())
        .isEqualTo(ComplianceState.EXPIRED);
  }

  @Test
  void theNewestRejectedOrPendingSubmissionGivesTheState() {
    criminalRecord().getReview().setStatus(DocumentStatus.REJECTED);
    assertThat(evaluate().driverRequirement(CredentialRequirement.CRIMINAL_RECORD).state())
        .isEqualTo(ComplianceState.REJECTED);

    criminalRecord().getReview().setStatus(DocumentStatus.PENDING);
    assertThat(evaluate().driverRequirement(CredentialRequirement.CRIMINAL_RECORD).state())
        .isEqualTo(ComplianceState.PENDING);
  }

  @Test
  void aRenewalUnderReviewNextToTheApprovalIsNamed() {
    VehicleDocumentModel renewal =
        vehicleDocument(
            VehicleDocumentType.VEHICLE_INSPECTION, DATE.plusYears(1), DocumentStatus.PENDING);
    renewal.setCreatedAt(Instant.parse("2026-11-10T00:00:00Z"));
    vanA.documents().add(renewal);

    RequirementDTO inspection =
        evaluate().vehicles().get(0).requirements().stream()
            .filter(entry -> entry.requirement() == CredentialRequirement.VEHICLE_INSPECTION)
            .findFirst()
            .orElseThrow();

    assertThat(inspection.state()).isEqualTo(ComplianceState.APPROVED);
    assertThat(inspection.satisfied()).isTrue();
    assertThat(inspection.current()).isNotSameAs(renewal);
    assertThat(inspection.pending()).isSameAs(renewal);
  }

  @Test
  void oneCompleteVanOfTwoIsEnough() {
    vehicles.add(new VehicleInputDTO(van(2L, "VAN2B34"), new ArrayList<>()));

    DriverComplianceDTO compliance = evaluate();

    assertThat(compliance.canApprove()).isTrue();
    assertThat(compliance.qualifiedVehicles())
        .extracting(vehicle -> vehicle.vehicle().getPlate())
        .containsExactly("VAN1A23");
  }

  @Test
  void withoutAQualifiedVanTheBlockersNameTheVanClosestToComplete() {
    vanA.documents().get(2).getReview().setStatus(DocumentStatus.REJECTED);
    VehicleInputDTO bare = new VehicleInputDTO(van(2L, "VAN2B34"), new ArrayList<>());
    vehicles.add(bare);

    DriverComplianceDTO compliance = evaluate();

    assertThat(compliance.canApprove()).isFalse();
    assertThat(compliance.blockers())
        .containsExactly(
            new BlockerDTO(
                BlockerKind.VEHICLE_REQUIREMENT,
                CredentialRequirement.SCHOOL_VEHICLE_AUTHORIZATION,
                ComplianceState.REJECTED,
                vanA.vehicle().getToken(),
                "VAN1A23"));
  }

  @Test
  void onTheSameNumberOfGapsTheOldestVanIsTheClosest() {
    vehicles.clear();
    VehicleInputDTO newer = new VehicleInputDTO(van(2L, "NEW1A11"), new ArrayList<>());
    newer.vehicle().setCreatedAt(Instant.parse("2026-11-01T00:00:00Z"));
    VehicleInputDTO older = new VehicleInputDTO(van(3L, "OLD1A11"), new ArrayList<>());
    older.vehicle().setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    vehicles.addAll(List.of(newer, older));

    assertThat(evaluate().blockers())
        .extracting(blocker -> blocker.plate())
        .containsOnly("OLD1A11");
  }

  @Test
  void aLicenseWithoutDOrEIsACategoryBlockerEvenWhileUnderReview() {
    DriverCnhModel categoryB = cnh("B", DATE.plusYears(3), DocumentStatus.PENDING, Instant.now());
    cnhs.set(0, categoryB);

    DriverComplianceDTO compliance = evaluate();

    assertThat(compliance.blockers())
        .extracting(blocker -> blocker.kind())
        .containsExactly(BlockerKind.CNH_CATEGORY);
    categoryB.getReview().setStatus(DocumentStatus.APPROVED);
    RequirementDTO license = evaluate().driverRequirement(CredentialRequirement.DRIVER_LICENSE);
    assertThat(license.state()).isEqualTo(ComplianceState.APPROVED);
    assertThat(license.satisfied()).isFalse();
  }

  @Test
  void aDriverNotUnderReviewCannotBeApprovedAndANoVanDriverHasABlocker() {
    driver.setApprovalStatus(DriverApprovalStatus.PENDING);
    vehicles.clear();

    assertThat(evaluate().blockers())
        .extracting(blocker -> blocker.kind())
        .containsExactly(BlockerKind.APPROVAL_STATUS, BlockerKind.NO_ACTIVE_VEHICLE);
  }

  @Test
  void aSubmissionWaitingForReviewIsTheAdminsMove() {
    criminalRecord().getReview().setStatus(DocumentStatus.PENDING);

    DriverComplianceDTO compliance = evaluate();

    assertThat(compliance.nextAction()).isEqualTo(NextAction.ADMIN);
    assertThat(compliance.pendingReviewCount()).isEqualTo(1);
    assertThat(compliance.oldestPendingAt()).isEqualTo(criminalRecord().getCreatedAt());
  }

  @Test
  void aPendingSubmissionWithoutFileIsStillTheDriversMove() {
    criminalRecord().getReview().setStatus(DocumentStatus.PENDING);
    criminalRecord().setFile(null);

    DriverComplianceDTO compliance = evaluate();

    assertThat(compliance.nextAction()).isEqualTo(NextAction.DRIVER);
    assertThat(compliance.pendingReviewCount()).isZero();
  }

  @Test
  void aRejectedAuthorizationWithNothingPendingIsTheDriversMove() {
    vanA.documents().get(2).getReview().setStatus(DocumentStatus.REJECTED);

    assertThat(evaluate().nextAction()).isEqualTo(NextAction.DRIVER);
  }

  @Test
  void anApprovedDriverRenewingIsTheAdminsMoveAndInOrderOtherwise() {
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    assertThat(evaluate().nextAction()).isEqualTo(NextAction.NONE);

    vanA.documents()
        .add(
            vehicleDocument(
                VehicleDocumentType.VEHICLE_INSPECTION, DATE.plusYears(1), DocumentStatus.PENDING));
    DriverComplianceDTO renewing = evaluate();
    assertThat(renewing.nextAction()).isEqualTo(NextAction.ADMIN);
    assertThat(renewing.pendingReviewCount()).isEqualTo(1);
  }

  @Test
  void anApprovedDriverWhoseRequirementExpiredMustAct() {
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    criminalRecord().setExpiresAt(DATE.minusDays(1));

    assertThat(evaluate().nextAction()).isEqualTo(NextAction.DRIVER);
  }

  @Test
  void aRejectedOrPendingRegistrationIsTheDriversMove() {
    driver.setApprovalStatus(DriverApprovalStatus.REJECTED);
    assertThat(evaluate().nextAction()).isEqualTo(NextAction.DRIVER);
    driver.setApprovalStatus(DriverApprovalStatus.PENDING);
    assertThat(evaluate().nextAction()).isEqualTo(NextAction.DRIVER);
  }

  @Test
  void theCountsAndTheNearestExpiryCoverTheRequiredRequirementsOnly() {
    criminalRecord().setExpiresAt(DATE.minusDays(2));
    driverDocuments.removeIf(
        document -> document.getDocumentType() == DriverDocumentType.SCHOOL_DRIVER_COURSE);
    vanA.documents().get(2).getReview().setStatus(DocumentStatus.REJECTED);
    DriverDocumentModel municipal =
        driverDocument(
            DriverDocumentType.MUNICIPAL_AUTHORIZATION,
            DATE.minusYears(1),
            DocumentStatus.APPROVED);
    driverDocuments.add(municipal);

    DriverComplianceDTO compliance = evaluate();

    assertThat(compliance.expiredCount()).isEqualTo(1);
    assertThat(compliance.missingCount()).isEqualTo(1);
    assertThat(compliance.rejectedCount()).isEqualTo(1);
    assertThat(compliance.nextExpiryOn()).isEqualTo(DATE.plusMonths(5));
  }

  private DriverComplianceDTO evaluate() {
    return policy.evaluate(
        new DriverComplianceInputDTO(driver, cnhs, driverDocuments, vehicles), DATE);
  }

  private DriverDocumentModel criminalRecord() {
    return driverDocuments.stream()
        .filter(document -> document.getDocumentType() == DriverDocumentType.CRIMINAL_RECORD)
        .findFirst()
        .orElseThrow();
  }

  private static VehicleInputDTO completeVan(Long id, String plate) {
    return new VehicleInputDTO(
        van(id, plate),
        new ArrayList<>(
            List.of(
                vehicleDocument(
                    VehicleDocumentType.CRLV, DATE.plusYears(1), DocumentStatus.APPROVED),
                vehicleDocument(
                    VehicleDocumentType.VEHICLE_INSPECTION,
                    DATE.plusMonths(5),
                    DocumentStatus.APPROVED),
                vehicleDocument(
                    VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION,
                    DATE.plusMonths(8),
                    DocumentStatus.APPROVED))));
  }

  private static VehicleModel van(Long id, String plate) {
    VehicleModel vehicle = new VehicleModel();
    vehicle.setId(id);
    vehicle.setToken("van-" + id);
    vehicle.setPlate(plate);
    vehicle.setCreatedAt(LONG_AGO);
    return vehicle;
  }

  private static DriverCnhModel cnh(
      String category, LocalDate validUntil, DocumentStatus status, Instant createdAt) {
    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setRegistrationNumber("01234567890");
    cnh.setCategory(category);
    cnh.setValidUntil(validUntil);
    cnh.setFile(new MediaFileModel());
    cnh.setCreatedAt(createdAt);
    cnh.getReview().setStatus(status);
    return cnh;
  }

  private static DriverDocumentModel driverDocument(
      DriverDocumentType type, LocalDate expiresAt, DocumentStatus status) {
    DriverDocumentModel document = new DriverDocumentModel();
    document.setDocumentType(type);
    document.setExpiresAt(expiresAt);
    document.setFile(new MediaFileModel());
    document.setCreatedAt(LONG_AGO);
    document.getReview().setStatus(status);
    return document;
  }

  private static VehicleDocumentModel vehicleDocument(
      VehicleDocumentType type, LocalDate expiresAt, DocumentStatus status) {
    VehicleDocumentModel document = new VehicleDocumentModel();
    document.setDocumentType(type);
    document.setExpiresAt(expiresAt);
    document.setFile(new MediaFileModel());
    document.setCreatedAt(LONG_AGO);
    document.getReview().setStatus(status);
    return document;
  }
}
