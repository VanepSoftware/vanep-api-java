package br.com.vanep.contract.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ContractCreateRequestDTO(
    @NotBlank(message = "{contract.client_driver_token.required}") String clientDriverToken,
    @NotNull(message = "{contract.starts_on.required}") LocalDate startsOn,
    @NotNull(message = "{contract.ends_on.required}") LocalDate endsOn,
    @NotNull(message = "{contract.total_amount.required}")
        @DecimalMin(value = "0", inclusive = false, message = "{contract.amount.invalid}")
        @Digits(integer = 10, fraction = 2, message = "{contract.amount.scale}")
        BigDecimal totalAmount,
    @NotNull(message = "{contract.installments.required}")
        @Min(value = 1, message = "{contract.installments.invalid}")
        @Max(value = 12, message = "{contract.installments.invalid}")
        Integer installments,
    @NotNull(message = "{contract.due_day.required}")
        @Min(value = 1, message = "{contract.due_day.invalid}")
        @Max(value = 28, message = "{contract.due_day.invalid}")
        Integer dueDay,
    @NotEmpty(message = "{contract.items.required}") List<@Valid ContractItemRequestDTO> items) {}
