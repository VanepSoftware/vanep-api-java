package br.com.vanep.servicerequest.dto;

import br.com.vanep.proposal.dto.ProposalSummaryResponseDTO;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ServiceRequestClientResponseDTO(
    String token,
    String clientDriverToken,
    String conversationToken,
    String driverToken,
    String driverName,
    BigDecimal driverRating,
    ServiceRequestStatus status,
    LocalDate desiredStartDate,
    String notes,
    String declineReason,
    Instant expiresAt,
    ProposalSummaryResponseDTO currentProposal,
    List<ServiceRequestClientItemResponseDTO> items,
    Instant createdAt,
    Instant updatedAt) {}
