package br.com.vanep.servicerequest.dto;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ServiceRequestClientItemResponseDTO(
    @Nullable String dependentToken,
    String dependentName,
    LocalDate dependentBirthDate,
    String schoolName,
    String pickupCityName,
    String pickupStateUf,
    @Nullable String pickupNeighborhood,
    List<ServiceRequestDayResponseDTO> days) {}
