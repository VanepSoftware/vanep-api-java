package br.com.vanep.contract.service;

import br.com.vanep.contract.enums.ContractStatus;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ContractTransitionPolicy {

  public boolean allows(ContractStatus from, ContractStatus to) {
    return nextStatusesOf(from).contains(to);
  }

  Set<ContractStatus> nextStatusesOf(ContractStatus from) {
    return switch (from) {
      case AWAITING_SIGNATURES -> Set.of(ContractStatus.SIGNED, ContractStatus.CANCELLED);
      case SIGNED -> Set.of(ContractStatus.ACTIVE, ContractStatus.CANCELLED);
      case ACTIVE ->
          Set.of(
              ContractStatus.SUSPENDED,
              ContractStatus.ENDED,
              ContractStatus.TERMINATED,
              ContractStatus.SUPERSEDED);
      case SUSPENDED ->
          Set.of(ContractStatus.ACTIVE, ContractStatus.ENDED, ContractStatus.TERMINATED);
      case ENDED, TERMINATED, CANCELLED, SUPERSEDED -> Set.of();
    };
  }
}
