package br.com.vanep.contract.dto;

import br.com.vanep.schedule.dto.ScheduleSlotResponseDTO;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ContractItemResponseDTO(
    String token,
    String dependentToken,
    String dependentName,
    String schoolToken,
    String schoolName,
    @Nullable String pickupZipCode,
    String pickupStreet,
    @Nullable String pickupNumber,
    @Nullable String pickupComplement,
    @Nullable String pickupNeighborhood,
    String pickupCityToken,
    String pickupCityName,
    List<ScheduleSlotResponseDTO> slots) {}
