package br.com.vanep.schooltransportcredential.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.schooltransportcredential.dto.CredentialDTO;
import br.com.vanep.schooltransportcredential.dto.SchoolTransportCredentialsDTO;
import br.com.vanep.schooltransportcredential.enums.CredentialRequirement;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class SchoolTransportCredentialPolicyTest {

  private static final LocalDate DATE = LocalDate.of(2026, 11, 12);
  private static final LocalDate VALID = LocalDate.of(2027, 6, 30);

  private final SchoolTransportCredentialPolicy policy = new SchoolTransportCredentialPolicy();

  private List<DriverCnhModel> cnhs;
  private List<DriverDocumentModel> driverDocuments;
  private List<VehicleDocumentModel> vehicleDocuments;

  @BeforeEach
  void setUp() {
    cnhs = new ArrayList<>(List.of(cnh("AD", LocalDate.of(2030, 1, 31), true)));
    driverDocuments =
        new ArrayList<>(
            List.of(
                driverDocument(DriverDocumentType.SCHOOL_DRIVER_COURSE, "CURSO-1", null),
                driverDocument(DriverDocumentType.CRIMINAL_RECORD, "CERT-1", VALID)));
    vehicleDocuments =
        new ArrayList<>(
            List.of(
                vehicleDocument(VehicleDocumentType.CRLV, "RENAVAM-1", VALID),
                vehicleDocument(VehicleDocumentType.VEHICLE_INSPECTION, null, VALID),
                vehicleDocument(
                    VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION, "AUT-118", VALID)));
  }

  private SchoolTransportCredentialsDTO evaluate() {
    return policy.evaluate(cnhs, driverDocuments, vehicleDocuments, DATE);
  }

  @Test
  void everythingApprovedAndValidSatisfiesEveryRequirement() {
    SchoolTransportCredentialsDTO result = evaluate();

    assertThat(result.failed()).isEmpty();
    assertThat(result.satisfied()).isTrue();
    assertThat(result.credentials())
        .extracting(credential -> credential.requirement())
        .containsExactly(
            CredentialRequirement.DRIVER_LICENSE,
            CredentialRequirement.SCHOOL_DRIVER_COURSE,
            CredentialRequirement.CRIMINAL_RECORD,
            CredentialRequirement.VEHICLE_REGISTRATION,
            CredentialRequirement.VEHICLE_INSPECTION,
            CredentialRequirement.SCHOOL_VEHICLE_AUTHORIZATION);
    assertThat(credential(result, CredentialRequirement.DRIVER_LICENSE))
        .isEqualTo(
            new CredentialDTO(
                CredentialRequirement.DRIVER_LICENSE, "01234567890", LocalDate.of(2030, 1, 31)));
    assertThat(credential(result, CredentialRequirement.SCHOOL_VEHICLE_AUTHORIZATION))
        .isEqualTo(
            new CredentialDTO(
                CredentialRequirement.SCHOOL_VEHICLE_AUTHORIZATION, "AUT-118", VALID));
    assertThat(credential(result, CredentialRequirement.SCHOOL_DRIVER_COURSE).validUntil())
        .isNull();
  }

  @Test
  void withoutTheVansDocumentsTheThreeVehicleRequirementsFail() {
    vehicleDocuments.clear();

    assertThat(evaluate().failed())
        .containsExactlyInAnyOrder(
            CredentialRequirement.VEHICLE_REGISTRATION,
            CredentialRequirement.VEHICLE_INSPECTION,
            CredentialRequirement.SCHOOL_VEHICLE_AUTHORIZATION);
  }

  @Test
  void expiredTheDayBeforeTheDateFails() {
    vehicleDocuments.set(
        1, vehicleDocument(VehicleDocumentType.VEHICLE_INSPECTION, null, DATE.minusDays(1)));

    assertThat(evaluate().failed()).containsExactly(CredentialRequirement.VEHICLE_INSPECTION);
  }

  @Test
  void validUntilTheDateItselfPasses() {
    vehicleDocuments.set(1, vehicleDocument(VehicleDocumentType.VEHICLE_INSPECTION, null, DATE));

    assertThat(evaluate().failed()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = DocumentStatus.class,
      names = {"PENDING", "REJECTED"})
  void aDocumentNotApprovedFails(DocumentStatus status) {
    driverDocuments.get(1).getReview().setStatus(status);

    assertThat(evaluate().failed()).containsExactly(CredentialRequirement.CRIMINAL_RECORD);
  }

  @Test
  void anInactiveDriverDocumentFails() {
    driverDocuments.get(0).setActive(false);

    assertThat(evaluate().failed()).containsExactly(CredentialRequirement.SCHOOL_DRIVER_COURSE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"B", "A", "AB", "C"})
  void aCnhWithoutCategoryDOrEFails(String category) {
    cnhs.set(0, cnh(category, LocalDate.of(2030, 1, 31), true));

    assertThat(evaluate().failed()).containsExactly(CredentialRequirement.DRIVER_LICENSE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"AD", "AE", "D", "E"})
  void aCnhWithCategoryDOrEPasses(String category) {
    cnhs.set(0, cnh(category, LocalDate.of(2030, 1, 31), true));

    assertThat(evaluate().failed()).isEmpty();
  }

  @Test
  void anInactiveCnhFails() {
    cnhs.set(0, cnh("D", LocalDate.of(2030, 1, 31), false));

    assertThat(evaluate().failed()).containsExactly(CredentialRequirement.DRIVER_LICENSE);
  }

  @ParameterizedTest
  @EnumSource(value = DocumentStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
  void onlyAnApprovedCnhSatisfiesTheDriverLicense(DocumentStatus status) {
    DriverCnhModel notApproved = cnh("D", LocalDate.of(2030, 1, 31), true);
    notApproved.getReview().setStatus(status);
    cnhs.set(0, notApproved);

    assertThat(evaluate().failed()).containsExactly(CredentialRequirement.DRIVER_LICENSE);
  }

  @Test
  void aPendingRenewalNextToTheApprovedCnhKeepsTheLicense() {
    DriverCnhModel renewal = cnh("D", LocalDate.of(2035, 1, 31), true);
    renewal.getReview().setStatus(DocumentStatus.PENDING);
    cnhs.add(renewal);

    assertThat(evaluate().failed()).isEmpty();
  }

  @Test
  void anExpiredCnhFails() {
    cnhs.set(0, cnh("D", DATE.minusDays(1), true));

    assertThat(evaluate().failed()).containsExactly(CredentialRequirement.DRIVER_LICENSE);
  }

  @Test
  void aCnhWithoutCategoryOrExpiryFails() {
    cnhs.set(0, cnh(null, null, true));

    assertThat(evaluate().failed()).containsExactly(CredentialRequirement.DRIVER_LICENSE);
  }

  @Test
  void betweenTwoValidDocumentsTheOneWithTheFarthestExpiryCounts() {
    vehicleDocuments.add(
        vehicleDocument(VehicleDocumentType.VEHICLE_INSPECTION, "VIST-2", VALID.plusMonths(6)));

    assertThat(credential(evaluate(), CredentialRequirement.VEHICLE_INSPECTION))
        .isEqualTo(
            new CredentialDTO(
                CredentialRequirement.VEHICLE_INSPECTION, "VIST-2", VALID.plusMonths(6)));
  }

  @Test
  void aDriverDocumentWithoutExpiryBeatsOneWithExpiry() {
    driverDocuments.add(driverDocument(DriverDocumentType.CRIMINAL_RECORD, "CERT-2", null));

    assertThat(credential(evaluate(), CredentialRequirement.CRIMINAL_RECORD).number())
        .isEqualTo("CERT-2");
  }

  @Test
  void betweenTwoDocumentsWithoutExpiryTheMostRecentCounts() {
    DriverDocumentModel newer =
        driverDocument(DriverDocumentType.SCHOOL_DRIVER_COURSE, "CURSO-2", null);
    newer.setCreatedAt(Instant.parse("2026-11-01T00:00:00Z"));
    driverDocuments.add(newer);

    assertThat(credential(evaluate(), CredentialRequirement.SCHOOL_DRIVER_COURSE).number())
        .isEqualTo("CURSO-2");
  }

  @Test
  void anApprovedMunicipalAuthorizationIsListedButItsAbsenceNeverFails() {
    assertThat(evaluate().credentials())
        .extracting(credential -> credential.requirement())
        .doesNotContain(CredentialRequirement.MUNICIPAL_AUTHORIZATION);

    driverDocuments.add(
        driverDocument(DriverDocumentType.MUNICIPAL_AUTHORIZATION, "TE-0001/2026", VALID));
    SchoolTransportCredentialsDTO result = evaluate();

    assertThat(result.failed()).isEmpty();
    assertThat(credential(result, CredentialRequirement.MUNICIPAL_AUTHORIZATION).number())
        .isEqualTo("TE-0001/2026");
  }

  @Test
  void aPendingMunicipalAuthorizationIsNotListedAndDoesNotFail() {
    DriverDocumentModel pending =
        driverDocument(DriverDocumentType.MUNICIPAL_AUTHORIZATION, "TE-0001/2026", VALID);
    pending.getReview().setStatus(DocumentStatus.PENDING);
    driverDocuments.add(pending);

    SchoolTransportCredentialsDTO result = evaluate();

    assertThat(result.failed()).isEmpty();
    assertThat(result.credentials())
        .extracting(credential -> credential.requirement())
        .doesNotContain(CredentialRequirement.MUNICIPAL_AUTHORIZATION);
  }

  @Test
  void aDocumentOfAnotherTypeDoesNotSatisfyTheCourse() {
    driverDocuments.set(0, driverDocument(DriverDocumentType.CRIMINAL_RECORD, "X", null));

    assertThat(evaluate().failed()).containsExactly(CredentialRequirement.SCHOOL_DRIVER_COURSE);
  }

  private static CredentialDTO credential(
      SchoolTransportCredentialsDTO result, CredentialRequirement requirement) {
    return result.credentials().stream()
        .filter(credential -> credential.requirement() == requirement)
        .findFirst()
        .orElseThrow();
  }

  private static DriverCnhModel cnh(String category, LocalDate validUntil, boolean active) {
    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setRegistrationNumber("01234567890");
    cnh.setCategory(category);
    cnh.setValidUntil(validUntil);
    cnh.setActive(active);
    cnh.getReview().setStatus(DocumentStatus.APPROVED);
    return cnh;
  }

  private static DriverDocumentModel driverDocument(
      DriverDocumentType type, String number, LocalDate expiresAt) {
    DriverDocumentModel document = new DriverDocumentModel();
    document.setDocumentType(type);
    document.setDocumentNumber(number);
    document.setExpiresAt(expiresAt);
    document.setFile(new MediaFileModel());
    document.setCreatedAt(Instant.parse("2026-10-01T00:00:00Z"));
    document.getReview().setStatus(DocumentStatus.APPROVED);
    return document;
  }

  private static VehicleDocumentModel vehicleDocument(
      VehicleDocumentType type, String number, LocalDate expiresAt) {
    VehicleDocumentModel document = new VehicleDocumentModel();
    document.setDocumentType(type);
    document.setDocumentNumber(number);
    document.setExpiresAt(expiresAt);
    document.setFile(new MediaFileModel());
    document.setCreatedAt(Instant.parse("2026-10-01T00:00:00Z"));
    document.getReview().setStatus(DocumentStatus.APPROVED);
    return document;
  }

  @Test
  void aCnhSentWithOnlyItsFileLacksNoCategoryYet() {
    DriverCnhModel sent = new DriverCnhModel();
    DriverCnhModel categoryB = new DriverCnhModel();
    categoryB.setCategory("B");

    assertThat(SchoolTransportCredentialPolicy.lacksSchoolTransportCategory(sent)).isFalse();
    assertThat(SchoolTransportCredentialPolicy.lacksSchoolTransportCategory(categoryB)).isTrue();
  }
}
