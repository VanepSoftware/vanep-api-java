package br.com.vanep.drivercompliance.dto;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.drivercompliance.enums.ComplianceState;
import br.com.vanep.schooltransportcredential.enums.CredentialRequirement;
import br.com.vanep.shared.document.dto.DocumentFileResponseDTO;
import br.com.vanep.shared.document.enums.DocumentKind;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DriverMeComplianceResponseDTO(
    LocalDate asOf,
    DriverApprovalStatus approvalStatus,
    List<RequirementResponse> driverRequirements,
    List<VehicleResponse> vehicles) {

  public record VehicleResponse(
      String token,
      String plate,
      String brand,
      String model,
      boolean qualified,
      List<RequirementResponse> requirements) {}

  public record RequirementResponse(
      CredentialRequirement requirement,
      boolean optional,
      boolean satisfied,
      ComplianceState state,
      boolean expiringSoon,
      SubmissionResponse current,
      SubmissionResponse pending) {}

  public record SubmissionResponse(
      DocumentKind kind,
      String token,
      String documentNumber,
      LocalDate expiresAt,
      DocumentStatus status,
      DocumentRejectionReason rejectionReasonCode,
      String rejectionReason,
      Instant reviewedAt,
      Instant createdAt,
      Long version,
      String fileUrl,
      DocumentFileResponseDTO file,
      String category,
      LocalDate issueDate,
      String registrationNumber) {}
}
