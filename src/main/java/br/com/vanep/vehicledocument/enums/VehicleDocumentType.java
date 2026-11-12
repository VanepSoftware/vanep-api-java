package br.com.vanep.vehicledocument.enums;

import br.com.vanep.shared.document.enums.DocumentDataRequirements;

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
}
