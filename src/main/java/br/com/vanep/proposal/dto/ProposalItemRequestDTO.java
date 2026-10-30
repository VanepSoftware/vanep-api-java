package br.com.vanep.proposal.dto;

import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record ProposalItemRequestDTO(
    @NotBlank String dependentToken,
    @NotNull
        @DecimalMin(value = "0", inclusive = false, message = "{contract.amount.invalid}")
        @Digits(integer = 10, fraction = 2, message = "{proposal.amount.scale}")
        BigDecimal monthlyAmount,
    @NotEmpty(message = "{schedule.slots.required}")
        List<@NotNull @Valid ScheduleSlotRequestDTO> slots) {}
