package br.com.vanep.proposal.dto;

import br.com.vanep.proposal.enums.ProposalStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ProposalSummaryResponseDTO(
    String token,
    Integer version,
    ProposalStatus status,
    BigDecimal totalAmount,
    Integer installments,
    BigDecimal installmentAmount,
    Instant validUntil,
    LocalDate startsOn,
    boolean hasPendingAdjustment) {}
