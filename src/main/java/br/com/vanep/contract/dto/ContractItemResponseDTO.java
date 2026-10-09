package br.com.vanep.contract.dto;

import br.com.vanep.schedule.dto.ScheduleSlotResponseDTO;
import java.math.BigDecimal;
import java.util.List;

public record ContractItemResponseDTO(
    String token,
    String dependentToken,
    String dependentName,
    String schoolToken,
    String schoolName,
    String pickupZipCode,
    String pickupStreet,
    String pickupNumber,
    String pickupComplement,
    String pickupNeighborhood,
    String pickupDistrictName,
    String pickupCityToken,
    String pickupCityName,
    BigDecimal monthlyAmount,
    List<ScheduleSlotResponseDTO> slots) {}
