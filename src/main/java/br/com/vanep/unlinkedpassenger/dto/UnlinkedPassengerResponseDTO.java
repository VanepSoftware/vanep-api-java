package br.com.vanep.unlinkedpassenger.dto;

import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.familymatch.dto.FamilyMatchResponseDTO;
import br.com.vanep.schedule.dto.ScheduleSlotResponseDTO;
import br.com.vanep.shared.enums.SchoolShift;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record UnlinkedPassengerResponseDTO(
    String token,
    String name,
    String schoolToken,
    String schoolName,
    SchoolShift schoolShift,
    AddressResponseDTO address,
    @Nullable String notes,
    List<ScheduleSlotResponseDTO> slots,
    @Nullable String vehicleToken,
    @Nullable String dependentToken,
    @Nullable Instant invitedAt,
    @Nullable FamilyMatchResponseDTO familyMatch,
    Instant createdAt,
    Instant updatedAt) {}
