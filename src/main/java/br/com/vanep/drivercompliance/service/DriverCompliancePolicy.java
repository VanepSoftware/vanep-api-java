package br.com.vanep.drivercompliance.service;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO.BlockerDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO.RequirementDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO.VehicleDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceInputDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceInputDTO.VehicleInputDTO;
import br.com.vanep.drivercompliance.enums.BlockerKind;
import br.com.vanep.drivercompliance.enums.ComplianceState;
import br.com.vanep.drivercompliance.enums.NextAction;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.schooltransportcredential.enums.CredentialRequirement;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialPolicy;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.model.DocumentSubmission;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class DriverCompliancePolicy {

  static final List<CredentialRequirement> DRIVER_REQUIREMENTS =
      List.of(
          CredentialRequirement.DRIVER_LICENSE,
          CredentialRequirement.SCHOOL_DRIVER_COURSE,
          CredentialRequirement.CRIMINAL_RECORD,
          CredentialRequirement.MUNICIPAL_AUTHORIZATION);

  static final List<CredentialRequirement> VEHICLE_REQUIREMENTS =
      List.of(
          CredentialRequirement.VEHICLE_REGISTRATION,
          CredentialRequirement.VEHICLE_INSPECTION,
          CredentialRequirement.SCHOOL_VEHICLE_AUTHORIZATION);

  private static final Comparator<DocumentSubmission> OLDEST_FIRST =
      Comparator.comparing(
          (DocumentSubmission submission) -> submission.getCreatedAt(),
          Comparator.nullsFirst(Comparator.<Instant>naturalOrder()));

  private static final Comparator<VehicleDTO> CLOSEST_TO_COMPLETE =
      Comparator.comparingLong(
              (VehicleDTO vehicle) ->
                  vehicle.requirements().stream().filter(entry -> !entry.satisfied()).count())
          .thenComparing(
              vehicle -> vehicle.vehicle().getCreatedAt(),
              Comparator.nullsFirst(Comparator.<Instant>naturalOrder()))
          .thenComparing(
              vehicle -> vehicle.vehicle().getId(),
              Comparator.nullsFirst(Comparator.<Long>naturalOrder()));

  private final SchoolTransportCredentialPolicy credentials;

  public DriverCompliancePolicy(SchoolTransportCredentialPolicy credentials) {
    this.credentials = credentials;
  }

  public DriverComplianceDTO evaluate(DriverComplianceInputDTO input, LocalDate date) {
    List<RequirementDTO> driverRequirements =
        DRIVER_REQUIREMENTS.stream()
            .map(
                requirement ->
                    evaluateRequirement(
                        requirement, input.cnhs(), input.driverDocuments(), List.of(), date))
            .toList();
    List<VehicleDTO> vehicles =
        input.vehicles().stream().map(vehicle -> evaluateVehicle(vehicle, date)).toList();

    DriverApprovalStatus status = input.driver().getApprovalStatus();
    boolean driverSatisfied = required(driverRequirements).allMatch(entry -> entry.satisfied());
    boolean anyQualified = vehicles.stream().anyMatch(vehicle -> vehicle.qualified());
    boolean canApprove =
        status == DriverApprovalStatus.UNDER_REVIEW && driverSatisfied && anyQualified;
    List<DocumentSubmission> reviewable = reviewableSubmissions(input);
    List<RequirementDTO> requiredEntries =
        Stream.concat(
                required(driverRequirements),
                vehicles.stream().flatMap(vehicle -> vehicle.requirements().stream()))
            .toList();

    return new DriverComplianceDTO(
        driverRequirements,
        vehicles,
        canApprove,
        canApprove ? List.of() : blockers(status, driverRequirements, vehicles),
        nextAction(status, canApprove, !reviewable.isEmpty(), driverSatisfied && anyQualified),
        reviewable.size(),
        reviewable.stream().min(OLDEST_FIRST).map(oldest -> oldest.getCreatedAt()).orElse(null),
        countByState(requiredEntries, ComplianceState.REJECTED),
        countByState(requiredEntries, ComplianceState.MISSING),
        countByState(requiredEntries, ComplianceState.EXPIRED),
        nextExpiry(requiredEntries));
  }

  RequirementDTO evaluateRequirement(
      CredentialRequirement requirement,
      List<DriverCnhModel> cnhs,
      List<DriverDocumentModel> driverDocuments,
      List<VehicleDocumentModel> vehicleDocuments,
      LocalDate date) {
    List<DocumentSubmission> submissions =
        credentials.submissionsOf(requirement, cnhs, driverDocuments, vehicleDocuments);
    Optional<DocumentSubmission> satisfying =
        credentials.findSatisfying(requirement, submissions, date);
    DocumentSubmission newest = submissions.stream().max(OLDEST_FIRST).orElse(null);
    DocumentSubmission current = satisfying.orElse(newest);
    DocumentSubmission pending =
        submissions.stream()
            .filter(submission -> submission != current && isPending(submission))
            .max(OLDEST_FIRST)
            .orElse(null);
    return new RequirementDTO(
        requirement,
        requirement.isOptional(),
        satisfying.isPresent(),
        satisfying.isPresent() ? ComplianceState.APPROVED : stateOf(newest, date),
        current,
        pending);
  }

  static ComplianceState stateOf(@Nullable DocumentSubmission newest, LocalDate date) {
    if (newest == null) {
      return ComplianceState.MISSING;
    }
    boolean expired =
        newest.expiresOn() != null
            && !SchoolTransportCredentialPolicy.isValidOn(newest.expiresOn(), date);
    return switch (newest.getReview().getStatus()) {
      case PENDING -> expired ? ComplianceState.EXPIRED : ComplianceState.PENDING;
      case REJECTED -> ComplianceState.REJECTED;
      case APPROVED -> expired ? ComplianceState.EXPIRED : ComplianceState.APPROVED;
    };
  }

  VehicleDTO evaluateVehicle(VehicleInputDTO vehicle, LocalDate date) {
    List<RequirementDTO> requirements =
        VEHICLE_REQUIREMENTS.stream()
            .map(
                requirement ->
                    evaluateRequirement(
                        requirement, List.of(), List.of(), vehicle.documents(), date))
            .toList();
    return new VehicleDTO(
        vehicle.vehicle(),
        requirements.stream().allMatch(entry -> entry.satisfied()),
        requirements);
  }

  static List<BlockerDTO> blockers(
      DriverApprovalStatus status,
      List<RequirementDTO> driverRequirements,
      List<VehicleDTO> vehicles) {
    List<BlockerDTO> blockers = new ArrayList<>();
    if (status != DriverApprovalStatus.UNDER_REVIEW) {
      blockers.add(new BlockerDTO(BlockerKind.APPROVAL_STATUS, null, null, null, null));
    }
    required(driverRequirements)
        .filter(entry -> !entry.satisfied())
        .forEach(
            entry ->
                blockers.add(
                    new BlockerDTO(
                        driverBlockerKind(entry), entry.requirement(), entry.state(), null, null)));
    if (vehicles.isEmpty()) {
      blockers.add(new BlockerDTO(BlockerKind.NO_ACTIVE_VEHICLE, null, null, null, null));
    } else if (vehicles.stream().noneMatch(vehicle -> vehicle.qualified())) {
      VehicleDTO closest = vehicles.stream().min(CLOSEST_TO_COMPLETE).orElseThrow();
      VehicleModel van = closest.vehicle();
      closest.requirements().stream()
          .filter(entry -> !entry.satisfied())
          .forEach(
              entry ->
                  blockers.add(
                      new BlockerDTO(
                          BlockerKind.VEHICLE_REQUIREMENT,
                          entry.requirement(),
                          entry.state(),
                          van.getToken(),
                          van.getPlate())));
    }
    return List.copyOf(blockers);
  }

  // A license sent without D or E stays useless whatever the review says.
  static BlockerKind driverBlockerKind(RequirementDTO entry) {
    boolean live =
        entry.state() == ComplianceState.PENDING || entry.state() == ComplianceState.APPROVED;
    return live
            && entry.current() != null
            && SchoolTransportCredentialPolicy.lacksSchoolTransportCategory(entry.current())
        ? BlockerKind.CNH_CATEGORY
        : BlockerKind.DRIVER_REQUIREMENT;
  }

  static NextAction nextAction(
      DriverApprovalStatus status, boolean canApprove, boolean awaitingReview, boolean inOrder) {
    if (canApprove) {
      return NextAction.READY_TO_APPROVE;
    }
    if (awaitingReview) {
      return NextAction.ADMIN;
    }
    if (status != DriverApprovalStatus.APPROVED || !inOrder) {
      return NextAction.DRIVER;
    }
    return NextAction.NONE;
  }

  // Only a submission with its file can be reviewed; without one it is still the driver's move.
  static List<DocumentSubmission> reviewableSubmissions(DriverComplianceInputDTO input) {
    return Stream.of(
            input.cnhs().stream().filter(cnh -> cnh.isActive()),
            input.driverDocuments().stream().filter(document -> document.isActive()),
            input.vehicles().stream().flatMap(vehicle -> vehicle.documents().stream()))
        .flatMap(submissions -> submissions.<DocumentSubmission>map(submission -> submission))
        .filter(submission -> isPending(submission) && submission.getReviewedFile() != null)
        .toList();
  }

  static boolean isPending(DocumentSubmission submission) {
    return submission.getReview().getStatus() == DocumentStatus.PENDING;
  }

  static Stream<RequirementDTO> required(List<RequirementDTO> entries) {
    return entries.stream().filter(entry -> !entry.optional());
  }

  static int countByState(List<RequirementDTO> entries, ComplianceState state) {
    return (int) entries.stream().filter(entry -> entry.state() == state).count();
  }

  static @Nullable LocalDate nextExpiry(List<RequirementDTO> entries) {
    return entries.stream()
        .filter(entry -> entry.satisfied())
        .map(entry -> Objects.requireNonNull(entry.current()).expiresOn())
        .filter(value -> value != null)
        .min(Comparator.naturalOrder())
        .orElse(null);
  }
}
