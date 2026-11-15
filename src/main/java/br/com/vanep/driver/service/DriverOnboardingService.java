package br.com.vanep.driver.service;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.dto.DriverDocumentSummaryDTO;
import br.com.vanep.driver.dto.DriverOnboardingDocumentsStepDTO;
import br.com.vanep.driver.dto.DriverOnboardingStatusResponseDTO;
import br.com.vanep.driver.dto.DriverOnboardingStepDTO;
import br.com.vanep.driver.dto.DriverRejectionRequestDTO;
import br.com.vanep.driver.dto.DriverResponseDTO;
import br.com.vanep.driver.dto.VehicleDocumentSummaryDTO;
import br.com.vanep.driver.dto.VehicleDocumentsStepDTO;
import br.com.vanep.driver.mapper.DriverMapper;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.driverservicearea.repository.DriverServiceAreaRepository;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.web.MediaUrl;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialPolicy;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import br.com.vanep.vehicledocument.mapper.VehicleDocumentMapper;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import br.com.vanep.vehicledocument.repository.VehicleDocumentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DriverOnboardingService {

  private static final List<DriverDocumentType> MANDATORY_DRIVER_DOCUMENT_TYPES =
      List.of(DriverDocumentType.SCHOOL_DRIVER_COURSE, DriverDocumentType.CRIMINAL_RECORD);

  private static final List<VehicleDocumentType> MANDATORY_VEHICLE_DOCUMENT_TYPES =
      List.of(
          VehicleDocumentType.CRLV,
          VehicleDocumentType.VEHICLE_INSPECTION,
          VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION);

  private final DriverRepository driverRepository;
  private final VehicleRepository vehicleRepository;
  private final DriverCnhRepository driverCnhRepository;
  private final DriverDocumentRepository driverDocumentRepository;
  private final VehicleDocumentRepository vehicleDocumentRepository;
  private final DriverServiceAreaRepository driverServiceAreaRepository;
  private final SchoolTransportCredentialService credentialService;
  private final UserService userService;
  private final DriverNotificationService driverNotificationService;
  private final DriverMapper mapper;
  private final MessageSource messages;

  public DriverOnboardingService(
      DriverRepository driverRepository,
      VehicleRepository vehicleRepository,
      DriverCnhRepository driverCnhRepository,
      DriverDocumentRepository driverDocumentRepository,
      VehicleDocumentRepository vehicleDocumentRepository,
      DriverServiceAreaRepository driverServiceAreaRepository,
      SchoolTransportCredentialService credentialService,
      UserService userService,
      DriverNotificationService driverNotificationService,
      DriverMapper mapper,
      MessageSource messages) {
    this.driverRepository = driverRepository;
    this.vehicleRepository = vehicleRepository;
    this.driverCnhRepository = driverCnhRepository;
    this.driverDocumentRepository = driverDocumentRepository;
    this.vehicleDocumentRepository = vehicleDocumentRepository;
    this.driverServiceAreaRepository = driverServiceAreaRepository;
    this.credentialService = credentialService;
    this.userService = userService;
    this.driverNotificationService = driverNotificationService;
    this.mapper = mapper;
    this.messages = messages;
  }

  @Transactional(readOnly = true)
  public Page<DriverResponseDTO> findPendingDrivers(Pageable pageable) {
    return driverRepository
        .findByApprovalStatusWithUser(DriverApprovalStatus.UNDER_REVIEW, pageable)
        .map(mapper::toResponse);
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Transactional(readOnly = true)
  public DriverOnboardingStatusResponseDTO getOnboardingStatus(String callerUid) {
    UserModel user = userService.requireByTokenAndType(callerUid, UserType.DRIVER);
    DriverModel driver = requireDriverByUserId(user.getId());

    DriverOnboardingStepDTO profileStep = evaluateProfileStep(driver, user);
    List<VehicleModel> vehicles = vehicleRepository.findByDriverId(driver.getId());
    DriverOnboardingStepDTO vehicleStep = evaluateVehicleStep(vehicles);
    DriverOnboardingStepDTO cnhStep = evaluateCnhStep(driver);
    DriverOnboardingDocumentsStepDTO documentsStep = evaluateDocumentsStep(driver, vehicles);

    boolean allStepsCompleted =
        profileStep.completed()
            && vehicleStep.completed()
            && cnhStep.completed()
            && documentsStep.completed();

    boolean canSubmit =
        allStepsCompleted
            && (driver.getApprovalStatus() == DriverApprovalStatus.PENDING
                || driver.getApprovalStatus() == DriverApprovalStatus.REJECTED);

    return new DriverOnboardingStatusResponseDTO(
        driver.getToken(),
        driver.getApprovalStatus(),
        canSubmit,
        driver.getSubmittedAt(),
        driver.getRejectionReason(),
        profileStep,
        vehicleStep,
        cnhStep,
        documentsStep);
  }

  @Transactional
  public DriverOnboardingStatusResponseDTO submitOnboarding(String callerUid) {
    UserModel user = userService.requireByTokenAndType(callerUid, UserType.DRIVER);
    DriverModel driver = requireDriverByUserId(user.getId());

    if (driver.getApprovalStatus() == DriverApprovalStatus.UNDER_REVIEW
        || driver.getApprovalStatus() == DriverApprovalStatus.APPROVED) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, message("driver.onboarding.already_submitted"));
    }

    DriverOnboardingStepDTO profileStep = evaluateProfileStep(driver, user);
    List<VehicleModel> vehicles = vehicleRepository.findByDriverId(driver.getId());
    DriverOnboardingStepDTO vehicleStep = evaluateVehicleStep(vehicles);
    DriverOnboardingStepDTO cnhStep = evaluateCnhStep(driver);
    DriverOnboardingDocumentsStepDTO documentsStep = evaluateDocumentsStep(driver, vehicles);

    List<String> errors = new ArrayList<>();
    if (!profileStep.completed()) {
      errors.add(message("driver.onboarding.profile_incomplete"));
    }
    if (!vehicleStep.completed()) {
      errors.add(message("driver.onboarding.vehicle_required"));
    }
    if (!cnhStep.completed()) {
      if (cnhStep.pendingItems().contains("cnh_rejected")) {
        errors.add(message("driver.onboarding.cnh_rejected"));
      } else if (cnhStep.pendingItems().contains("cnh_expired")) {
        errors.add(message("driver.onboarding.cnh_expired"));
      } else if (cnhStep.pendingItems().contains("cnh_category")) {
        errors.add(message("driver.onboarding.cnh_category"));
      } else if (cnhStep.pendingItems().contains("cnh_file")) {
        errors.add(message("driver.onboarding.cnh_file"));
      } else {
        errors.add(message("driver.onboarding.cnh_required"));
      }
    }
    String missingDocuments = describeMissingDocuments(documentsStep);
    if (!documentsStep.completed() && !missingDocuments.isEmpty()) {
      errors.add(
          messages.getMessage(
              "driver.onboarding.documents_missing",
              new Object[] {missingDocuments},
              LocaleContextHolder.getLocale()));
    }

    if (!errors.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_CONTENT, String.join("; ", errors));
    }

    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    driver.setSubmittedAt(Instant.now());
    driver.setRejectionReason(null);
    DriverModel savedDriver = driverRepository.save(driver);

    return new DriverOnboardingStatusResponseDTO(
        savedDriver.getToken(),
        savedDriver.getApprovalStatus(),
        false,
        savedDriver.getSubmittedAt(),
        savedDriver.getRejectionReason(),
        profileStep,
        vehicleStep,
        cnhStep,
        documentsStep);
  }

  DriverOnboardingStepDTO evaluateProfileStep(DriverModel driver, UserModel user) {
    List<String> pending = new ArrayList<>();
    boolean hasBasePrice =
        driver.getBasePrice() != null && driver.getBasePrice().compareTo(BigDecimal.ZERO) > 0;
    if (!hasBasePrice) {
      pending.add("basePrice");
    }

    boolean hasCity =
        !driverServiceAreaRepository.findByDriverId(driver.getId()).isEmpty()
            || user.getAddressId() != null;
    if (!hasCity) {
      pending.add("city");
    }

    return new DriverOnboardingStepDTO(pending.isEmpty(), pending);
  }

  DriverOnboardingStepDTO evaluateVehicleStep(List<VehicleModel> vehicles) {
    boolean hasActiveVehicle = vehicles.stream().anyMatch(vehicle -> vehicle.isActive());
    List<String> pending = new ArrayList<>();
    if (!hasActiveVehicle) {
      pending.add("vehicle");
    }
    return new DriverOnboardingStepDTO(pending.isEmpty(), pending);
  }

  DriverOnboardingStepDTO evaluateCnhStep(DriverModel driver) {
    List<DriverCnhModel> cnhs =
        driverCnhRepository.findByDriverIdAndSupersededAtIsNull(driver.getId());
    List<DriverCnhModel> activeCnhs =
        cnhs.stream()
            .filter(cnh -> cnh.isActive() && cnh.getReview().getStatus() != DocumentStatus.REJECTED)
            .toList();
    List<String> pending = new ArrayList<>();
    if (activeCnhs.isEmpty()) {
      pending.add(cnhs.stream().anyMatch(cnh -> cnh.isActive()) ? "cnh_rejected" : "cnh");
    } else {
      // A CNH sent with only its file has no expiry nor category until the reviewer approves it.
      LocalDate today = SchoolTransportCredentialService.today();
      List<DriverCnhModel> validCnhs =
          activeCnhs.stream()
              .filter(cnh -> cnh.getValidUntil() == null || !cnh.getValidUntil().isBefore(today))
              .toList();
      boolean qualified =
          validCnhs.stream()
              .anyMatch(
                  cnh ->
                      cnh.getCategory() == null
                          || SchoolTransportCredentialPolicy.hasSchoolTransportCategory(
                              cnh.getCategory()));
      if (validCnhs.isEmpty()) {
        pending.add("cnh_expired");
      } else if (!qualified) {
        pending.add("cnh_category");
      }
    }
    return new DriverOnboardingStepDTO(pending.isEmpty(), pending);
  }

  DriverOnboardingDocumentsStepDTO evaluateDocumentsStep(
      DriverModel driver, List<VehicleModel> vehicles) {
    LocalDate today = SchoolTransportCredentialService.today();
    List<DriverDocumentModel> driverDocuments =
        driverDocumentRepository.findByDriverIdAndSupersededAtIsNull(driver.getId());
    List<DriverDocumentType> missingDriverTypes =
        MANDATORY_DRIVER_DOCUMENT_TYPES.stream()
            .filter(
                type -> driverDocuments.stream().noneMatch(doc -> isSubmitted(doc, type, today)))
            .toList();

    List<VehicleDocumentsStepDTO> vehicleSteps = evaluateVehicleDocuments(vehicles, today);
    boolean completed =
        missingDriverTypes.isEmpty()
            && vehicleSteps.stream().anyMatch(vehicleStep -> vehicleStep.completed());

    return new DriverOnboardingDocumentsStepDTO(
        completed,
        missingDriverTypes,
        driverDocuments.stream().map(doc -> toDocumentSummary(doc)).toList(),
        vehicleSteps);
  }

  List<VehicleDocumentsStepDTO> evaluateVehicleDocuments(
      List<VehicleModel> vehicles, LocalDate today) {
    List<VehicleModel> activeVehicles =
        vehicles.stream().filter(vehicle -> vehicle.isActive()).toList();
    if (activeVehicles.isEmpty()) {
      return List.of();
    }
    Map<Long, List<VehicleDocumentModel>> documentsByVehicle =
        vehicleDocumentRepository
            .findUnsupersededByVehicleIdIn(
                activeVehicles.stream().map(vehicle -> vehicle.getId()).toList())
            .stream()
            .collect(Collectors.groupingBy(doc -> doc.getVehicle().getId()));
    return activeVehicles.stream()
        .map(
            vehicle ->
                evaluateDocumentsOf(
                    vehicle, documentsByVehicle.getOrDefault(vehicle.getId(), List.of()), today))
        .toList();
  }

  VehicleDocumentsStepDTO evaluateDocumentsOf(
      VehicleModel vehicle, List<VehicleDocumentModel> documents, LocalDate today) {
    List<VehicleDocumentType> missingTypes =
        MANDATORY_VEHICLE_DOCUMENT_TYPES.stream()
            .filter(type -> documents.stream().noneMatch(doc -> isSubmitted(doc, type, today)))
            .toList();
    return new VehicleDocumentsStepDTO(
        vehicle.getToken(),
        vehicle.getPlate(),
        missingTypes.isEmpty(),
        missingTypes,
        documents.stream().map(doc -> toDocumentSummary(doc)).toList());
  }

  static boolean isSubmitted(
      DriverDocumentModel document, DriverDocumentType type, LocalDate today) {
    return document.isActive()
        && document.getDocumentType() == type
        && isValidSubmission(
            document.getReview(), document.getFile(), document.getExpiresAt(), today);
  }

  static boolean isSubmitted(
      VehicleDocumentModel document, VehicleDocumentType type, LocalDate today) {
    return document.getDocumentType() == type
        && isValidSubmission(
            document.getReview(), document.getFile(), document.getExpiresAt(), today);
  }

  private static boolean isValidSubmission(
      DocumentReview review, MediaFileModel file, LocalDate expiresAt, LocalDate today) {
    return review.getStatus() != DocumentStatus.REJECTED
        && file != null
        && (expiresAt == null || !expiresAt.isBefore(today));
  }

  static String describeMissingDocuments(DriverOnboardingDocumentsStepDTO documentsStep) {
    List<String> parts = new ArrayList<>();
    if (!documentsStep.missingDriverTypes().isEmpty()) {
      parts.add(joinNames(documentsStep.missingDriverTypes()));
    }
    boolean hasCompleteVehicle =
        documentsStep.vehicles().stream().anyMatch(vehicleStep -> vehicleStep.completed());
    documentsStep.vehicles().stream()
        .filter(vehicleStep -> !hasCompleteVehicle && !vehicleStep.completed())
        .forEach(
            vehicleStep ->
                parts.add(vehicleStep.plate() + ": " + joinNames(vehicleStep.missingTypes())));
    return String.join("; ", parts);
  }

  static String joinNames(List<? extends Enum<?>> types) {
    return types.stream().map(type -> type.name()).collect(Collectors.joining(", "));
  }

  DriverDocumentSummaryDTO toDocumentSummary(DriverDocumentModel doc) {
    return new DriverDocumentSummaryDTO(
        doc.getToken(),
        doc.getDocumentType(),
        doc.getReview().getStatus(),
        doc.getReview().getRejectionReason(),
        MediaUrl.of("/api/driver-documents", doc.getToken(), "file", doc.getFile()));
  }

  VehicleDocumentSummaryDTO toDocumentSummary(VehicleDocumentModel doc) {
    return new VehicleDocumentSummaryDTO(
        doc.getToken(),
        doc.getDocumentType(),
        doc.getReview().getStatus(),
        doc.getReview().getRejectionReason(),
        MediaUrl.of(VehicleDocumentMapper.BASE_PATH, doc.getToken(), "file", doc.getFile()));
  }

  @Transactional
  public DriverResponseDTO approve(String driverToken, String adminUid) {
    UserModel adminUser = userService.requireByToken(adminUid);
    DriverModel driver = requireDriverByToken(driverToken);

    if (driver.getApprovalStatus() != DriverApprovalStatus.UNDER_REVIEW) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, message("driver.onboarding.not_under_review"));
    }
    if (!credentialService.hasAnyQualifiedVehicle(
        driver.getId(), SchoolTransportCredentialService.today())) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_CONTENT, message("driver.onboarding.documents_not_approved"));
    }

    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    driver.setActive(true);
    driver.setAvailable(true);
    driver.setReviewedAt(Instant.now());
    driver.setReviewedBy(adminUser);
    DriverModel saved = driverRepository.save(driver);

    driverNotificationService.notifyApproval(saved);

    return mapper.toResponse(saved);
  }

  @Transactional
  public DriverResponseDTO reject(
      String driverToken, DriverRejectionRequestDTO request, String adminUid) {
    UserModel adminUser = userService.requireByToken(adminUid);
    DriverModel driver = requireDriverByToken(driverToken);

    if (driver.getApprovalStatus() != DriverApprovalStatus.UNDER_REVIEW) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, message("driver.onboarding.not_under_review"));
    }

    driver.setApprovalStatus(DriverApprovalStatus.REJECTED);
    driver.setRejectionReason(request.reason());
    driver.setReviewedAt(Instant.now());
    driver.setReviewedBy(adminUser);
    DriverModel saved = driverRepository.save(driver);

    driverNotificationService.notifyRejection(saved, request.reason());

    return mapper.toResponse(saved);
  }

  private DriverModel requireDriverByUserId(Long userId) {
    return driverRepository
        .findByUserId(userId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("user.driver_profile.not_found")));
  }

  private DriverModel requireDriverByToken(String token) {
    return driverRepository
        .findByToken(token)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("driver.not_found")));
  }
}
