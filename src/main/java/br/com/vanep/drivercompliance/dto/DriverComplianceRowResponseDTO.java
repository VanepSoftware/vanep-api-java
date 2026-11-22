package br.com.vanep.drivercompliance.dto;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.drivercompliance.enums.NextAction;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record DriverComplianceRowResponseDTO(
    String token,
    String name,
    String email,
    @Nullable String city,
    long vehicleCount,
    DriverApprovalStatus approvalStatus,
    Instant submittedAt,
    Instant createdAt,
    NextAction nextAction,
    boolean canApprove,
    int pendingReviewCount,
    @Nullable Instant oldestPendingAt,
    int rejectedCount,
    int missingCount,
    int expiredCount,
    @Nullable LocalDate nextExpiryOn) {}
