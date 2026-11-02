package br.com.vanep.driveroccupancy.dto;

import java.util.List;

public record DriverOccupancyResponseDTO(
    List<ShiftOccupancyDTO> shifts, long students, long studentsWithFamily) {}
