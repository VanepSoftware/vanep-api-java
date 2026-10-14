package br.com.vanep.unlinkedpassenger.dto;

import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import jakarta.validation.Valid;
import java.util.List;

public record UnlinkedPassengerScheduleRequestDTO(List<@Valid ScheduleSlotRequestDTO> slots) {}
