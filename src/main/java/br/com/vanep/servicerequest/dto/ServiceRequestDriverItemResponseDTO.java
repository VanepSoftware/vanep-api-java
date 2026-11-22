package br.com.vanep.servicerequest.dto;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record ServiceRequestDriverItemResponseDTO(
    @Nullable String dependentToken,
    @Nullable String dependentFirstName,
    @Nullable Integer dependentAge,
    String schoolName,
    String pickupCityName,
    String pickupStateUf,
    @Nullable String pickupNeighborhood,
    List<ServiceRequestDayResponseDTO> days) {}
