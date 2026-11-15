package br.com.vanep.servicerequest.dto;

import java.time.LocalDate;
import java.util.List;

public record ServiceRequestClientItemResponseDTO(
    String dependentToken,
    String dependentName,
    LocalDate dependentBirthDate,
    String schoolName,
    String pickupCityName,
    String pickupStateUf,
    String pickupNeighborhood,
    List<ServiceRequestDayResponseDTO> days) {}
