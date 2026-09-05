package br.com.vanep.user.enums;

public enum OnboardingStep {
  PERSONAL_ADDRESS("PERSONAL_ADDRESS"),

  SERVICE_AREA("SERVICE_AREA");

  private final String value;

  OnboardingStep(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
