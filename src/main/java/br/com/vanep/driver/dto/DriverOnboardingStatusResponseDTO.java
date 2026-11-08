package br.com.vanep.driver.dto;

import br.com.vanep.driver.DriverApprovalStatus;
import java.time.Instant;

public record DriverOnboardingStatusResponseDTO(
    String driverToken,
    DriverApprovalStatus approvalStatus,
    boolean canSubmit,
    Instant submittedAt,
    String rejectionReason,
    DriverOnboardingStepDTO profileStep,
    DriverOnboardingStepDTO vehicleStep,
    DriverOnboardingStepDTO cnhStep,
    DriverOnboardingDocumentsStepDTO documentsStep) {}
