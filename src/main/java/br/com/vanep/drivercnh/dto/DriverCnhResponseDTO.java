package br.com.vanep.drivercnh.dto;

import br.com.vanep.shared.document.dto.DocumentFileResponseDTO;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import java.time.Instant;
import java.time.LocalDate;

public record DriverCnhResponseDTO(
    String token,
    String driverToken,
    String registrationNumber,
    String category,
    LocalDate issueDate,
    LocalDate validUntil,
    LocalDate firstLicenseDate,
    String securityNumber,
    String issuingState,
    String fileUrl,
    DocumentFileResponseDTO file,
    DocumentStatus status,
    ReviewMethod reviewMethod,
    DocumentRejectionReason rejectionReasonCode,
    String rejectionReason,
    String reviewedByUserToken,
    Instant reviewedAt,
    Instant dataCorrectedAt,
    Long version,
    boolean active,
    Instant createdAt) {}
