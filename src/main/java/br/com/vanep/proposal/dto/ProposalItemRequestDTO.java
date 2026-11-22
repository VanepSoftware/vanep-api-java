package br.com.vanep.proposal.dto;

import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ProposalItemRequestDTO(
    @NotBlank @Nullable String dependentToken,
    @NotEmpty(message = "{schedule.slots.required}")
        List<@NotNull @Valid ScheduleSlotRequestDTO> slots) {}
