package br.com.vanep.drivercompliance.dto;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.enums.DriverRejectionReason;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO.BlockerDTO;
import br.com.vanep.drivercompliance.dto.SubmissionResponseDTO.ActorResponseDTO;
import br.com.vanep.drivercompliance.enums.ComplianceState;
import br.com.vanep.drivercompliance.enums.HistoryKind;
import br.com.vanep.drivercompliance.enums.NextAction;
import br.com.vanep.schooltransportcredential.enums.CredentialRequirement;
import br.com.vanep.shared.document.enums.DocumentKind;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DriverComplianceDetailResponseDTO(
    LocalDate asOf,
    DriverResponse driver,
    DecisionResponse decision,
    List<RequirementResponse> driverRequirements,
    List<VehicleResponse> vehicles,
    List<ReviewQueueItemResponse> reviewQueue,
    List<HistoryItemResponse> history) {

  public record DriverResponse(
      String token,
      String name,
      String email,
      String phone,
      String document,
      LocalDate birthDate,
      String city,
      Integer experienceYears,
      Instant createdAt,
      DriverApprovalStatus approvalStatus,
      Instant submittedAt,
      DriverRejectionReason rejectionReasonCode,
      String rejectionReason,
      Instant reviewedAt,
      ActorResponseDTO reviewedBy) {}

  public record DecisionResponse(
      boolean canApprove,
      NextAction nextAction,
      int pendingReviewCount,
      List<BlockerDTO> blockers) {}

  public record RequirementResponse(
      CredentialRequirement requirement,
      boolean optional,
      boolean satisfied,
      ComplianceState state,
      SubmissionResponseDTO current,
      SubmissionResponseDTO pending,
      LastDecisionResponse lastDecision) {}

  public record LastDecisionResponse(
      DocumentStatus status,
      DocumentRejectionReason rejectionReasonCode,
      String rejectionReason,
      ActorResponseDTO reviewedBy,
      Instant reviewedAt,
      Instant supersededAt) {}

  public record VehicleResponse(
      String token,
      String plate,
      String brand,
      String model,
      Integer year,
      Integer capacity,
      boolean qualified,
      List<RequirementResponse> requirements) {}

  public record ReviewQueueItemResponse(
      DocumentKind kind, String token, CredentialRequirement requirement, String vehicleToken) {}

  public record HistoryItemResponse(
      Instant at,
      HistoryKind kind,
      CredentialRequirement requirement,
      String vehicleToken,
      String plate,
      ActorResponseDTO actor,
      String rejectionReasonCode) {}
}
