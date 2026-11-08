package br.com.vanep.contract.dto;

import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ContractItemRequestDTO(
    @NotBlank String dependentToken, List<@Valid ScheduleSlotRequestDTO> slots) {}
