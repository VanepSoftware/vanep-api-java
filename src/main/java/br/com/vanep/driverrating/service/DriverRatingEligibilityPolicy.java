package br.com.vanep.driverrating.service;

import br.com.vanep.clientdriver.enums.RelationshipStatus;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class DriverRatingEligibilityPolicy {

  static final Duration MINIMUM_LINK_AGE = Duration.ofMinutes(5);

  public boolean canRate(RelationshipStatus status, Instant linkCreatedAt, Instant now) {
    return isLinkActive(status) && isLinkOldEnough(linkCreatedAt, now);
  }

  public boolean isLinkActive(RelationshipStatus status) {
    return status == RelationshipStatus.ACTIVE;
  }

  public boolean isLinkOldEnough(Instant linkCreatedAt, Instant now) {
    return !linkCreatedAt.plus(MINIMUM_LINK_AGE).isAfter(now);
  }
}
