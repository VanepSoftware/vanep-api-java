package br.com.vanep.contract.enums;

public enum ContractTermsViolation {
  PERIOD_INVALID("contract.period.invalid"),
  PERIOD_TOO_LONG("contract.period.too_long");

  private final String messageKey;

  ContractTermsViolation(String messageKey) {
    this.messageKey = messageKey;
  }

  public String messageKey() {
    return messageKey;
  }
}
