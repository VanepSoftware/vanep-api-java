package br.com.vanep.proposal.dto;

import br.com.vanep.proposal.enums.ProposalAdjustmentField;
import br.com.vanep.proposal.enums.ProposalAdjustmentPolicy;
import br.com.vanep.proposal.enums.ProposalCancellationPolicy;
import br.com.vanep.proposal.enums.ProposalStatus;
import br.com.vanep.schedule.dto.ScheduleSlotResponseDTO;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ProposalResponseDTO(
    String token,
    String serviceRequestToken,
    String clientDriverToken,
    Integer version,
    ProposalStatus status,
    Instant validUntil,
    LocalDate startsOn,
    LocalDate endsOn,
    BigDecimal totalAmount,
    Integer installments,
    BigDecimal installmentAmount,
    BigDecimal lastInstallmentAmount,
    Integer dueDay,
    BigDecimal enrollmentFee,
    ProposalAdjustmentPolicy adjustmentPolicy,
    ProposalCancellationPolicy cancellationPolicy,
    Vehicle vehicle,
    String message,
    String declineReason,
    Instant acceptedAt,
    List<Item> items,
    List<Adjustment> adjustments,
    Instant createdAt,
    Instant updatedAt) {

  // dependentName is null for the driver, who sees only the first name (LGPD).
  public record Item(
      String dependentToken,
      String dependentName,
      String dependentFirstName,
      List<ScheduleSlotResponseDTO> slots) {}

  // token is null for the client, who has no use for the driver's vehicle.
  public record Vehicle(
      String token,
      String plate,
      String brand,
      String model,
      Integer manufactureYear,
      String color,
      Integer capacity) {}

  public record Adjustment(
      ProposalAdjustmentField field, String suggestedValue, String note, Instant createdAt) {}
}
