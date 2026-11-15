package br.com.vanep.driverstudent.dto;

import br.com.vanep.driverstudent.enums.FamilyStatus;
import br.com.vanep.familymatch.dto.FamilyMatchResponseDTO;
import br.com.vanep.routepassenger.enums.PassengerSource;
import br.com.vanep.schedule.dto.ScheduleSlotResponseDTO;
import br.com.vanep.shared.enums.SchoolShift;
import java.time.Instant;
import java.util.List;

public record DriverStudentResponseDTO(
    String token,
    PassengerSource source,
    String name,
    String schoolToken,
    String schoolName,
    SchoolShift schoolShift,
    List<ScheduleSlotResponseDTO> slots,
    String vehicleToken,
    String dependentToken,
    FamilyStatus familyStatus,
    Instant invitedAt,
    FamilyMatchResponseDTO familyMatch) {}
