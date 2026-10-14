package br.com.vanep.contract.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ContractCreateRequestDTO(
    @NotBlank String clientDriverToken,
    @NotNull LocalDate startsOn,
    @NotNull LocalDate endsOn,
    @NotNull @DecimalMin(value = "0", inclusive = false, message = "{contract.amount.invalid}")
        BigDecimal totalAmount,
    @NotNull
        @Min(value = 1, message = "{contract.installments.invalid}")
        @Max(value = 12, message = "{contract.installments.invalid}")
        Integer installments,
    @NotNull
        @Min(value = 1, message = "{contract.due_day.invalid}")
        @Max(value = 28, message = "{contract.due_day.invalid}")
        Integer dueDay,
    @NotEmpty(message = "{contract.items.required}") List<@Valid ContractItemRequestDTO> items) {}
