package br.com.vanep.auth.token;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthCodeIssuePolicy {

  private static final Duration DAILY_WINDOW = Duration.ofHours(24);

  private final Duration resendCooldown;
  private final int maxPerDay;

  public AuthCodeIssuePolicy(
      @Value("${vanep.auth.code.resend-cooldown-seconds}") long resendCooldownSeconds,
      @Value("${vanep.auth.code.max-per-day}") int maxPerDay) {
    this.resendCooldown = Duration.ofSeconds(resendCooldownSeconds);
    this.maxPerDay = maxPerDay;
  }

  public AuthCodeIssueDecision decide(Instant lastIssuedAt, long issuedInWindow, Instant now) {
    if (issuedInWindow >= maxPerDay) {
      return AuthCodeIssueDecision.SKIP;
    }
    if (lastIssuedAt != null && now.isBefore(lastIssuedAt.plus(resendCooldown))) {
      return AuthCodeIssueDecision.SKIP;
    }
    return AuthCodeIssueDecision.ISSUE;
  }

  public Instant dailyWindowStart(Instant now) {
    return now.minus(DAILY_WINDOW);
  }
}
