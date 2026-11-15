package br.com.vanep.drivercompliance.dto;

import br.com.vanep.drivercompliance.enums.BlockerKind;
import br.com.vanep.drivercompliance.enums.ComplianceState;
import br.com.vanep.drivercompliance.enums.NextAction;
import br.com.vanep.schooltransportcredential.enums.CredentialRequirement;
import br.com.vanep.shared.document.model.DocumentSubmission;
import br.com.vanep.vehicle.model.VehicleModel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DriverComplianceDTO(
    List<RequirementDTO> driverRequirements,
    List<VehicleDTO> vehicles,
    boolean canApprove,
    List<BlockerDTO> blockers,
    NextAction nextAction,
    int pendingReviewCount,
    Instant oldestPendingAt,
    int rejectedCount,
    int missingCount,
    int expiredCount,
    LocalDate nextExpiryOn) {

  public List<VehicleDTO> qualifiedVehicles() {
    return vehicles.stream().filter(vehicle -> vehicle.qualified()).toList();
  }

  public RequirementDTO driverRequirement(CredentialRequirement requirement) {
    return driverRequirements.stream()
        .filter(entry -> entry.requirement() == requirement)
        .findFirst()
        .orElseThrow();
  }

  public record RequirementDTO(
      CredentialRequirement requirement,
      boolean optional,
      boolean satisfied,
      ComplianceState state,
      DocumentSubmission current,
      DocumentSubmission pending) {}

  public record VehicleDTO(
      VehicleModel vehicle, boolean qualified, List<RequirementDTO> requirements) {}

  public record BlockerDTO(
      BlockerKind kind,
      CredentialRequirement requirement,
      ComplianceState state,
      String vehicleToken,
      String plate) {}
}
