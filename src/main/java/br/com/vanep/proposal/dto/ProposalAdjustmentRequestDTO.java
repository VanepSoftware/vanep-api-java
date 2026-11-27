package br.com.vanep.proposal.dto;

import br.com.vanep.proposal.enums.ProposalAdjustmentField;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProposalAdjustmentRequestDTO(
    @NotNull(message = "{proposal.adjustment.field.required}") ProposalAdjustmentField field,
    @Size(max = 100, message = "{proposal.adjustment.suggested_value.too_long}")
        String suggestedValue,
    @Size(max = 500, message = "{proposal.adjustment.note.too_long}") String note) {}
