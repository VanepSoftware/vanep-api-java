package br.com.vanep.clientdriver.service;

import br.com.vanep.contract.enums.ContractStatus;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LinkRatingEligibilityPolicy {

  public boolean allowsRating(List<ContractStatus> contractStatuses) {
    return contractStatuses.stream()
        .anyMatch(status -> ContractStatus.REACHED_ACTIVE.contains(status));
  }
}
