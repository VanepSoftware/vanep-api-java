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
import org.jspecify.annotations.Nullable;

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
    @Nullable String message,
    String declineReason,
    Instant acceptedAt,
    List<Item> items,
    List<Adjustment> adjustments,
    Instant createdAt,
    Instant updatedAt) {

  // dependentName is null for the driver, who sees only the first name (LGPD).
  public record Item(
      @Nullable String dependentToken,
      @Nullable String dependentName,
      @Nullable String dependentFirstName,
      List<ScheduleSlotResponseDTO> slots) {}

  // token is null for the client, who has no use for the driver's vehicle.
  public record Vehicle(
      @Nullable String token,
      @Nullable String plate,
      @Nullable String brand,
      @Nullable String model,
      @Nullable Integer manufactureYear,
      @Nullable String color,
      Integer capacity) {}

  public record Adjustment(
      ProposalAdjustmentField field,
      @Nullable String suggestedValue,
      @Nullable String note,
      Instant createdAt) {}
}
