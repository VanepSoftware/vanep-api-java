package br.com.vanep.servicerequest.dto;

import br.com.vanep.proposal.dto.ProposalSummaryResponseDTO;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ServiceRequestDriverResponseDTO(
    String token,
    String clientDriverToken,
    @Nullable String conversationToken,
    @Nullable String clientFirstName,
    ServiceRequestStatus status,
    LocalDate desiredStartDate,
    @Nullable String notes,
    String declineReason,
    Instant expiresAt,
    @Nullable ProposalSummaryResponseDTO currentProposal,
    List<ServiceRequestDriverItemResponseDTO> items,
    Instant createdAt,
    Instant updatedAt) {}
