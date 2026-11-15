package br.com.vanep.drivercompliance.mapper;

import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceRowResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class DriverComplianceMapper {

  public DriverComplianceRowResponseDTO toRow(
      DriverModel driver, DriverComplianceDTO compliance, String city, long vehicleCount) {
    return new DriverComplianceRowResponseDTO(
        driver.getToken(),
        driver.getUser().getName(),
        driver.getUser().getEmail(),
        city,
        vehicleCount,
        driver.getApprovalStatus(),
        driver.getSubmittedAt(),
        driver.getCreatedAt(),
        compliance.nextAction(),
        compliance.canApprove(),
        compliance.pendingReviewCount(),
        compliance.oldestPendingAt(),
        compliance.rejectedCount(),
        compliance.missingCount(),
        compliance.expiredCount(),
        compliance.nextExpiryOn());
  }
}
