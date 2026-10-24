package br.com.vanep.proposal.dto;

import jakarta.validation.constraints.Size;

public record ProposalDeclineRequestDTO(
    @Size(max = 500, message = "{proposal.decline_reason.too_long}") String reason) {}
