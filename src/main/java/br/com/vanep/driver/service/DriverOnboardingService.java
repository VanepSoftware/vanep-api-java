package br.com.vanep.driver.service;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.dto.DriverDocumentSummaryDTO;
import br.com.vanep.driver.dto.DriverOnboardingDocumentsStepDTO;
import br.com.vanep.driver.dto.DriverOnboardingStatusResponseDTO;
import br.com.vanep.driver.dto.DriverOnboardingStepDTO;
import br.com.vanep.driver.dto.DriverRejectionRequestDTO;
import br.com.vanep.driver.dto.DriverResponseDTO;
import br.com.vanep.driver.mapper.DriverMapper;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.driverdocument.enums.DocumentStatusEnum;
import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.driverservicearea.repository.DriverServiceAreaRepository;
import br.com.vanep.media.web.MediaUrl;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DriverOnboardingService {

  private static final List<DocumentTypeEnum> MANDATORY_DOCUMENT_TYPES =
      List.of(
          DocumentTypeEnum.CRLV,
          DocumentTypeEnum.VEHICLE_INSPECTION,
          DocumentTypeEnum.MUNICIPAL_AUTHORIZATION);

  private final DriverRepository driverRepository;
  private final VehicleRepository vehicleRepository;
  private final DriverCnhRepository driverCnhRepository;
  private final DriverDocumentRepository driverDocumentRepository;
  private final DriverServiceAreaRepository driverServiceAreaRepository;
  private final UserService userService;
  private final DriverMapper mapper;
  private final MessageSource messages;

  public DriverOnboardingService(
      DriverRepository driverRepository,
      VehicleRepository vehicleRepository,
      DriverCnhRepository driverCnhRepository,
      DriverDocumentRepository driverDocumentRepository,
      DriverServiceAreaRepository driverServiceAreaRepository,
      UserService userService,
      DriverMapper mapper,
      MessageSource messages) {
    this.driverRepository = driverRepository;
    this.vehicleRepository = vehicleRepository;
    this.driverCnhRepository = driverCnhRepository;
    this.driverDocumentRepository = driverDocumentRepository;
    this.driverServiceAreaRepository = driverServiceAreaRepository;
    this.userService = userService;
    this.mapper = mapper;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Transactional(readOnly = true)
  public DriverOnboardingStatusResponseDTO getOnboardingStatus(String callerUid) {
    UserModel user = userService.requireByTokenAndType(callerUid, UserType.DRIVER);
    DriverModel driver = requireDriverByUserId(user.getId());

    DriverOnboardingStepDTO profileStep = evaluateProfileStep(driver, user);
    DriverOnboardingStepDTO vehicleStep = evaluateVehicleStep(driver);
    DriverOnboardingStepDTO cnhStep = evaluateCnhStep(driver);
    DriverOnboardingDocumentsStepDTO documentsStep = evaluateDocumentsStep(driver);

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
    DriverOnboardingStepDTO vehicleStep = evaluateVehicleStep(driver);
    DriverOnboardingStepDTO cnhStep = evaluateCnhStep(driver);
    DriverOnboardingDocumentsStepDTO documentsStep = evaluateDocumentsStep(driver);

    List<String> errors = new ArrayList<>();
    if (!profileStep.completed()) {
      errors.add(message("driver.onboarding.profile_incomplete"));
    }
    if (!vehicleStep.completed()) {
      errors.add(message("driver.onboarding.vehicle_required"));
    }
    if (!cnhStep.completed()) {
      if (cnhStep.pendingItems().contains("cnh_expired")) {
        errors.add(message("driver.onboarding.cnh_expired"));
      } else {
        errors.add(message("driver.onboarding.cnh_required"));
      }
    }
    if (!documentsStep.completed()) {
      String missing =
          documentsStep.missingTypes().stream()
              .map(DocumentTypeEnum::name)
              .collect(Collectors.joining(", "));
      errors.add(
          messages.getMessage(
              "driver.onboarding.documents_missing",
              new Object[] {missing},
              LocaleContextHolder.getLocale()));
    }

    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, String.join("; ", errors));
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

  DriverOnboardingStepDTO evaluateVehicleStep(DriverModel driver) {
    List<VehicleModel> vehicles = vehicleRepository.findByDriverId(driver.getId());
    boolean hasActiveVehicle = vehicles.stream().anyMatch(VehicleModel::isActive);
    List<String> pending = new ArrayList<>();
    if (!hasActiveVehicle) {
      pending.add("vehicle");
    }
    return new DriverOnboardingStepDTO(pending.isEmpty(), pending);
  }

  DriverOnboardingStepDTO evaluateCnhStep(DriverModel driver) {
    List<DriverCnhModel> cnhs = driverCnhRepository.findByDriverId(driver.getId());
    List<DriverCnhModel> activeCnhs = cnhs.stream().filter(DriverCnhModel::isActive).toList();
    List<String> pending = new ArrayList<>();
    if (activeCnhs.isEmpty()) {
      pending.add("cnh");
    } else {
      boolean hasNonExpired =
          activeCnhs.stream()
              .anyMatch(
                  cnh ->
                      cnh.getValidUntil() != null
                          && !cnh.getValidUntil().isBefore(LocalDate.now()));
      if (!hasNonExpired) {
        pending.add("cnh_expired");
      }
    }
    return new DriverOnboardingStepDTO(pending.isEmpty(), pending);
  }

  DriverOnboardingDocumentsStepDTO evaluateDocumentsStep(DriverModel driver) {
    List<DriverDocumentModel> documents = driverDocumentRepository.findByDriverId(driver.getId());
    List<DriverDocumentSummaryDTO> summaries =
        documents.stream().map(this::toDocumentSummary).toList();

    List<DocumentTypeEnum> missingTypes = new ArrayList<>();
    for (DocumentTypeEnum requiredType : MANDATORY_DOCUMENT_TYPES) {
      boolean present =
          documents.stream()
              .anyMatch(
                  doc ->
                      doc.isActive()
                          && doc.getDocumentType() == requiredType
                          && doc.getStatus() != DocumentStatusEnum.REJECTED);
      if (!present) {
        missingTypes.add(requiredType);
      }
    }

    return new DriverOnboardingDocumentsStepDTO(missingTypes.isEmpty(), missingTypes, summaries);
  }

  private DriverDocumentSummaryDTO toDocumentSummary(DriverDocumentModel doc) {
    return new DriverDocumentSummaryDTO(
        doc.getToken(),
        doc.getDocumentType(),
        doc.getStatus(),
        doc.getRejectionReason(),
        MediaUrl.of("/api/driver-documents", doc.getToken(), "file", doc.getFile()));
  }

  @Transactional
  public DriverResponseDTO approve(String driverToken, String adminUid) {
    UserModel adminUser = userService.requireByToken(adminUid);
    DriverModel driver = requireDriverByToken(driverToken);

    if (driver.getApprovalStatus() != DriverApprovalStatus.UNDER_REVIEW) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, message("driver.onboarding.not_under_review"));
    }

    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    driver.setActive(true);
    driver.setReviewedAt(Instant.now());
    driver.setReviewedBy(adminUser);
    DriverModel saved = driverRepository.save(driver);

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
