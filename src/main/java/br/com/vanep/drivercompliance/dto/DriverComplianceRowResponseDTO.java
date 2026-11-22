package br.com.vanep.drivercompliance.dto;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.drivercompliance.enums.NextAction;
import java.time.Instant;
import java.time.LocalDate;

public record DriverComplianceRowResponseDTO(
    String token,
    String name,
    String email,
    String city,
    long vehicleCount,
    DriverApprovalStatus approvalStatus,
    Instant submittedAt,
    Instant createdAt,
    NextAction nextAction,
    boolean canApprove,
    int pendingReviewCount,
    Instant oldestPendingAt,
    int rejectedCount,
    int missingCount,
    int expiredCount,
    LocalDate nextExpiryOn) {}
