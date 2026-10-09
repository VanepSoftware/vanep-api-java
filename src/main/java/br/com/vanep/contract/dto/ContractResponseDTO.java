package br.com.vanep.contract.dto;

import br.com.vanep.contract.enums.ContractStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ContractResponseDTO(
    String token,
    String clientDriverToken,
    String clientToken,
    String clientName,
    String driverToken,
    String driverName,
    ContractStatus status,
    LocalDate startsOn,
    LocalDate endsOn,
    BigDecimal totalAmount,
    Integer installments,
    Integer dueDay,
    List<ContractItemResponseDTO> items,
    Instant createdAt,
    Instant updatedAt) {}
