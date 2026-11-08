package br.com.vanep.unlinkedpassenger.dto;

import br.com.vanep.address.dto.DependentAddressRequestDTO;
import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import br.com.vanep.shared.enums.SchoolShift;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UnlinkedPassengerCreateRequestDTO(
    @NotBlank(message = "{unlinked_passenger.name.required}")
        @Size(max = 255, message = "{unlinked_passenger.name.too_long}")
        String name,
    @NotBlank String schoolToken,
    @NotNull SchoolShift schoolShift,
    @NotNull @Valid DependentAddressRequestDTO address,
    @Size(max = 500, message = "{unlinked_passenger.notes.too_long}") String notes,
    List<@Valid ScheduleSlotRequestDTO> slots) {}
