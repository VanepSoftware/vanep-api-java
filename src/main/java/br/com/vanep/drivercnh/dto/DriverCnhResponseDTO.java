package br.com.vanep.drivercnh.dto;

import br.com.vanep.shared.document.dto.DocumentFileResponseDTO;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record DriverCnhResponseDTO(
    String token,
    String driverToken,
    @Nullable String registrationNumber,
    @Nullable String category,
    @Nullable LocalDate issueDate,
    @Nullable LocalDate validUntil,
    @Nullable LocalDate firstLicenseDate,
    @Nullable String securityNumber,
    @Nullable String issuingState,
    @Nullable String fileUrl,
    @Nullable DocumentFileResponseDTO file,
    DocumentStatus status,
    ReviewMethod reviewMethod,
    @Nullable DocumentRejectionReason rejectionReasonCode,
    @Nullable String rejectionReason,
    @Nullable String reviewedByUserToken,
    Instant reviewedAt,
    Instant dataCorrectedAt,
    Long version,
    boolean active,
    Instant createdAt) {}
