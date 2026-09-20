package br.com.vanep.auth.token;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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
    return retryAfter(lastIssuedAt, issuedInWindow, now).isEmpty()
        ? AuthCodeIssueDecision.ISSUE
        : AuthCodeIssueDecision.SKIP;
  }

  /**
   * Same limits as {@link #decide}, for the callers that must answer the user instead of silently
   * skipping the send.
   *
   * @return empty when a new send is allowed; otherwise when it becomes allowed
   */
  public Optional<Instant> retryAfter(Instant lastIssuedAt, long issuedInWindow, Instant now) {
    if (issuedInWindow >= maxPerDay) {
      // A slot frees when the oldest send leaves the window; the newest one is a safe upper bound.
      Instant reference = lastIssuedAt != null ? lastIssuedAt : now;
      return Optional.of(reference.plus(DAILY_WINDOW));
    }
    if (lastIssuedAt != null && now.isBefore(lastIssuedAt.plus(resendCooldown))) {
      return Optional.of(lastIssuedAt.plus(resendCooldown));
    }
    return Optional.empty();
  }

  public Instant dailyWindowStart(Instant now) {
    return now.minus(DAILY_WINDOW);
  }
}
