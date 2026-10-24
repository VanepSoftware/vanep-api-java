package br.com.vanep.proposal.dto;

import br.com.vanep.proposal.enums.ProposalAdjustmentPolicy;
import br.com.vanep.proposal.enums.ProposalCancellationPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProposalCreateRequestDTO(
    @NotNull @FutureOrPresent(message = "{proposal.starts_on.past}") LocalDate startsOn,
    @NotNull LocalDate endsOn,
    @NotNull
        @DecimalMin(value = "0", inclusive = false, message = "{contract.amount.invalid}")
        @Digits(integer = 10, fraction = 2, message = "{proposal.amount.scale}")
        BigDecimal totalAmount,
    @NotNull
        @Min(value = 1, message = "{contract.installments.invalid}")
        @Max(value = 12, message = "{contract.installments.invalid}")
        Integer installments,
    @NotNull
        @Min(value = 1, message = "{contract.due_day.invalid}")
        @Max(value = 28, message = "{contract.due_day.invalid}")
        Integer dueDay,
    @DecimalMin(value = "0", message = "{proposal.enrollment_fee.invalid}")
        @Digits(integer = 10, fraction = 2, message = "{proposal.amount.scale}")
        BigDecimal enrollmentFee,
    @NotNull ProposalAdjustmentPolicy adjustmentPolicy,
    @NotNull ProposalCancellationPolicy cancellationPolicy,
    @NotBlank String vehicleToken,
    @Min(value = 1, message = "{proposal.valid_days.invalid}")
        @Max(value = 7, message = "{proposal.valid_days.invalid}")
        Integer validDays,
    @Size(max = 1000, message = "{proposal.message.too_long}") String message,
    @NotEmpty(message = "{proposal.items.required}")
        List<@NotNull @Valid ProposalItemRequestDTO> items) {}
