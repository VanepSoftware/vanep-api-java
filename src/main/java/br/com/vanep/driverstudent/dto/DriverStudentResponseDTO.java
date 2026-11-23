package br.com.vanep.driverstudent.dto;

import br.com.vanep.driverstudent.enums.FamilyStatus;
import br.com.vanep.familymatch.dto.FamilyMatchResponseDTO;
import br.com.vanep.routepassenger.enums.PassengerSource;
import br.com.vanep.schedule.dto.ScheduleSlotResponseDTO;
import br.com.vanep.shared.enums.SchoolShift;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record DriverStudentResponseDTO(
    String token,
    PassengerSource source,
    String name,
    String schoolToken,
    String schoolName,
    @Nullable SchoolShift schoolShift,
    List<ScheduleSlotResponseDTO> slots,
    @Nullable String vehicleToken,
    @Nullable String dependentToken,
    FamilyStatus familyStatus,
    @Nullable Instant invitedAt,
    @Nullable FamilyMatchResponseDTO familyMatch) {}
