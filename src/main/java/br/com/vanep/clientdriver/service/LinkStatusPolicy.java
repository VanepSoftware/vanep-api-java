package br.com.vanep.clientdriver.service;

import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.contract.enums.ContractStatus;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LinkStatusPolicy {

  static final Set<ContractStatus> CONTRACT_STATUSES_THAT_KEEP_THE_LINK_ACTIVE =
      Set.of(ContractStatus.SIGNED, ContractStatus.ACTIVE, ContractStatus.SUSPENDED);

  public RelationshipStatus deriveStatus(
      RelationshipStatus current, List<ContractStatus> contractStatuses) {
    if (current == RelationshipStatus.BLOCKED) {
      return RelationshipStatus.BLOCKED;
    }
    if (contractStatuses.isEmpty()) {
      return RelationshipStatus.PENDING;
    }
    return hasContractThatKeepsTheLinkActive(contractStatuses)
        ? RelationshipStatus.ACTIVE
        : RelationshipStatus.INACTIVE;
  }

  boolean hasContractThatKeepsTheLinkActive(List<ContractStatus> contractStatuses) {
    return contractStatuses.stream()
        .anyMatch(status -> CONTRACT_STATUSES_THAT_KEEP_THE_LINK_ACTIVE.contains(status));
  }
}
