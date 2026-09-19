package br.com.vanep.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthCodeIssuePolicyTest {

  private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

  private final AuthCodeIssuePolicy policy = new AuthCodeIssuePolicy(60, 10);

  @Test
  void issuesWhenThereIsNoPreviousCode() {
    assertThat(policy.decide(null, 0, NOW)).isEqualTo(AuthCodeIssueDecision.ISSUE);
  }

  @Test
  void skipsInsideTheResendCooldown() {
    Instant lastIssuedAt = NOW.minus(Duration.ofSeconds(59));

    assertThat(policy.decide(lastIssuedAt, 1, NOW)).isEqualTo(AuthCodeIssueDecision.SKIP);
  }

  @Test
  void issuesOnceTheCooldownHasPassed() {
    Instant lastIssuedAt = NOW.minus(Duration.ofSeconds(60));

    assertThat(policy.decide(lastIssuedAt, 1, NOW)).isEqualTo(AuthCodeIssueDecision.ISSUE);
  }

  @Test
  void skipsWhenTheDailyCapIsReached() {
    Instant lastIssuedAt = NOW.minus(Duration.ofHours(2));

    assertThat(policy.decide(lastIssuedAt, 10, NOW)).isEqualTo(AuthCodeIssueDecision.SKIP);
  }

  @Test
  void issuesBelowTheDailyCap() {
    Instant lastIssuedAt = NOW.minus(Duration.ofHours(2));

    assertThat(policy.decide(lastIssuedAt, 9, NOW)).isEqualTo(AuthCodeIssueDecision.ISSUE);
  }

  @Test
  void countsFromTheStartOfTheRollingWindow() {
    assertThat(policy.dailyWindowStart(NOW)).isEqualTo(NOW.minus(Duration.ofHours(24)));
  }

  @Test
  void retryAfterIsEmptyWhenASendIsAllowed() {
    assertThat(policy.retryAfter(null, 0, NOW)).isEmpty();
    assertThat(policy.retryAfter(NOW.minus(Duration.ofSeconds(60)), 1, NOW)).isEmpty();
  }

  @Test
  void retryAfterEndsTheResendCooldown() {
    Instant lastIssuedAt = NOW.minus(Duration.ofSeconds(59));

    assertThat(policy.retryAfter(lastIssuedAt, 1, NOW))
        .contains(lastIssuedAt.plus(Duration.ofSeconds(60)));
  }

  @Test
  void retryAfterClearsTheDailyCapOneWindowAfterTheLastSend() {
    Instant lastIssuedAt = NOW.minus(Duration.ofHours(2));

    assertThat(policy.retryAfter(lastIssuedAt, 10, NOW))
        .contains(lastIssuedAt.plus(Duration.ofHours(24)));
  }
}
