package br.com.vanep.drivercompliance.service;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO.RequirementDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO.VehicleDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDetailResponseDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDetailResponseDTO.HistoryItemResponse;
import br.com.vanep.drivercompliance.dto.DriverComplianceDetailResponseDTO.ReviewQueueItemResponse;
import br.com.vanep.drivercompliance.dto.DriverComplianceDetailResponseDTO.VehicleResponse;
import br.com.vanep.drivercompliance.dto.DriverComplianceInputDTO;
import br.com.vanep.drivercompliance.enums.HistoryKind;
import br.com.vanep.drivercompliance.mapper.DriverComplianceDetailMapper;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.schooltransportcredential.enums.CredentialRequirement;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialPolicy;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.model.DocumentSubmission;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import br.com.vanep.vehicledocument.repository.VehicleDocumentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverComplianceDetailService {

  static final int HISTORY_LIMIT = 50;

  private static final Comparator<DocumentSubmission> LATEST_DECIDED =
      Comparator.comparing(
              (DocumentSubmission submission) -> submission.getReview().getReviewedAt(),
              Comparator.nullsFirst(Comparator.<Instant>naturalOrder()))
          .thenComparing(
              submission -> submission.getCreatedAt(),
              Comparator.nullsFirst(Comparator.<Instant>naturalOrder()));
  private static final Comparator<HistoryItemResponse> NEWEST_FIRST_DECISIONS_BEFORE_SENDINGS =
      Comparator.comparing(
              (HistoryItemResponse item) -> item.at(),
              Comparator.nullsLast(Comparator.<Instant>reverseOrder()))
          .thenComparing(item -> isSending(item.kind()))
          .thenComparing(item -> item.kind());

  private final DriverRepository drivers;
  private final DriverCnhRepository cnhs;
  private final DriverDocumentRepository driverDocuments;
  private final VehicleDocumentRepository vehicleDocuments;
  private final DriverComplianceService complianceService;
  private final DriverCompliancePolicy policy;
  private final DriverComplianceDetailMapper mapper;
  private final UserRepository users;

  public DriverComplianceDetailService(
      DriverRepository drivers,
      DriverCnhRepository cnhs,
      DriverDocumentRepository driverDocuments,
      VehicleDocumentRepository vehicleDocuments,
      DriverComplianceService complianceService,
      DriverCompliancePolicy policy,
      DriverComplianceDetailMapper mapper,
      UserRepository users) {
    this.drivers = drivers;
    this.cnhs = cnhs;
    this.driverDocuments = driverDocuments;
    this.vehicleDocuments = vehicleDocuments;
    this.complianceService = complianceService;
    this.policy = policy;
    this.mapper = mapper;
    this.users = users;
  }

  @Transactional(readOnly = true)
  public DriverComplianceDetailResponseDTO findByDriverToken(String driverToken) {
    DriverModel driver =
        drivers
            .findWithReviewerByToken(driverToken)
            .orElseThrow(() -> ApiProblemException.notFound("driver.not_found"));
    LocalDate today = SchoolTransportCredentialService.today();
    DriverComplianceInputDTO input = complianceService.loadInput(driver);
    DriverComplianceDTO compliance = policy.evaluate(input, today);
    Submissions submissions = loadSubmissions(driver, input);
    Map<Long, UserModel> correctors = loadCorrectors(submissions);
    String city =
        drivers.findCitiesByDriverIdIn(List.of(driver.getId())).stream()
            .findFirst()
            .map(found -> found.getCityName())
            .orElse(null);

    return new DriverComplianceDetailResponseDTO(
        today,
        mapper.toDriver(driver, city),
        mapper.toDecision(compliance),
        compliance.driverRequirements().stream()
            .map(
                entry ->
                    mapper.toRequirement(entry, lastDecided(entry, null, submissions), correctors))
            .toList(),
        compliance.vehicles().stream()
            .map(vehicle -> toVehicle(vehicle, submissions, correctors))
            .toList(),
        reviewQueue(compliance),
        history(driver, submissions, correctors));
  }

  // Who corrected the data is kept as an id (D25): the names come in one query for the whole page.
  Map<Long, UserModel> loadCorrectors(Submissions submissions) {
    Set<Long> ids =
        submissions
            .all()
            .map(submission -> submission.getReview().getDataCorrectedById())
            .filter(id -> id != null)
            .collect(Collectors.toSet());
    if (ids.isEmpty()) {
      return Map.of();
    }
    return users.findAllById(ids).stream()
        .collect(Collectors.toMap(user -> user.getId(), user -> user));
  }

  // The current submissions come with the evaluation; the superseded ones take one query per table
  // whatever the number of vans and submissions (rule 17). Inactive ones are on no requirement.
  Submissions loadSubmissions(DriverModel driver, DriverComplianceInputDTO input) {
    List<Long> vehicleIds =
        input.vehicles().stream().map(vehicle -> vehicle.vehicle().getId()).toList();
    return new Submissions(
        Stream.concat(
                input.cnhs().stream(),
                cnhs.findByDriverIdAndSupersededAtIsNotNull(driver.getId()).stream())
            .filter(cnh -> cnh.isActive())
            .toList(),
        Stream.concat(
                input.driverDocuments().stream(),
                driverDocuments.findByDriverIdAndSupersededAtIsNotNull(driver.getId()).stream())
            .filter(document -> document.isActive())
            .toList(),
        Stream.concat(
                input.vehicles().stream().flatMap(vehicle -> vehicle.documents().stream()),
                vehicleIds.isEmpty()
                    ? Stream.<VehicleDocumentModel>empty()
                    : vehicleDocuments
                        .findByVehicleIdInAndSupersededAtIsNotNull(vehicleIds)
                        .stream())
            .toList());
  }

  VehicleResponse toVehicle(
      VehicleDTO evaluated, Submissions submissions, Map<Long, UserModel> correctors) {
    VehicleModel vehicle = evaluated.vehicle();
    return new VehicleResponse(
        vehicle.getToken(),
        vehicle.getPlate(),
        vehicle.getBrand(),
        vehicle.getModel(),
        vehicle.getManufactureYear(),
        vehicle.getCapacity(),
        evaluated.qualified(),
        evaluated.requirements().stream()
            .map(
                entry ->
                    mapper.toRequirement(
                        entry, lastDecided(entry, vehicle, submissions), correctors))
            .toList());
  }

  // The newest decision the requirement does not already show as its current or pending one.
  static @Nullable DocumentSubmission lastDecided(
      RequirementDTO entry, @Nullable VehicleModel vehicle, Submissions submissions) {
    Stream<? extends DocumentSubmission> candidates =
        vehicle == null
            ? Stream.concat(submissions.cnhs().stream(), submissions.driverDocuments().stream())
            : submissions.vehicleDocuments().stream()
                .filter(document -> document.getVehicle().getId().equals(vehicle.getId()));
    return candidates
        .map(submission -> (DocumentSubmission) submission)
        .filter(submission -> submission.getReview().getStatus() != DocumentStatus.PENDING)
        .filter(submission -> submission != entry.current() && submission != entry.pending())
        .filter(
            submission ->
                SchoolTransportCredentialPolicy.requirementOf(submission) == entry.requirement())
        .max(LATEST_DECIDED)
        .orElse(null);
  }

  static List<ReviewQueueItemResponse> reviewQueue(DriverComplianceDTO compliance) {
    List<ReviewQueueItemResponse> queue = new ArrayList<>();
    compliance
        .driverRequirements()
        .forEach(
            entry ->
                reviewableOf(entry)
                    .forEach(item -> queue.add(queueItem(item, entry.requirement(), null))));
    compliance.vehicles().stream()
        .forEach(
            vehicle ->
                vehicle
                    .requirements()
                    .forEach(
                        entry ->
                            reviewableOf(entry)
                                .forEach(
                                    item ->
                                        queue.add(
                                            queueItem(
                                                item,
                                                entry.requirement(),
                                                vehicle.vehicle().getToken())))));
    return List.copyOf(queue);
  }

  static List<DocumentSubmission> reviewableOf(RequirementDTO entry) {
    return Stream.of(entry.current(), entry.pending())
        .filter(submission -> submission != null && isReviewable(submission))
        .distinct()
        .toList();
  }

  static boolean isReviewable(DocumentSubmission submission) {
    return submission.getReview().getStatus() == DocumentStatus.PENDING
        && submission.getReviewedFile() != null;
  }

  static ReviewQueueItemResponse queueItem(
      DocumentSubmission submission,
      CredentialRequirement requirement,
      @Nullable String vehicleToken) {
    return new ReviewQueueItemResponse(
        submission.kind(), submission.getToken(), requirement, vehicleToken);
  }

  // Built from the stored dates: the API keeps only the last onboarding sending and decision (R14).
  static List<HistoryItemResponse> history(
      DriverModel driver, Submissions submissions, Map<Long, UserModel> correctors) {
    List<HistoryItemResponse> items = new ArrayList<>();
    Stream.concat(submissions.cnhs().stream(), submissions.driverDocuments().stream())
        .forEach(submission -> addSubmissionEvents(items, submission, null, correctors));
    submissions
        .vehicleDocuments()
        .forEach(
            document -> addSubmissionEvents(items, document, document.getVehicle(), correctors));
    addOnboardingEvents(items, driver);
    return items.stream()
        .sorted(NEWEST_FIRST_DECISIONS_BEFORE_SENDINGS)
        .limit(HISTORY_LIMIT)
        .toList();
  }

  static boolean isSending(HistoryKind kind) {
    return kind == HistoryKind.SUBMISSION_SENT || kind == HistoryKind.ONBOARDING_SUBMITTED;
  }

  static void addSubmissionEvents(
      List<HistoryItemResponse> items,
      DocumentSubmission submission,
      @Nullable VehicleModel vehicle,
      Map<Long, UserModel> correctors) {
    CredentialRequirement requirement = SchoolTransportCredentialPolicy.requirementOf(submission);
    String vehicleToken = vehicle == null ? null : vehicle.getToken();
    String plate = vehicle == null ? null : vehicle.getPlate();
    items.add(
        new HistoryItemResponse(
            submission.getCreatedAt(),
            HistoryKind.SUBMISSION_SENT,
            requirement,
            vehicleToken,
            plate,
            null,
            null));
    DocumentStatus status = submission.getReview().getStatus();
    if (submission.getReview().getReviewedAt() == null || status == DocumentStatus.PENDING) {
      return;
    }
    items.add(
        new HistoryItemResponse(
            submission.getReview().getReviewedAt(),
            status == DocumentStatus.APPROVED
                ? HistoryKind.SUBMISSION_APPROVED
                : HistoryKind.SUBMISSION_REJECTED,
            requirement,
            vehicleToken,
            plate,
            DriverComplianceDetailMapper.toActor(submission.getReview().getReviewedBy()),
            submission.getReview().getRejectionReasonCode() == null
                ? null
                : submission.getReview().getRejectionReasonCode().name()));
    if (submission.getReview().getDataCorrectedAt() != null) {
      items.add(
          new HistoryItemResponse(
              submission.getReview().getDataCorrectedAt(),
              HistoryKind.SUBMISSION_DATA_CORRECTED,
              requirement,
              vehicleToken,
              plate,
              DriverComplianceDetailMapper.toActor(
                  DriverComplianceDetailMapper.correctorOf(submission.getReview(), correctors)),
              null));
    }
  }

  static void addOnboardingEvents(List<HistoryItemResponse> items, DriverModel driver) {
    if (driver.getSubmittedAt() != null) {
      items.add(
          new HistoryItemResponse(
              driver.getSubmittedAt(),
              HistoryKind.ONBOARDING_SUBMITTED,
              null,
              null,
              null,
              null,
              null));
    }
    boolean decided =
        driver.getApprovalStatus() == DriverApprovalStatus.APPROVED
            || driver.getApprovalStatus() == DriverApprovalStatus.REJECTED;
    if (decided && driver.getReviewedAt() != null) {
      items.add(
          new HistoryItemResponse(
              driver.getReviewedAt(),
              driver.getApprovalStatus() == DriverApprovalStatus.APPROVED
                  ? HistoryKind.DRIVER_APPROVED
                  : HistoryKind.DRIVER_REJECTED,
              null,
              null,
              null,
              DriverComplianceDetailMapper.toActor(driver.getReviewedBy()),
              driver.getRejectionReasonCode() == null
                  ? null
                  : driver.getRejectionReasonCode().name()));
    }
  }

  record Submissions(
      List<DriverCnhModel> cnhs,
      List<DriverDocumentModel> driverDocuments,
      List<VehicleDocumentModel> vehicleDocuments) {

    Stream<DocumentSubmission> all() {
      return Stream.of(cnhs, driverDocuments, vehicleDocuments)
          .flatMap(list -> list.stream().map(submission -> (DocumentSubmission) submission));
    }
  }
}
