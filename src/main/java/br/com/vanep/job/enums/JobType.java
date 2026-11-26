package br.com.vanep.job.enums;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public enum JobType {
  // In-queue retries cover a flaky FCM call; after that, a replay every 2 h, five times.
  SEND_PUSH(
      5,
      Duration.ofSeconds(10),
      Duration.ofMinutes(2),
      List.of(
          Duration.ofHours(2),
          Duration.ofHours(2),
          Duration.ofHours(2),
          Duration.ofHours(2),
          Duration.ofHours(2))),
  // No in-queue retry: the first failure goes to failed_jobs, which retries it three times.
  SEND_EMAIL(
      1,
      Duration.ZERO,
      Duration.ZERO,
      List.of(Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(6)));

  private static final int MAX_DOUBLINGS = 30;

  private final int maxAttempts;
  private final Duration backoffBase;
  private final Duration backoffMax;
  private final List<Duration> replayDelays;

  JobType(int maxAttempts, Duration backoffBase, Duration backoffMax, List<Duration> replayDelays) {
    this.maxAttempts = maxAttempts;
    this.backoffBase = backoffBase;
    this.backoffMax = backoffMax;
    this.replayDelays = replayDelays;
  }

  public int maxAttempts() {
    return maxAttempts;
  }

  public Duration backoffAfter(int failedAttempts) {
    int doublings = Math.clamp(failedAttempts - 1L, 0, MAX_DOUBLINGS);
    Duration delay = backoffBase.multipliedBy(1L << doublings);
    return delay.compareTo(backoffMax) > 0 ? backoffMax : delay;
  }

  // How long a failed job waits in failed_jobs before going back to the queue, given how many
  // replays already failed; empty once the schedule is exhausted.
  public Optional<Duration> replayDelayAfter(int failedReplays) {
    if (failedReplays < 0 || failedReplays >= replayDelays.size()) {
      return Optional.empty();
    }
    return Optional.of(replayDelays.get(failedReplays));
  }
}
