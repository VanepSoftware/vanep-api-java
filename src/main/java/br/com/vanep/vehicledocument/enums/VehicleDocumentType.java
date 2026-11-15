package br.com.vanep.vehicledocument.enums;

import br.com.vanep.shared.document.enums.DocumentDataRequirements;
import java.time.LocalDate;

public enum VehicleDocumentType implements DocumentDataRequirements {
  CRLV(false),
  VEHICLE_INSPECTION(false),
  SCHOOL_VEHICLE_AUTHORIZATION(true);

  private final boolean numberRequired;

  VehicleDocumentType(boolean numberRequired) {
    this.numberRequired = numberRequired;
  }

  @Override
  public boolean requiresNumberOnApproval() {
    return numberRequired;
  }

  @Override
  public boolean requiresExpiryOnApproval() {
    return true;
  }

  // Guards against typos, not a legal rule: every state's CRLV calendar ends within next year.
  @Override
  public LocalDate latestExpiryOn(LocalDate today) {
    return switch (this) {
      case CRLV -> LocalDate.of(today.getYear() + 1, 12, 31);
      case VEHICLE_INSPECTION -> today.plusMonths(7);
      case SCHOOL_VEHICLE_AUTHORIZATION -> today.plusYears(2);
    };
  }
}
