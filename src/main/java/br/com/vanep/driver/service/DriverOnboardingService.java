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
import br.com.vanep.driver.enums.DriverRejectionReason;
import br.com.vanep.driver.event.DriverReviewEvent;
import br.com.vanep.driver.mapper.DriverMapper;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO.RequirementDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO.VehicleDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceInputDTO;
import br.com.vanep.drivercompliance.enums.ComplianceState;
import br.com.vanep.drivercompliance.service.DriverCompliancePolicy;
import br.com.vanep.drivercompliance.service.DriverComplianceService;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.mapper.DriverDocumentMapper;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverservicearea.repository.DriverServiceAreaRepository;
import br.com.vanep.media.web.MediaUrl;
import br.com.vanep.schooltransportcredential.enums.CredentialRequirement;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialPolicy;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import br.com.vanep.vehicledocument.mapper.VehicleDocumentMapper;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
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

  private final DriverRepository driverRepository;
  private final DriverServiceAreaRepository driverServiceAreaRepository;
  private final DriverComplianceService complianceService;
  private final DriverCompliancePolicy compliancePolicy;
  private final UserService userService;
  private final ApplicationEventPublisher events;
  private final DriverMapper mapper;
  private final MessageSource messages;

  public DriverOnboardingService(
      DriverRepository driverRepository,
      DriverServiceAreaRepository driverServiceAreaRepository,
      DriverComplianceService complianceService,
      DriverCompliancePolicy compliancePolicy,
      UserService userService,
      ApplicationEventPublisher events,
      DriverMapper mapper,
      MessageSource messages) {
    this.driverRepository = driverRepository;
    this.driverServiceAreaRepository = driverServiceAreaRepository;
    this.complianceService = complianceService;
    this.compliancePolicy = compliancePolicy;
    this.userService = userService;
    this.events = events;
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
    DriverComplianceInputDTO input = complianceService.loadInput(driver);
    DriverComplianceDTO compliance =
        compliancePolicy.evaluate(input, SchoolTransportCredentialService.today());
    DriverOnboardingStepDTO vehicleStep = evaluateVehicleStep(input);
    DriverOnboardingStepDTO cnhStep = evaluateCnhStep(compliance);
    DriverOnboardingDocumentsStepDTO documentsStep = evaluateDocumentsStep(input, compliance);

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
        describeRejection(driver),
        driver.getRejectionReasonCode(),
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
    DriverComplianceInputDTO input = complianceService.loadInput(driver);
    DriverComplianceDTO compliance =
        compliancePolicy.evaluate(input, SchoolTransportCredentialService.today());
    DriverOnboardingStepDTO vehicleStep = evaluateVehicleStep(input);
    DriverOnboardingStepDTO cnhStep = evaluateCnhStep(compliance);
    DriverOnboardingDocumentsStepDTO documentsStep = evaluateDocumentsStep(input, compliance);

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
    driver.setRejectionReasonCode(null);
    DriverModel savedDriver = driverRepository.save(driver);

    return new DriverOnboardingStatusResponseDTO(
        savedDriver.getToken(),
        savedDriver.getApprovalStatus(),
        false,
        savedDriver.getSubmittedAt(),
        null,
        null,
        profileStep,
        vehicleStep,
        cnhStep,
        documentsStep);
  }

  // A rejection with a code and no message still tells the driver why, in the code's words.
  String describeRejection(DriverModel driver) {
    if (driver.getRejectionReason() != null || driver.getRejectionReasonCode() == null) {
      return driver.getRejectionReason();
    }
    return message("driver.rejection_reason." + driver.getRejectionReasonCode().name());
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

  DriverOnboardingStepDTO evaluateVehicleStep(DriverComplianceInputDTO input) {
    return new DriverOnboardingStepDTO(
        !input.vehicles().isEmpty(), input.vehicles().isEmpty() ? List.of("vehicle") : List.of());
  }

  DriverOnboardingStepDTO evaluateCnhStep(DriverComplianceDTO compliance) {
    RequirementDTO license = compliance.driverRequirement(CredentialRequirement.DRIVER_LICENSE);
    String pendingItem =
        switch (license.state()) {
          case MISSING -> "cnh";
          case REJECTED -> "cnh_rejected";
          case EXPIRED -> "cnh_expired";
          case APPROVED ->
              SchoolTransportCredentialPolicy.lacksSchoolTransportCategory(license.current())
                  ? "cnh_category"
                  : null;
          case PENDING ->
              SchoolTransportCredentialPolicy.lacksSchoolTransportCategory(license.current())
                  ? "cnh_category"
                  : null;
        };
    return new DriverOnboardingStepDTO(
        pendingItem == null, pendingItem == null ? List.of() : List.of(pendingItem));
  }

  DriverOnboardingDocumentsStepDTO evaluateDocumentsStep(
      DriverComplianceInputDTO input, DriverComplianceDTO compliance) {
    List<DriverDocumentType> missingDriverTypes =
        compliance.driverRequirements().stream()
            .filter(
                entry ->
                    SchoolTransportCredentialPolicy.DRIVER_DOCUMENT_TYPES.containsKey(
                        entry.requirement()))
            .filter(entry -> !entry.optional() && !isSent(entry))
            .map(
                entry ->
                    SchoolTransportCredentialPolicy.DRIVER_DOCUMENT_TYPES.get(entry.requirement()))
            .toList();
    Map<Long, List<VehicleDocumentModel>> documentsByVehicle =
        input.vehicles().stream()
            .collect(
                Collectors.toMap(
                    vehicle -> vehicle.vehicle().getId(), vehicle -> vehicle.documents()));
    List<VehicleDocumentsStepDTO> vehicleSteps =
        compliance.vehicles().stream()
            .map(
                vehicle ->
                    evaluateDocumentsOf(vehicle, documentsByVehicle.get(vehicle.vehicle().getId())))
            .toList();
    boolean completed =
        missingDriverTypes.isEmpty()
            && vehicleSteps.stream().anyMatch(vehicleStep -> vehicleStep.completed());

    return new DriverOnboardingDocumentsStepDTO(
        completed,
        missingDriverTypes,
        input.driverDocuments().stream().map(doc -> toDocumentSummary(doc)).toList(),
        vehicleSteps);
  }

  VehicleDocumentsStepDTO evaluateDocumentsOf(
      VehicleDTO vehicle, List<VehicleDocumentModel> documents) {
    List<VehicleDocumentType> missingTypes =
        vehicle.requirements().stream()
            .filter(entry -> !isSent(entry))
            .map(
                entry ->
                    SchoolTransportCredentialPolicy.VEHICLE_DOCUMENT_TYPES.get(entry.requirement()))
            .toList();
    return new VehicleDocumentsStepDTO(
        vehicle.vehicle().getToken(),
        vehicle.vehicle().getPlate(),
        missingTypes.isEmpty(),
        missingTypes,
        documents.stream().map(doc -> toDocumentSummary(doc)).toList());
  }

  // Sent: approved, or waiting for review with its file.
  static boolean isSent(RequirementDTO entry) {
    return entry.state() == ComplianceState.APPROVED
        || (entry.state() == ComplianceState.PENDING && entry.current().getReviewedFile() != null);
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
        MediaUrl.of(DriverDocumentMapper.BASE_PATH, doc.getToken(), "file", doc.getFile()));
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
    DriverModel driver = requireDriverForDecision(driverToken);
    DriverComplianceDTO compliance =
        complianceService.evaluate(driver, SchoolTransportCredentialService.today());
    if (!compliance.canApprove()) {
      throw ApiProblemException.unprocessable("driver.onboarding.documents_not_approved")
          .withProperty("blockers", compliance.blockers());
    }

    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    driver.setActive(true);
    driver.setAvailable(true);
    driver.setReviewedAt(Instant.now());
    driver.setReviewedBy(adminUser);
    DriverModel saved = driverRepository.save(driver);

    events.publishEvent(new DriverReviewEvent.Approved(saved.getToken()));

    return mapper.toResponse(saved);
  }

  @Transactional
  public DriverResponseDTO reject(
      String driverToken, DriverRejectionRequestDTO request, String adminUid) {
    UserModel adminUser = userService.requireByToken(adminUid);
    if (request.reasonCode() == DriverRejectionReason.OTHER
        && (request.reason() == null || request.reason().isBlank())) {
      throw ApiProblemException.badRequest("driver.rejection.reason.required");
    }
    DriverModel driver = requireDriverForDecision(driverToken);

    driver.setApprovalStatus(DriverApprovalStatus.REJECTED);
    driver.setRejectionReasonCode(request.reasonCode());
    driver.setRejectionReason(
        request.reason() == null || request.reason().isBlank() ? null : request.reason().strip());
    driver.setReviewedAt(Instant.now());
    driver.setReviewedBy(adminUser);
    DriverModel saved = driverRepository.save(driver);

    events.publishEvent(new DriverReviewEvent.Rejected(saved.getToken()));

    return mapper.toResponse(saved);
  }

  // The lock queues a concurrent approval and rejection, and the second sees the first's status.
  DriverModel requireDriverForDecision(String driverToken) {
    DriverModel driver =
        driverRepository
            .findByTokenForUpdate(driverToken)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, message("driver.not_found")));
    if (driver.getApprovalStatus() != DriverApprovalStatus.UNDER_REVIEW) {
      throw ApiProblemException.badRequest("driver.onboarding.not_under_review");
    }
    return driver;
  }

  private DriverModel requireDriverByUserId(Long userId) {
    return driverRepository
        .findByUserId(userId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("user.driver_profile.not_found")));
  }
}
