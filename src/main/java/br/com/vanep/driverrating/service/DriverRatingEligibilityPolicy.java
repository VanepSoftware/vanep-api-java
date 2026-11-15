package br.com.vanep.driverrating.service;

import br.com.vanep.clientdriver.service.LinkRatingEligibilityPolicy;
import br.com.vanep.contract.enums.ContractStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DriverRatingEligibilityPolicy {

  static final Duration MINIMUM_LINK_AGE = Duration.ofMinutes(5);

  private final LinkRatingEligibilityPolicy linkEligibility;

  public DriverRatingEligibilityPolicy(LinkRatingEligibilityPolicy linkEligibility) {
    this.linkEligibility = linkEligibility;
  }

  public boolean canRate(
      List<ContractStatus> contractStatuses, Instant linkCreatedAt, Instant now) {
    return hasContractThatReachedActive(contractStatuses) && isLinkOldEnough(linkCreatedAt, now);
  }

  public boolean hasContractThatReachedActive(List<ContractStatus> contractStatuses) {
    return linkEligibility.allowsRating(contractStatuses);
  }

  public boolean isLinkOldEnough(Instant linkCreatedAt, Instant now) {
    return !linkCreatedAt.plus(MINIMUM_LINK_AGE).isAfter(now);
  }
}
