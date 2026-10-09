package br.com.vanep.contract.enums;

import java.util.Set;

public enum ContractStatus {
  AWAITING_SIGNATURES,
  SIGNED,
  ACTIVE,
  SUSPENDED,
  ENDED,
  TERMINATED,
  CANCELLED,
  SUPERSEDED;

  public static final Set<ContractStatus> SIGNED_AND_NOT_ENDED = Set.of(SIGNED, ACTIVE, SUSPENDED);
}
