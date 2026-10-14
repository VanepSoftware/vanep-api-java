package br.com.vanep.contract.dto;

import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record ContractItemRequestDTO(
    @NotBlank String dependentToken,
    @NotNull @DecimalMin(value = "0", inclusive = false, message = "{contract.amount.invalid}")
        BigDecimal monthlyAmount,
    List<@Valid ScheduleSlotRequestDTO> slots) {}
