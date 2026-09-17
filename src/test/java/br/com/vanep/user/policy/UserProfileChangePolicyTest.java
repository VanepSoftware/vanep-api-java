package br.com.vanep.user.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserProfileChangePolicyTest {

  private static final int COOLDOWN_DAYS = 30;

  private UserProfileChangePolicy policy;

  @BeforeEach
  void setUp() {
    policy = new UserProfileChangePolicy(COOLDOWN_DAYS);
  }

  @Test
  void retryAfterWhenWithinCooldownWindow() {
    Instant lastChange = Instant.parse("2026-07-01T12:00:00Z");
    Instant now = Instant.parse("2026-07-15T12:00:00Z");
    Instant expectedRetry = lastChange.plus(Duration.ofDays(COOLDOWN_DAYS));

    Optional<Instant> retryAfter = policy.retryAfter(lastChange, now);

    assertThat(retryAfter).contains(expectedRetry);
  }

  @Test
  void allowWhenCooldownElapsed() {
    Instant lastChange = Instant.parse("2026-06-01T12:00:00Z");
    Instant now = Instant.parse("2026-07-15T12:00:00Z");

    Optional<Instant> retryAfter = policy.retryAfter(lastChange, now);

    assertThat(retryAfter).isEmpty();
  }

  @Test
  void allowWhenLastChangeIsNull() {
    Instant now = Instant.parse("2026-07-15T12:00:00Z");

    Optional<Instant> retryAfter = policy.retryAfter(null, now);

    assertThat(retryAfter).isEmpty();
  }

  @Test
  void allowWhenNowEqualsExactBoundary() {
    Instant lastChange = Instant.parse("2026-07-01T12:00:00Z");
    Instant now = lastChange.plus(Duration.ofDays(COOLDOWN_DAYS));

    Optional<Instant> retryAfter = policy.retryAfter(lastChange, now);

    assertThat(retryAfter).isEmpty();
  }
}
