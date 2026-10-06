package br.com.vanep.stopchange.dto;

import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.shared.enums.Shift;
import br.com.vanep.stopchange.enums.StopChangeStatus;
import java.time.Instant;
import java.time.LocalDate;

public record StopChangeResponseDTO(
    String token,
    String dependentToken,
    String dependentName,
    String tripToken,
    LocalDate serviceDate,
    Shift shift,
    AddressResponseDTO newDropoffAddress,
    String reason,
    StopChangeStatus status,
    String requestedByUserToken,
    String respondedByUserToken,
    Instant respondedAt,
    Instant createdAt,
    Instant updatedAt) {}
