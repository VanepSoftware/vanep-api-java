package br.com.vanep.clientdriver.service;

import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.contract.enums.ContractStatus;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LinkStatusPolicy {

  public RelationshipStatus deriveStatus(
      RelationshipStatus current, List<ContractStatus> contractStatuses) {
    if (current == RelationshipStatus.BLOCKED) {
      return RelationshipStatus.BLOCKED;
    }
    if (contractStatuses.isEmpty()) {
      return RelationshipStatus.PENDING;
    }
    return hasSignedContractNotEnded(contractStatuses)
        ? RelationshipStatus.ACTIVE
        : RelationshipStatus.INACTIVE;
  }

  boolean hasSignedContractNotEnded(List<ContractStatus> contractStatuses) {
    return contractStatuses.stream()
        .anyMatch(status -> ContractStatus.SIGNED_AND_NOT_ENDED.contains(status));
  }
}
