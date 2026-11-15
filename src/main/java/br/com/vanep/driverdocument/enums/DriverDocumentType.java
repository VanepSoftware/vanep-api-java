package br.com.vanep.driverdocument.enums;

import br.com.vanep.shared.document.enums.DocumentDataRequirements;
import java.time.LocalDate;

public enum DriverDocumentType implements DocumentDataRequirements {
  MUNICIPAL_AUTHORIZATION(true),
  SCHOOL_DRIVER_COURSE(false),
  CRIMINAL_RECORD(false);

  private final boolean numberRequired;

  DriverDocumentType(boolean numberRequired) {
    this.numberRequired = numberRequired;
  }

  @Override
  public boolean requiresNumberOnApproval() {
    return numberRequired;
  }

  @Override
  public boolean requiresExpiryOnApproval() {
    return false;
  }

  // Guards against typos, not a legal rule.
  @Override
  public LocalDate latestExpiryOn(LocalDate today) {
    return switch (this) {
      case MUNICIPAL_AUTHORIZATION, SCHOOL_DRIVER_COURSE -> today.plusYears(5);
      case CRIMINAL_RECORD -> LocalDate.MAX;
    };
  }
}
