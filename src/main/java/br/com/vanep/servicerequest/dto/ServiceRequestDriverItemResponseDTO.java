package br.com.vanep.servicerequest.dto;

import java.util.List;

public record ServiceRequestDriverItemResponseDTO(
    String dependentToken,
    String dependentFirstName,
    Integer dependentAge,
    String schoolName,
    String pickupCityName,
    String pickupStateUf,
    String pickupNeighborhood,
    List<ServiceRequestDayResponseDTO> days) {}
