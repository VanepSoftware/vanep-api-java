package br.com.vanep.driver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.dto.DriverOnboardingDocumentsStepDTO;
import br.com.vanep.driver.dto.DriverOnboardingStatusResponseDTO;
import br.com.vanep.driver.dto.DriverRejectionRequestDTO;
import br.com.vanep.driver.dto.DriverResponseDTO;
import br.com.vanep.driver.dto.VehicleDocumentsStepDTO;
import br.com.vanep.driver.mapper.DriverMapper;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.drivercompliance.service.DriverCompliancePolicy;
import br.com.vanep.drivercompliance.service.DriverComplianceService;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.driverservicearea.model.DriverServiceAreaModel;
import br.com.vanep.driverservicearea.repository.DriverServiceAreaRepository;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialPolicy;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import br.com.vanep.vehicledocument.repository.VehicleDocumentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DriverOnboardingServiceTest {

  private static final DriverCompliancePolicy POLICY =
      new DriverCompliancePolicy(new SchoolTransportCredentialPolicy());

  @Mock private DriverRepository driverRepository;
  @Mock private VehicleRepository vehicleRepository;
  @Mock private DriverCnhRepository driverCnhRepository;
  @Mock private DriverDocumentRepository driverDocumentRepository;
  @Mock private VehicleDocumentRepository vehicleDocumentRepository;
  @Mock private DriverServiceAreaRepository driverServiceAreaRepository;
  @Mock private UserService userService;
  @Mock private DriverNotificationService driverNotificationService;
  @Mock private DriverMapper mapper;
  @Mock private MessageSource messages;

  private DriverOnboardingService service;

  private UserModel user;
  private DriverModel driver;

  @BeforeEach
  void setUp() {
    service =
        new DriverOnboardingService(
            driverRepository,
            driverServiceAreaRepository,
            new DriverComplianceService(
                driverCnhRepository,
                driverDocumentRepository,
                vehicleRepository,
                vehicleDocumentRepository,
                POLICY),
            POLICY,
            userService,
            driverNotificationService,
            mapper,
            messages);

    lenient()
        .when(messages.getMessage(anyString(), any(), any()))
        .thenAnswer(inv -> inv.getArgument(0));

    user = new UserModel();
    user.setId(10L);
    user.setToken("user-token-10");
    user.setType(UserType.DRIVER);

    driver = new DriverModel();
    driver.setId(20L);
    driver.setToken("driver-token-20");
    driver.setUser(user);
    driver.setApprovalStatus(DriverApprovalStatus.PENDING);
    driver.setBasePrice(BigDecimal.valueOf(150.00));
  }

  private DriverDocumentModel driverDocument(DriverDocumentType type, boolean withFile) {
    DriverDocumentModel doc = new DriverDocumentModel();
    doc.setToken("doc-token-" + type.name().toLowerCase());
    doc.setDriver(driver);
    doc.setActive(true);
    doc.setDocumentType(type);
    if (withFile) {
      doc.setFile(file(type.name()));
    }
    return doc;
  }

  private VehicleModel vehicle(long id, String plate) {
    VehicleModel vehicle = new VehicleModel();
    vehicle.setId(id);
    vehicle.setToken("vehicle-token-" + id);
    vehicle.setPlate(plate);
    vehicle.setActive(true);
    vehicle.setDriver(driver);
    return vehicle;
  }

  private VehicleDocumentModel vehicleDocument(
      VehicleModel vehicle, VehicleDocumentType type, boolean withFile) {
    VehicleDocumentModel doc = new VehicleDocumentModel();
    doc.setToken("vehicle-doc-" + vehicle.getId() + "-" + type.name().toLowerCase());
    doc.setVehicle(vehicle);
    doc.setDocumentType(type);
    if (withFile) {
      doc.setFile(file(type.name()));
    }
    return doc;
  }

  private List<DriverDocumentModel> completeDriverDocuments() {
    return List.of(
        driverDocument(DriverDocumentType.SCHOOL_DRIVER_COURSE, true),
        driverDocument(DriverDocumentType.CRIMINAL_RECORD, true));
  }

  private List<VehicleDocumentModel> completeVanDocuments(VehicleModel vehicle) {
    return List.of(
        vehicleDocument(vehicle, VehicleDocumentType.CRLV, true),
        vehicleDocument(vehicle, VehicleDocumentType.VEHICLE_INSPECTION, true),
        vehicleDocument(vehicle, VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION, true));
  }

  private DriverCnhModel cnh(String category, LocalDate validUntil) {
    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver);
    cnh.setActive(true);
    cnh.setCategory(category);
    cnh.setValidUntil(validUntil);
    cnh.setFile(file("cnh"));
    return cnh;
  }

  private void stubQualifiedDriver() {
    DriverCnhModel license = cnh("D", LocalDate.now().plusYears(2));
    license.getReview().setStatus(DocumentStatus.APPROVED);
    List<DriverDocumentModel> documents = completeDriverDocuments();
    documents.forEach(document -> document.getReview().setStatus(DocumentStatus.APPROVED));
    VehicleModel van = vehicle(1L, "ABC1D23");
    List<VehicleDocumentModel> vanDocuments = completeVanDocuments(van);
    vanDocuments.forEach(
        document -> {
          document.setExpiresAt(LocalDate.now().plusYears(1));
          document.getReview().setStatus(DocumentStatus.APPROVED);
        });
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of(license));
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(documents);
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of(van));
    when(vehicleDocumentRepository.findUnsupersededByVehicleIdIn(List.of(1L)))
        .thenReturn(vanDocuments);
  }

  private MediaFileModel file(String name) {
    MediaFileModel file = new MediaFileModel();
    file.setToken("media-token-" + name.toLowerCase());
    return file;
  }

  private void stubReadyToSubmit(VehicleModel vehicle) {
    user.setAddressId(55L);
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of(vehicle));
    DriverCnhModel cnh = cnh("D", LocalDate.now().plusYears(1));
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of(cnh));
    stubCompleteDocuments(vehicle);
  }

  private void stubCompleteDocuments(VehicleModel vehicle) {
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(completeDriverDocuments());
    when(vehicleDocumentRepository.findUnsupersededByVehicleIdIn(List.of(vehicle.getId())))
        .thenReturn(completeVanDocuments(vehicle));
  }

  private void stubDriverWithArea() {
    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    DriverServiceAreaModel area = new DriverServiceAreaModel();
    area.setCity(new CityModel());
    when(driverServiceAreaRepository.findByDriverId(20L)).thenReturn(List.of(area));
    DriverCnhModel cnh = cnh("D", LocalDate.now().plusYears(2));
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of(cnh));
  }

  @Test
  void getOnboardingStatusReturnsIncompleteWhenNothingConfigured() {
    driver.setBasePrice(BigDecimal.ZERO);
    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    when(driverServiceAreaRepository.findByDriverId(20L)).thenReturn(List.of());
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of());
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of());
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of());

    DriverOnboardingStatusResponseDTO status = service.getOnboardingStatus("user-token-10");

    assertThat(status.approvalStatus()).isEqualTo(DriverApprovalStatus.PENDING);
    assertThat(status.canSubmit()).isFalse();
    assertThat(status.profileStep().completed()).isFalse();
    assertThat(status.profileStep().pendingItems()).contains("basePrice", "city");
    assertThat(status.vehicleStep().completed()).isFalse();
    assertThat(status.vehicleStep().pendingItems()).contains("vehicle");
    assertThat(status.cnhStep().completed()).isFalse();
    assertThat(status.cnhStep().pendingItems()).contains("cnh");
    assertThat(status.documentsStep().completed()).isFalse();
    assertThat(status.documentsStep().missingDriverTypes())
        .containsExactly(
            DriverDocumentType.SCHOOL_DRIVER_COURSE, DriverDocumentType.CRIMINAL_RECORD);
    assertThat(status.documentsStep().vehicles()).isEmpty();
  }

  @Test
  void getOnboardingStatusReturnsCompleteWhenAllRequirementsMet() {
    stubDriverWithArea();
    VehicleModel van = vehicle(1L, "ABC1D23");
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of(van));
    stubCompleteDocuments(van);

    DriverOnboardingStatusResponseDTO status = service.getOnboardingStatus("user-token-10");

    assertThat(status.canSubmit()).isTrue();
    assertThat(status.documentsStep().completed()).isTrue();
    assertThat(status.documentsStep().missingDriverTypes()).isEmpty();
    assertThat(status.documentsStep().vehicles())
        .singleElement()
        .satisfies(
            vanStep -> {
              assertThat(vanStep.vehicleToken()).isEqualTo("vehicle-token-1");
              assertThat(vanStep.plate()).isEqualTo("ABC1D23");
              assertThat(vanStep.completed()).isTrue();
              assertThat(vanStep.missingTypes()).isEmpty();
              assertThat(vanStep.uploadedDocuments()).hasSize(3);
            });
  }

  @Test
  void oneCompleteVanIsEnoughWhenTheOtherHasNoDocuments() {
    stubDriverWithArea();
    VehicleModel complete = vehicle(1L, "ABC1D23");
    VehicleModel bare = vehicle(2L, "XYZ9K87");
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of(complete, bare));
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(completeDriverDocuments());
    when(vehicleDocumentRepository.findUnsupersededByVehicleIdIn(List.of(1L, 2L)))
        .thenReturn(completeVanDocuments(complete));

    DriverOnboardingStatusResponseDTO status = service.getOnboardingStatus("user-token-10");

    assertThat(status.documentsStep().completed()).isTrue();
    assertThat(status.documentsStep().vehicles())
        .extracting(vanStep -> vanStep.plate(), vanStep -> vanStep.completed())
        .containsExactly(tuple("ABC1D23", true), tuple("XYZ9K87", false));
    assertThat(status.documentsStep().vehicles().get(1).missingTypes())
        .containsExactly(
            VehicleDocumentType.CRLV,
            VehicleDocumentType.VEHICLE_INSPECTION,
            VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION);
  }

  @Test
  void theDocumentsOfAnotherVanDoNotCompleteAVan() {
    stubDriverWithArea();
    VehicleModel first = vehicle(1L, "ABC1D23");
    VehicleModel second = vehicle(2L, "XYZ9K87");
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of(first, second));
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(completeDriverDocuments());
    when(vehicleDocumentRepository.findUnsupersededByVehicleIdIn(List.of(1L, 2L)))
        .thenReturn(
            List.of(
                vehicleDocument(first, VehicleDocumentType.CRLV, true),
                vehicleDocument(second, VehicleDocumentType.VEHICLE_INSPECTION, true)));

    DriverOnboardingStatusResponseDTO status = service.getOnboardingStatus("user-token-10");

    assertThat(status.documentsStep().completed()).isFalse();
    assertThat(status.documentsStep().vehicles())
        .extracting(vanStep -> vanStep.missingTypes())
        .containsExactly(
            List.of(
                VehicleDocumentType.VEHICLE_INSPECTION,
                VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION),
            List.of(VehicleDocumentType.CRLV, VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION));
  }

  @Test
  void anInactiveVanIsNotEvaluated() {
    stubDriverWithArea();
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of());
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(completeDriverDocuments());

    DriverOnboardingStatusResponseDTO status = service.getOnboardingStatus("user-token-10");

    assertThat(status.documentsStep().completed()).isFalse();
    assertThat(status.documentsStep().vehicles()).isEmpty();
  }

  @Test
  void aRejectedVanDocumentDoesNotCount() {
    stubDriverWithArea();
    VehicleModel van = vehicle(1L, "ABC1D23");
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of(van));
    VehicleDocumentModel rejected = vehicleDocument(van, VehicleDocumentType.CRLV, true);
    rejected.getReview().setStatus(DocumentStatus.REJECTED);
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(completeDriverDocuments());
    when(vehicleDocumentRepository.findUnsupersededByVehicleIdIn(List.of(1L)))
        .thenReturn(
            List.of(
                rejected,
                vehicleDocument(van, VehicleDocumentType.VEHICLE_INSPECTION, true),
                vehicleDocument(van, VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION, true)));

    DriverOnboardingStatusResponseDTO status = service.getOnboardingStatus("user-token-10");

    assertThat(status.documentsStep().vehicles().get(0).missingTypes())
        .containsExactly(VehicleDocumentType.CRLV);
  }

  @Test
  void anExpiredDocumentDoesNotCount() {
    stubDriverWithArea();
    VehicleModel van = vehicle(1L, "ABC1D23");
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of(van));
    DriverDocumentModel expiredCourse =
        driverDocument(DriverDocumentType.SCHOOL_DRIVER_COURSE, true);
    expiredCourse.setExpiresAt(LocalDate.now().minusDays(1));
    VehicleDocumentModel expiredInspection =
        vehicleDocument(van, VehicleDocumentType.VEHICLE_INSPECTION, true);
    expiredInspection.setExpiresAt(LocalDate.now().minusDays(1));
    VehicleDocumentModel crlv = vehicleDocument(van, VehicleDocumentType.CRLV, true);
    crlv.setExpiresAt(LocalDate.now());
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(
            List.of(expiredCourse, driverDocument(DriverDocumentType.CRIMINAL_RECORD, true)));
    when(vehicleDocumentRepository.findUnsupersededByVehicleIdIn(List.of(1L)))
        .thenReturn(
            List.of(
                crlv,
                expiredInspection,
                vehicleDocument(van, VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION, true)));

    DriverOnboardingStatusResponseDTO status = service.getOnboardingStatus("user-token-10");

    assertThat(status.documentsStep().missingDriverTypes())
        .containsExactly(DriverDocumentType.SCHOOL_DRIVER_COURSE);
    assertThat(status.documentsStep().vehicles().get(0).missingTypes())
        .containsExactly(VehicleDocumentType.VEHICLE_INSPECTION);
  }

  @Test
  void getOnboardingStatusReturnsIncompleteWhenDocumentHasNoFileUploaded() {
    stubDriverWithArea();
    VehicleModel van = vehicle(1L, "ABC1D23");
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of(van));
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(completeDriverDocuments());
    when(vehicleDocumentRepository.findUnsupersededByVehicleIdIn(List.of(1L)))
        .thenReturn(
            List.of(
                vehicleDocument(van, VehicleDocumentType.CRLV, false),
                vehicleDocument(van, VehicleDocumentType.VEHICLE_INSPECTION, true),
                vehicleDocument(van, VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION, true)));

    DriverOnboardingStatusResponseDTO status = service.getOnboardingStatus("user-token-10");

    assertThat(status.canSubmit()).isFalse();
    assertThat(status.documentsStep().completed()).isFalse();
    assertThat(status.documentsStep().vehicles().get(0).missingTypes())
        .containsExactly(VehicleDocumentType.CRLV);
  }

  @Test
  void theMunicipalAuthorizationIsNotRequired() {
    stubDriverWithArea();
    VehicleModel van = vehicle(1L, "ABC1D23");
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of(van));
    stubCompleteDocuments(van);

    DriverOnboardingStatusResponseDTO status = service.getOnboardingStatus("user-token-10");

    assertThat(status.documentsStep().completed()).isTrue();
    assertThat(status.documentsStep().uploadedDocuments())
        .extracting(document -> document.documentType())
        .doesNotContain(DriverDocumentType.MUNICIPAL_AUTHORIZATION);
  }

  @Test
  void missingTheCourseLeavesTheStepIncompleteWithTheCourse() {
    stubDriverWithArea();
    VehicleModel van = vehicle(1L, "ABC1D23");
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of(van));
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of(driverDocument(DriverDocumentType.CRIMINAL_RECORD, true)));
    when(vehicleDocumentRepository.findUnsupersededByVehicleIdIn(List.of(1L)))
        .thenReturn(completeVanDocuments(van));

    DriverOnboardingStatusResponseDTO status = service.getOnboardingStatus("user-token-10");

    assertThat(status.documentsStep().completed()).isFalse();
    assertThat(status.documentsStep().missingDriverTypes())
        .containsExactly(DriverDocumentType.SCHOOL_DRIVER_COURSE);
    assertThat(status.canSubmit()).isFalse();
  }

  @Test
  void savingOnlyTheCrlvKeepsItAndListsWhatIsLeft() {
    stubDriverWithArea();
    VehicleModel van = vehicle(1L, "ABC1D23");
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of(van));
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of());
    when(vehicleDocumentRepository.findUnsupersededByVehicleIdIn(List.of(1L)))
        .thenReturn(List.of(vehicleDocument(van, VehicleDocumentType.CRLV, true)));

    DriverOnboardingStatusResponseDTO status = service.getOnboardingStatus("user-token-10");

    assertThat(status.documentsStep().completed()).isFalse();
    assertThat(status.documentsStep().missingDriverTypes())
        .containsExactly(
            DriverDocumentType.SCHOOL_DRIVER_COURSE, DriverDocumentType.CRIMINAL_RECORD);
    assertThat(status.documentsStep().vehicles().get(0).missingTypes())
        .containsExactly(
            VehicleDocumentType.VEHICLE_INSPECTION,
            VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION);
    assertThat(status.documentsStep().vehicles().get(0).uploadedDocuments())
        .extracting(document -> document.documentType())
        .containsExactly(VehicleDocumentType.CRLV);
  }

  @ParameterizedTest
  @ValueSource(strings = {"B", "AB", "C"})
  void aCnhWithoutCategoryDOrEIsReportedAsCnhCategory(String category) {
    stubDriverWithArea();
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of(cnh(category, LocalDate.now().plusYears(2))));
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of());
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of());

    DriverOnboardingStatusResponseDTO status = service.getOnboardingStatus("user-token-10");

    assertThat(status.cnhStep().completed()).isFalse();
    assertThat(status.cnhStep().pendingItems()).containsExactly("cnh_category");
  }

  @Test
  void aCnhSentWithOnlyItsFileCountsAsSent() {
    stubDriverWithArea();
    DriverCnhModel sent = cnh(null, null);
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of(sent));
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of());
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of());

    assertThat(service.getOnboardingStatus("user-token-10").cnhStep().completed()).isTrue();
  }

  @Test
  void anExpiredPendingCnhIsReportedAsExpired() {
    stubDriverWithArea();
    DriverCnhModel expired = cnh("D", LocalDate.now().minusDays(1));
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of(expired));
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of());
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of());

    assertThat(service.getOnboardingStatus("user-token-10").cnhStep().pendingItems())
        .containsExactly("cnh_expired");
  }

  @Test
  void aPendingCnhCountsAsSent() {
    stubDriverWithArea();
    DriverCnhModel pending = cnh("D", LocalDate.now().plusYears(2));
    pending.getReview().setStatus(DocumentStatus.PENDING);
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of(pending));
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of());
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of());

    assertThat(service.getOnboardingStatus("user-token-10").cnhStep().completed()).isTrue();
  }

  @Test
  void aRejectedCnhIsReportedAsCnhRejected() {
    stubDriverWithArea();
    DriverCnhModel rejected = cnh("D", LocalDate.now().plusYears(2));
    rejected.getReview().setStatus(DocumentStatus.REJECTED);
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of(rejected));
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of());
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of());

    DriverOnboardingStatusResponseDTO status = service.getOnboardingStatus("user-token-10");

    assertThat(status.cnhStep().completed()).isFalse();
    assertThat(status.cnhStep().pendingItems()).containsExactly("cnh_rejected");
  }

  @Test
  void submittingWithARejectedCnhIsRefusedWithCnhRejected() {
    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    stubReadyToSubmit(vehicle(1L, "ABC1D23"));
    DriverCnhModel rejected = cnh("D", LocalDate.now().plusYears(2));
    rejected.getReview().setStatus(DocumentStatus.REJECTED);
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of(rejected));

    assertThatThrownBy(() -> service.submitOnboarding("user-token-10"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e ->
                assertThat(((ResponseStatusException) e).getReason())
                    .contains("driver.onboarding.cnh_rejected"));
  }

  @Test
  void aCnhWithCategoryAdCompletesTheStep() {
    stubDriverWithArea();
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of(cnh("AD", LocalDate.now().plusYears(2))));
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of());
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of());

    assertThat(service.getOnboardingStatus("user-token-10").cnhStep().completed()).isTrue();
  }

  @Test
  void submittingWithACategoryBCnhIsRefusedWithCnhCategory() {
    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    stubReadyToSubmit(vehicle(1L, "ABC1D23"));
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of(cnh("B", LocalDate.now().plusYears(2))));

    assertThatThrownBy(() -> service.submitOnboarding("user-token-10"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e ->
                assertThat(((ResponseStatusException) e).getReason())
                    .contains("driver.onboarding.cnh_category"));
    assertThat(driver.getApprovalStatus()).isEqualTo(DriverApprovalStatus.PENDING);
  }

  @Test
  void submittingWithoutTheSchoolAuthorizationNamesTheTypeAndThePlate() {
    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    VehicleModel van = vehicle(1L, "ABC1D23");
    stubReadyToSubmit(van);
    when(vehicleDocumentRepository.findUnsupersededByVehicleIdIn(List.of(1L)))
        .thenReturn(
            List.of(
                vehicleDocument(van, VehicleDocumentType.CRLV, true),
                vehicleDocument(van, VehicleDocumentType.VEHICLE_INSPECTION, true)));

    assertThatThrownBy(() -> service.submitOnboarding("user-token-10"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

    verify(messages)
        .getMessage(
            eq("driver.onboarding.documents_missing"),
            argThat(args -> "ABC1D23: SCHOOL_VEHICLE_AUTHORIZATION".equals(args[0])),
            any());
    assertThat(driver.getApprovalStatus()).isEqualTo(DriverApprovalStatus.PENDING);
    verify(driverRepository, never()).save(any(DriverModel.class));
    verify(vehicleDocumentRepository, never()).save(any());
    verify(driverDocumentRepository, never()).save(any());
  }

  @Test
  void submittingWithOneCompleteVanAndAnotherWithoutDocumentsIsAccepted() {
    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    when(driverRepository.save(any(DriverModel.class))).thenAnswer(inv -> inv.getArgument(0));
    VehicleModel complete = vehicle(1L, "ABC1D23");
    VehicleModel bare = vehicle(2L, "XYZ9K87");
    user.setAddressId(55L);
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of(complete, bare));
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of(cnh("D", LocalDate.now().plusYears(1))));
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(completeDriverDocuments());
    when(vehicleDocumentRepository.findUnsupersededByVehicleIdIn(List.of(1L, 2L)))
        .thenReturn(completeVanDocuments(complete));

    assertThat(service.submitOnboarding("user-token-10").approvalStatus())
        .isEqualTo(DriverApprovalStatus.UNDER_REVIEW);
  }

  @Test
  void withACompleteVanOnlyTheDriverTypesAreMissing() {
    DriverOnboardingDocumentsStepDTO step =
        new DriverOnboardingDocumentsStepDTO(
            false,
            List.of(DriverDocumentType.CRIMINAL_RECORD),
            List.of(),
            List.of(
                new VehicleDocumentsStepDTO(
                    "vehicle-token-1", "ABC1D23", true, List.of(), List.of()),
                new VehicleDocumentsStepDTO(
                    "vehicle-token-2",
                    "XYZ9K87",
                    false,
                    List.of(VehicleDocumentType.CRLV),
                    List.of())));

    assertThat(DriverOnboardingService.describeMissingDocuments(step)).isEqualTo("CRIMINAL_RECORD");
  }

  @Test
  void theMissingDocumentsNameTheDriverTypesAndEachIncompleteVanByPlate() {
    DriverOnboardingDocumentsStepDTO step =
        new DriverOnboardingDocumentsStepDTO(
            false,
            List.of(DriverDocumentType.SCHOOL_DRIVER_COURSE),
            List.of(),
            List.of(
                new VehicleDocumentsStepDTO(
                    "vehicle-token-1",
                    "ABC1D23",
                    false,
                    List.of(VehicleDocumentType.CRLV, VehicleDocumentType.VEHICLE_INSPECTION),
                    List.of()),
                new VehicleDocumentsStepDTO(
                    "vehicle-token-2",
                    "XYZ9K87",
                    false,
                    List.of(VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION),
                    List.of())));

    assertThat(DriverOnboardingService.describeMissingDocuments(step))
        .isEqualTo(
            "SCHOOL_DRIVER_COURSE; ABC1D23: CRLV, VEHICLE_INSPECTION;"
                + " XYZ9K87: SCHOOL_VEHICLE_AUTHORIZATION");
  }

  @Test
  void getOnboardingStatusUnderReviewCannotSubmit() {
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    Instant now = Instant.now();
    driver.setSubmittedAt(now);

    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    when(driverServiceAreaRepository.findByDriverId(20L)).thenReturn(List.of());
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of());
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of());
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of());

    DriverOnboardingStatusResponseDTO status = service.getOnboardingStatus("user-token-10");

    assertThat(status.approvalStatus()).isEqualTo(DriverApprovalStatus.UNDER_REVIEW);
    assertThat(status.canSubmit()).isFalse();
    assertThat(status.submittedAt()).isEqualTo(now);
  }

  @Test
  void submitOnboardingThrowsBadRequestWhenAlreadyUnderReviewOrApproved() {
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));

    assertThatThrownBy(() -> service.submitOnboarding("user-token-10"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    assertThatThrownBy(() -> service.submitOnboarding("user-token-10"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void submitOnboardingThrowsUnprocessableEntityWhenVehicleMissing() {
    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    user.setAddressId(55L);
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of());
    DriverCnhModel cnh = cnh("D", LocalDate.now().plusYears(1));
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of(cnh));
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(completeDriverDocuments());

    assertThatThrownBy(() -> service.submitOnboarding("user-token-10"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> {
              ResponseStatusException ex = (ResponseStatusException) e;
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(ex.getReason()).contains("driver.onboarding.vehicle_required");
            });

    assertThat(driver.getApprovalStatus()).isEqualTo(DriverApprovalStatus.PENDING);
  }

  @Test
  void submitOnboardingWithoutAVanDoesNotReportAnEmptyDocumentList() {
    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    user.setAddressId(55L);
    when(vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(20L)))
        .thenReturn(List.of());
    DriverCnhModel cnh = cnh("D", LocalDate.now().plusYears(1));
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of(cnh));
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(completeDriverDocuments());

    assertThatThrownBy(() -> service.submitOnboarding("user-token-10"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> {
              ResponseStatusException ex = (ResponseStatusException) e;
              assertThat(ex.getReason()).contains("driver.onboarding.vehicle_required");
              assertThat(ex.getReason()).doesNotContain("driver.onboarding.documents_missing");
            });
  }

  @Test
  void submitOnboardingThrowsUnprocessableEntityWhenCnhExpired() {
    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    VehicleModel van = vehicle(1L, "ABC1D23");
    stubReadyToSubmit(van);
    DriverCnhModel expired = cnh("D", LocalDate.now().minusDays(1));
    expired.getReview().setStatus(DocumentStatus.APPROVED);
    when(driverCnhRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of(expired));

    assertThatThrownBy(() -> service.submitOnboarding("user-token-10"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> {
              ResponseStatusException ex = (ResponseStatusException) e;
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(ex.getReason()).contains("driver.onboarding.cnh_expired");
            });
  }

  @Test
  void submitOnboardingThrowsUnprocessableEntityWhenDocumentMissing() {
    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    VehicleModel van = vehicle(1L, "ABC1D23");
    stubReadyToSubmit(van);
    when(driverDocumentRepository.findByDriverIdInAndSupersededAtIsNull(List.of(20L)))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.submitOnboarding("user-token-10"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> {
              ResponseStatusException ex = (ResponseStatusException) e;
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(ex.getReason()).contains("driver.onboarding.documents_missing");
            });
    assertThat(driver.getApprovalStatus()).isEqualTo(DriverApprovalStatus.PENDING);
  }

  @Test
  void submitOnboardingSuccessTransitionsToUnderReview() {
    driver.setApprovalStatus(DriverApprovalStatus.REJECTED);
    driver.setRejectionReason("Correção necessária");

    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    when(driverRepository.save(any(DriverModel.class))).thenAnswer(inv -> inv.getArgument(0));
    stubReadyToSubmit(vehicle(1L, "ABC1D23"));

    DriverOnboardingStatusResponseDTO response = service.submitOnboarding("user-token-10");

    assertThat(response.approvalStatus()).isEqualTo(DriverApprovalStatus.UNDER_REVIEW);
    assertThat(response.canSubmit()).isFalse();
    assertThat(response.submittedAt()).isNotNull();
    assertThat(response.rejectionReason()).isNull();

    verify(driverRepository).save(driver);
    assertThat(driver.getApprovalStatus()).isEqualTo(DriverApprovalStatus.UNDER_REVIEW);
    assertThat(driver.getSubmittedAt()).isNotNull();
    assertThat(driver.getRejectionReason()).isNull();
  }

  @Test
  void submitOnboardingThrowsUnprocessableEntityWhenDocumentHasNoFileUploaded() {
    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    VehicleModel van = vehicle(1L, "ABC1D23");
    stubReadyToSubmit(van);
    when(vehicleDocumentRepository.findUnsupersededByVehicleIdIn(List.of(1L)))
        .thenReturn(
            List.of(
                vehicleDocument(van, VehicleDocumentType.CRLV, false),
                vehicleDocument(van, VehicleDocumentType.VEHICLE_INSPECTION, true),
                vehicleDocument(van, VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION, true)));

    assertThatThrownBy(() -> service.submitOnboarding("user-token-10"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> {
              ResponseStatusException ex = (ResponseStatusException) e;
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(ex.getReason()).contains("driver.onboarding.documents_missing");
            });
  }

  @Test
  void approveDriverSuccessTransitionsToApproved() {
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    driver.setActive(false);

    UserModel adminUser = new UserModel();
    adminUser.setId(99L);
    adminUser.setToken("admin-uid");

    when(userService.requireByToken("admin-uid")).thenReturn(adminUser);
    when(driverRepository.findByTokenForUpdate("driver-token-20")).thenReturn(Optional.of(driver));
    when(driverRepository.save(any(DriverModel.class))).thenAnswer(inv -> inv.getArgument(0));

    DriverResponseDTO expectedResponse =
        new DriverResponseDTO(
            "driver-token-20",
            "Driver",
            "d@v.com",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            BigDecimal.valueOf(150),
            null,
            null,
            null,
            null,
            DriverApprovalStatus.APPROVED,
            true,
            false,
            null,
            null);
    when(mapper.toResponse(driver)).thenReturn(expectedResponse);
    stubQualifiedDriver();

    DriverResponseDTO response = service.approve("driver-token-20", "admin-uid");

    assertThat(response).isEqualTo(expectedResponse);
    assertThat(driver.getApprovalStatus()).isEqualTo(DriverApprovalStatus.APPROVED);
    assertThat(driver.isActive()).isTrue();
    assertThat(driver.getReviewedAt()).isNotNull();
    assertThat(driver.getReviewedBy()).isEqualTo(adminUser);
    verify(driverRepository).save(driver);
    verify(driverNotificationService).notifyApproval(driver);
  }

  @Test
  void approvingADriverMakesThemAvailableForNewStudents() {
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    driver.setAvailable(false);

    when(userService.requireByToken("admin-uid")).thenReturn(new UserModel());
    when(driverRepository.findByTokenForUpdate("driver-token-20")).thenReturn(Optional.of(driver));
    when(driverRepository.save(any(DriverModel.class))).thenAnswer(inv -> inv.getArgument(0));
    stubQualifiedDriver();

    service.approve("driver-token-20", "admin-uid");

    assertThat(driver.isAvailable()).isTrue();
  }

  @Test
  void approvingWithoutAnyVanQualifiedOnTheCredentialsIsRefused() {
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    when(userService.requireByToken("admin-uid")).thenReturn(new UserModel());
    when(driverRepository.findByTokenForUpdate("driver-token-20")).thenReturn(Optional.of(driver));

    assertThatThrownBy(() -> service.approve("driver-token-20", "admin-uid"))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem -> {
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(problem.getMessageKey())
                  .isEqualTo("driver.onboarding.documents_not_approved");
              assertThat(problem.getBody().getProperties()).containsKey("blockers");
            });

    assertThat(driver.getApprovalStatus()).isEqualTo(DriverApprovalStatus.UNDER_REVIEW);
    verify(driverRepository, never()).save(any(DriverModel.class));
    verify(driverNotificationService, never()).notifyApproval(any());
  }

  @Test
  void rejectingADriverLeavesThemUnavailable() {
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    driver.setAvailable(false);

    when(userService.requireByToken("admin-uid")).thenReturn(new UserModel());
    when(driverRepository.findByTokenForUpdate("driver-token-20")).thenReturn(Optional.of(driver));
    when(driverRepository.save(any(DriverModel.class))).thenAnswer(inv -> inv.getArgument(0));

    service.reject(
        "driver-token-20", new DriverRejectionRequestDTO("Unreadable document"), "admin-uid");

    assertThat(driver.isAvailable()).isFalse();
  }

  @Test
  void approveDriverThrowsNotFoundWhenDriverMissing() {
    when(userService.requireByToken("admin-uid")).thenReturn(new UserModel());
    when(driverRepository.findByTokenForUpdate("unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.approve("unknown", "admin-uid"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void approveDriverThrowsBadRequestWhenNotUnderReview() {
    driver.setApprovalStatus(DriverApprovalStatus.PENDING);
    when(userService.requireByToken("admin-uid")).thenReturn(new UserModel());
    when(driverRepository.findByTokenForUpdate("driver-token-20")).thenReturn(Optional.of(driver));

    assertThatThrownBy(() -> service.approve("driver-token-20", "admin-uid"))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem -> {
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(problem.getMessageKey()).isEqualTo("driver.onboarding.not_under_review");
            });
  }

  @Test
  void rejectDriverSuccessTransitionsToRejectedWithReason() {
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);

    UserModel adminUser = new UserModel();
    adminUser.setId(99L);
    adminUser.setToken("admin-uid");

    when(userService.requireByToken("admin-uid")).thenReturn(adminUser);
    when(driverRepository.findByTokenForUpdate("driver-token-20")).thenReturn(Optional.of(driver));
    when(driverRepository.save(any(DriverModel.class))).thenAnswer(inv -> inv.getArgument(0));

    DriverResponseDTO expectedResponse =
        new DriverResponseDTO(
            "driver-token-20",
            "Driver",
            "d@v.com",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            BigDecimal.valueOf(150),
            null,
            null,
            null,
            null,
            DriverApprovalStatus.REJECTED,
            true,
            false,
            null,
            null);
    when(mapper.toResponse(driver)).thenReturn(expectedResponse);

    DriverRejectionRequestDTO request = new DriverRejectionRequestDTO("Foto da CNH ilegível");
    DriverResponseDTO response = service.reject("driver-token-20", request, "admin-uid");

    assertThat(response).isEqualTo(expectedResponse);
    assertThat(driver.getApprovalStatus()).isEqualTo(DriverApprovalStatus.REJECTED);
    assertThat(driver.getRejectionReason()).isEqualTo("Foto da CNH ilegível");
    assertThat(driver.getReviewedAt()).isNotNull();
    assertThat(driver.getReviewedBy()).isEqualTo(adminUser);
    verify(driverRepository).save(driver);
    verify(driverNotificationService).notifyRejection(driver, "Foto da CNH ilegível");
  }

  @Test
  void findPendingDriversReturnsMappedPage() {
    Pageable pageable = PageRequest.of(0, 10);
    DriverModel pendingDriver = new DriverModel();
    pendingDriver.setId(50L);
    pendingDriver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);

    DriverResponseDTO responseDto =
        new DriverResponseDTO(
            "pending-token",
            "Pending Driver",
            "p@v.com",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            BigDecimal.valueOf(100),
            null,
            null,
            null,
            null,
            DriverApprovalStatus.UNDER_REVIEW,
            true,
            false,
            null,
            null);

    Page<DriverModel> page = new PageImpl<>(List.of(pendingDriver), pageable, 1);
    when(driverRepository.findByApprovalStatusWithUser(DriverApprovalStatus.UNDER_REVIEW, pageable))
        .thenReturn(page);
    when(mapper.toResponse(pendingDriver)).thenReturn(responseDto);

    Page<DriverResponseDTO> result = service.findPendingDrivers(pageable);

    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent()).containsExactly(responseDto);
    verify(driverRepository)
        .findByApprovalStatusWithUser(DriverApprovalStatus.UNDER_REVIEW, pageable);
  }

  @Test
  void rejectDriverThrowsNotFoundWhenDriverMissing() {
    when(userService.requireByToken("admin-uid")).thenReturn(new UserModel());
    when(driverRepository.findByTokenForUpdate("unknown")).thenReturn(Optional.empty());

    DriverRejectionRequestDTO request = new DriverRejectionRequestDTO("Motivo qualquer");
    assertThatThrownBy(() -> service.reject("unknown", request, "admin-uid"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void rejectDriverThrowsBadRequestWhenNotUnderReview() {
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    when(userService.requireByToken("admin-uid")).thenReturn(new UserModel());
    when(driverRepository.findByTokenForUpdate("driver-token-20")).thenReturn(Optional.of(driver));

    DriverRejectionRequestDTO request = new DriverRejectionRequestDTO("Motivo qualquer");
    assertThatThrownBy(() -> service.reject("driver-token-20", request, "admin-uid"))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem -> {
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(problem.getMessageKey()).isEqualTo("driver.onboarding.not_under_review");
            });
  }
}
