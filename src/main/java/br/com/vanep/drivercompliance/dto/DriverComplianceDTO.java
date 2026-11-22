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
import org.jspecify.annotations.Nullable;

public record DriverComplianceDTO(
    List<RequirementDTO> driverRequirements,
    List<VehicleDTO> vehicles,
    boolean canApprove,
    List<BlockerDTO> blockers,
    NextAction nextAction,
    int pendingReviewCount,
    @Nullable Instant oldestPendingAt,
    int rejectedCount,
    int missingCount,
    int expiredCount,
    @Nullable LocalDate nextExpiryOn) {

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
      @Nullable DocumentSubmission current,
      @Nullable DocumentSubmission pending) {}

  public record VehicleDTO(
      VehicleModel vehicle, boolean qualified, List<RequirementDTO> requirements) {}

  public record BlockerDTO(
      BlockerKind kind,
      @Nullable CredentialRequirement requirement,
      @Nullable ComplianceState state,
      @Nullable String vehicleToken,
      @Nullable String plate) {}
}
