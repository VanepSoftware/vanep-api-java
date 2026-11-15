package br.com.vanep.driverstudent.dto;

import br.com.vanep.shared.enums.SchoolShift;

public record UnlinkedStudentRowDTO(
    Long id,
    String token,
    String name,
    String schoolToken,
    String schoolName,
    SchoolShift schoolShift,
    Long vehicleId,
    Long dependentId,
    Long scheduleId) {}
