package br.com.vanep.user.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UserProfileChangePolicy {

  private final int cooldownDays;

  public UserProfileChangePolicy(
      @Value("${vanep.profile.change-cooldown-days:30}") int cooldownDays) {
    this.cooldownDays = cooldownDays;
  }

  /**
   * @return empty if the change is allowed; otherwise the Instant when a new change becomes allowed
   */
  public Optional<Instant> retryAfter(Instant lastChangeAt, Instant now) {
    if (lastChangeAt == null) {
      return Optional.empty();
    }
    Instant availableAt = lastChangeAt.plus(Duration.ofDays(cooldownDays));
    if (now.isBefore(availableAt)) {
      return Optional.of(availableAt);
    }
    return Optional.empty();
  }

  public int cooldownDays() {
    return cooldownDays;
  }
}
