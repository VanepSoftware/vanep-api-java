package br.com.vanep.driveroccupancy.dto;

import br.com.vanep.shared.enums.OperationShift;

public record ShiftOccupancyDTO(OperationShift shift, long passengers) {}
