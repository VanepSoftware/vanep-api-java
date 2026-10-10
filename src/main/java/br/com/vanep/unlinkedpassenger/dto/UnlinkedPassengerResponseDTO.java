package br.com.vanep.unlinkedpassenger.dto;

import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.schedule.dto.ScheduleSlotResponseDTO;
import br.com.vanep.shared.enums.SchoolShift;
import java.time.Instant;
import java.util.List;

public record UnlinkedPassengerResponseDTO(
    String token,
    String name,
    String schoolToken,
    String schoolName,
    SchoolShift schoolShift,
    AddressResponseDTO address,
    String notes,
    List<ScheduleSlotResponseDTO> slots,
    Instant createdAt,
    Instant updatedAt) {}
