package br.com.vanep.driverdocument.enums;

import br.com.vanep.shared.document.enums.DocumentDataRequirements;

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
}
