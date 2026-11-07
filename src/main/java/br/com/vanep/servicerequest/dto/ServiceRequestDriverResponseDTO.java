package br.com.vanep.servicerequest.dto;

import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ServiceRequestDriverResponseDTO(
    String token,
    String clientDriverToken,
    String clientFirstName,
    ServiceRequestStatus status,
    LocalDate desiredStartDate,
    String notes,
    String declineReason,
    Instant expiresAt,
    List<ServiceRequestDriverItemResponseDTO> items,
    Instant createdAt,
    Instant updatedAt) {}
