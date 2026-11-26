package br.com.vanep.job.enums;

import java.time.Duration;

public enum JobType {
  SEND_PUSH(5, Duration.ofSeconds(10), Duration.ofMinutes(2));

  private static final int MAX_DOUBLINGS = 30;

  private final int maxAttempts;
  private final Duration backoffBase;
  private final Duration backoffMax;

  JobType(int maxAttempts, Duration backoffBase, Duration backoffMax) {
    this.maxAttempts = maxAttempts;
    this.backoffBase = backoffBase;
    this.backoffMax = backoffMax;
  }

  public int maxAttempts() {
    return maxAttempts;
  }

  public Duration backoffAfter(int failedAttempts) {
    int doublings = Math.clamp(failedAttempts - 1L, 0, MAX_DOUBLINGS);
    Duration delay = backoffBase.multipliedBy(1L << doublings);
    return delay.compareTo(backoffMax) > 0 ? backoffMax : delay;
  }
}
