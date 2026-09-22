package br.com.vanep.driver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.dto.DriverOnboardingStatusResponseDTO;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.driverdocument.enums.DocumentStatusEnum;
import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.driverservicearea.model.DriverServiceAreaModel;
import br.com.vanep.driverservicearea.repository.DriverServiceAreaRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DriverOnboardingServiceTest {

  @Mock private DriverRepository driverRepository;
  @Mock private VehicleRepository vehicleRepository;
  @Mock private DriverCnhRepository driverCnhRepository;
  @Mock private DriverDocumentRepository driverDocumentRepository;
  @Mock private DriverServiceAreaRepository driverServiceAreaRepository;
  @Mock private UserService userService;
  @Mock private MessageSource messages;

  private DriverOnboardingService service;

  private UserModel user;
  private DriverModel driver;

  @BeforeEach
  void setUp() {
    service =
        new DriverOnboardingService(
            driverRepository,
            vehicleRepository,
            driverCnhRepository,
            driverDocumentRepository,
            driverServiceAreaRepository,
            userService,
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

  @Test
  void getOnboardingStatusReturnsIncompleteWhenNothingConfigured() {
    driver.setBasePrice(BigDecimal.ZERO);
    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    when(driverServiceAreaRepository.findByDriverId(20L)).thenReturn(List.of());
    when(vehicleRepository.findByDriverId(20L)).thenReturn(List.of());
    when(driverCnhRepository.findByDriverId(20L)).thenReturn(List.of());
    when(driverDocumentRepository.findByDriverId(20L)).thenReturn(List.of());

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
    assertThat(status.documentsStep().missingTypes())
        .containsExactlyInAnyOrder(
            DocumentTypeEnum.CRLV,
            DocumentTypeEnum.VEHICLE_INSPECTION,
            DocumentTypeEnum.MUNICIPAL_AUTHORIZATION);
  }

  @Test
  void getOnboardingStatusReturnsCompleteWhenAllRequirementsMet() {
    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));

    DriverServiceAreaModel area = new DriverServiceAreaModel();
    area.setCity(new CityModel());
    when(driverServiceAreaRepository.findByDriverId(20L)).thenReturn(List.of(area));

    VehicleModel vehicle = new VehicleModel();
    vehicle.setActive(true);
    when(vehicleRepository.findByDriverId(20L)).thenReturn(List.of(vehicle));

    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setActive(true);
    cnh.setValidUntil(LocalDate.now().plusYears(2));
    when(driverCnhRepository.findByDriverId(20L)).thenReturn(List.of(cnh));

    DriverDocumentModel crlv = new DriverDocumentModel();
    crlv.setActive(true);
    crlv.setDocumentType(DocumentTypeEnum.CRLV);
    crlv.setStatus(DocumentStatusEnum.PENDING);

    DriverDocumentModel inspection = new DriverDocumentModel();
    inspection.setActive(true);
    inspection.setDocumentType(DocumentTypeEnum.VEHICLE_INSPECTION);
    inspection.setStatus(DocumentStatusEnum.PENDING);

    DriverDocumentModel municipal = new DriverDocumentModel();
    municipal.setActive(true);
    municipal.setDocumentType(DocumentTypeEnum.MUNICIPAL_AUTHORIZATION);
    municipal.setStatus(DocumentStatusEnum.PENDING);

    when(driverDocumentRepository.findByDriverId(20L))
        .thenReturn(List.of(crlv, inspection, municipal));

    DriverOnboardingStatusResponseDTO status = service.getOnboardingStatus("user-token-10");

    assertThat(status.approvalStatus()).isEqualTo(DriverApprovalStatus.PENDING);
    assertThat(status.canSubmit()).isTrue();
    assertThat(status.profileStep().completed()).isTrue();
    assertThat(status.vehicleStep().completed()).isTrue();
    assertThat(status.cnhStep().completed()).isTrue();
    assertThat(status.documentsStep().completed()).isTrue();
    assertThat(status.documentsStep().missingTypes()).isEmpty();
  }

  @Test
  void getOnboardingStatusUnderReviewCannotSubmit() {
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    Instant now = Instant.now();
    driver.setSubmittedAt(now);

    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    when(driverServiceAreaRepository.findByDriverId(20L)).thenReturn(List.of());
    when(vehicleRepository.findByDriverId(20L)).thenReturn(List.of());
    when(driverCnhRepository.findByDriverId(20L)).thenReturn(List.of());
    when(driverDocumentRepository.findByDriverId(20L)).thenReturn(List.of());

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

    user.setAddressId(55L); // Has city via address
    when(vehicleRepository.findByDriverId(20L)).thenReturn(List.of());

    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setActive(true);
    cnh.setValidUntil(LocalDate.now().plusYears(1));
    when(driverCnhRepository.findByDriverId(20L)).thenReturn(List.of(cnh));

    DriverDocumentModel crlv = new DriverDocumentModel();
    crlv.setActive(true);
    crlv.setDocumentType(DocumentTypeEnum.CRLV);
    DriverDocumentModel inspection = new DriverDocumentModel();
    inspection.setActive(true);
    inspection.setDocumentType(DocumentTypeEnum.VEHICLE_INSPECTION);
    DriverDocumentModel municipal = new DriverDocumentModel();
    municipal.setActive(true);
    municipal.setDocumentType(DocumentTypeEnum.MUNICIPAL_AUTHORIZATION);
    when(driverDocumentRepository.findByDriverId(20L))
        .thenReturn(List.of(crlv, inspection, municipal));

    assertThatThrownBy(() -> service.submitOnboarding("user-token-10"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> {
              ResponseStatusException ex = (ResponseStatusException) e;
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(ex.getReason()).contains("driver.onboarding.vehicle_required");
            });

    assertThat(driver.getApprovalStatus()).isEqualTo(DriverApprovalStatus.PENDING);
  }

  @Test
  void submitOnboardingThrowsUnprocessableEntityWhenCnhExpired() {
    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));

    user.setAddressId(55L);
    VehicleModel vehicle = new VehicleModel();
    vehicle.setActive(true);
    when(vehicleRepository.findByDriverId(20L)).thenReturn(List.of(vehicle));

    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setActive(true);
    cnh.setValidUntil(LocalDate.now().minusDays(1)); // Expired!
    when(driverCnhRepository.findByDriverId(20L)).thenReturn(List.of(cnh));

    DriverDocumentModel crlv = new DriverDocumentModel();
    crlv.setActive(true);
    crlv.setDocumentType(DocumentTypeEnum.CRLV);
    DriverDocumentModel inspection = new DriverDocumentModel();
    inspection.setActive(true);
    inspection.setDocumentType(DocumentTypeEnum.VEHICLE_INSPECTION);
    DriverDocumentModel municipal = new DriverDocumentModel();
    municipal.setActive(true);
    municipal.setDocumentType(DocumentTypeEnum.MUNICIPAL_AUTHORIZATION);
    when(driverDocumentRepository.findByDriverId(20L))
        .thenReturn(List.of(crlv, inspection, municipal));

    assertThatThrownBy(() -> service.submitOnboarding("user-token-10"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> {
              ResponseStatusException ex = (ResponseStatusException) e;
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(ex.getReason()).contains("driver.onboarding.cnh_expired");
            });
  }

  @Test
  void submitOnboardingThrowsUnprocessableEntityWhenDocumentMissing() {
    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));

    user.setAddressId(55L);
    VehicleModel vehicle = new VehicleModel();
    vehicle.setActive(true);
    when(vehicleRepository.findByDriverId(20L)).thenReturn(List.of(vehicle));

    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setActive(true);
    cnh.setValidUntil(LocalDate.now().plusYears(1));
    when(driverCnhRepository.findByDriverId(20L)).thenReturn(List.of(cnh));

    // CRLV and inspection uploaded, but MUNICIPAL_AUTHORIZATION is missing!
    DriverDocumentModel crlv = new DriverDocumentModel();
    crlv.setActive(true);
    crlv.setDocumentType(DocumentTypeEnum.CRLV);
    DriverDocumentModel inspection = new DriverDocumentModel();
    inspection.setActive(true);
    inspection.setDocumentType(DocumentTypeEnum.VEHICLE_INSPECTION);
    when(driverDocumentRepository.findByDriverId(20L)).thenReturn(List.of(crlv, inspection));

    assertThatThrownBy(() -> service.submitOnboarding("user-token-10"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> {
              ResponseStatusException ex = (ResponseStatusException) e;
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            });
  }

  @Test
  void submitOnboardingSuccessTransitionsToUnderReview() {
    driver.setApprovalStatus(DriverApprovalStatus.REJECTED);
    driver.setRejectionReason("Correção necessária");

    when(userService.requireByTokenAndType("user-token-10", UserType.DRIVER)).thenReturn(user);
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    when(driverRepository.save(any(DriverModel.class))).thenAnswer(inv -> inv.getArgument(0));

    user.setAddressId(55L);
    VehicleModel vehicle = new VehicleModel();
    vehicle.setActive(true);
    when(vehicleRepository.findByDriverId(20L)).thenReturn(List.of(vehicle));

    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setActive(true);
    cnh.setValidUntil(LocalDate.now().plusYears(1));
    when(driverCnhRepository.findByDriverId(20L)).thenReturn(List.of(cnh));

    DriverDocumentModel crlv = new DriverDocumentModel();
    crlv.setActive(true);
    crlv.setDocumentType(DocumentTypeEnum.CRLV);
    DriverDocumentModel inspection = new DriverDocumentModel();
    inspection.setActive(true);
    inspection.setDocumentType(DocumentTypeEnum.VEHICLE_INSPECTION);
    DriverDocumentModel municipal = new DriverDocumentModel();
    municipal.setActive(true);
    municipal.setDocumentType(DocumentTypeEnum.MUNICIPAL_AUTHORIZATION);
    when(driverDocumentRepository.findByDriverId(20L))
        .thenReturn(List.of(crlv, inspection, municipal));

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
}
