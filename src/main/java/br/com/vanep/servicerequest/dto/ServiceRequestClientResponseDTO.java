package br.com.vanep.servicerequest.dto;

import br.com.vanep.proposal.dto.ProposalSummaryResponseDTO;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ServiceRequestClientResponseDTO(
    String token,
    String clientDriverToken,
    @Nullable String conversationToken,
    String driverToken,
    String driverName,
    @Nullable BigDecimal driverRating,
    ServiceRequestStatus status,
    LocalDate desiredStartDate,
    @Nullable String notes,
    String declineReason,
    Instant expiresAt,
    @Nullable ProposalSummaryResponseDTO currentProposal,
    List<ServiceRequestClientItemResponseDTO> items,
    Instant createdAt,
    Instant updatedAt) {}
