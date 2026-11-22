package br.com.vanep.driver.dto;

import br.com.vanep.driver.DriverApprovalStatus;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record DriverOnboardingStatusResponseDTO(
    String driverToken,
    DriverApprovalStatus approvalStatus,
    boolean canSubmit,
    Instant submittedAt,
    @Nullable String rejectionReason,
    DriverOnboardingStepDTO profileStep,
    DriverOnboardingStepDTO vehicleStep,
    DriverOnboardingStepDTO cnhStep,
    DriverOnboardingDocumentsStepDTO documentsStep) {}
