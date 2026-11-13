package br.com.vanep.schooltransportcredential.enums;

public enum CredentialRequirement {
  DRIVER_LICENSE(false),
  SCHOOL_DRIVER_COURSE(false),
  CRIMINAL_RECORD(false),
  VEHICLE_REGISTRATION(false),
  VEHICLE_INSPECTION(false),
  SCHOOL_VEHICLE_AUTHORIZATION(false),
  // Some cities require it and others do not; the contract carries the driver's declaration.
  MUNICIPAL_AUTHORIZATION(true);

  private final boolean optional;

  CredentialRequirement(boolean optional) {
    this.optional = optional;
  }

  public boolean isOptional() {
    return optional;
  }
}
