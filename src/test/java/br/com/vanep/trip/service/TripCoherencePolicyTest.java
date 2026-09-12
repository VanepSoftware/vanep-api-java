package br.com.vanep.trip.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.trip.enums.TripCoherenceViolation;
import br.com.vanep.trip.enums.TripStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TripCoherencePolicyTest {

  private static final Instant EARLY = Instant.parse("2026-09-10T09:00:00Z");
  private static final Instant LATE = Instant.parse("2026-09-10T12:00:00Z");

  private final TripCoherencePolicy policy = new TripCoherencePolicy();

  @Test
  void scheduledWithoutTimestampsIsCoherent() {
    assertThat(policy.validate(TripStatus.SCHEDULED, null, null)).isEmpty();
  }

  @Test
  void scheduledWithAStartIsRefused() {
    assertThat(policy.validate(TripStatus.SCHEDULED, EARLY, null))
        .contains(TripCoherenceViolation.STARTED_AT_FORBIDDEN);
  }

  @Test
  void inProgressWithAStartIsCoherent() {
    assertThat(policy.validate(TripStatus.IN_PROGRESS, EARLY, null)).isEmpty();
  }

  @Test
  void inProgressWithoutAStartIsRefused() {
    assertThat(policy.validate(TripStatus.IN_PROGRESS, null, null))
        .contains(TripCoherenceViolation.STARTED_AT_REQUIRED);
  }

  @Test
  void inProgressAlreadyFinishedIsRefused() {
    assertThat(policy.validate(TripStatus.IN_PROGRESS, EARLY, LATE))
        .contains(TripCoherenceViolation.FINISHED_AT_FORBIDDEN);
  }

  @Test
  void completedWithBothTimestampsIsCoherent() {
    assertThat(policy.validate(TripStatus.COMPLETED, EARLY, LATE)).isEmpty();
  }

  @Test
  void completedWithoutAFinishIsRefused() {
    assertThat(policy.validate(TripStatus.COMPLETED, EARLY, null))
        .contains(TripCoherenceViolation.FINISHED_AT_REQUIRED);
  }

  @Test
  void completedWithoutAStartIsRefused() {
    assertThat(policy.validate(TripStatus.COMPLETED, null, LATE))
        .contains(TripCoherenceViolation.STARTED_AT_REQUIRED);
  }

  @Test
  void finishingBeforeStartingIsRefused() {
    assertThat(policy.validate(TripStatus.COMPLETED, LATE, EARLY))
        .contains(TripCoherenceViolation.FINISHED_BEFORE_STARTED);
  }

  @Test
  void finishingAtTheSameInstantItStartedIsCoherent() {
    assertThat(policy.validate(TripStatus.COMPLETED, EARLY, EARLY)).isEmpty();
  }

  @Test
  void cancelledAcceptsEveryCoherentCombination() {
    assertThat(policy.validate(TripStatus.CANCELLED, null, null)).isEmpty();
    assertThat(policy.validate(TripStatus.CANCELLED, EARLY, null)).isEmpty();
    assertThat(policy.validate(TripStatus.CANCELLED, EARLY, LATE)).isEmpty();
  }

  @Test
  void cancelledStillRefusesAFinishWithoutAStart() {
    assertThat(policy.validate(TripStatus.CANCELLED, null, LATE))
        .contains(TripCoherenceViolation.STARTED_AT_REQUIRED);
  }

  @Test
  void cancelledStillRefusesFinishingBeforeStarting() {
    assertThat(policy.validate(TripStatus.CANCELLED, LATE, EARLY))
        .contains(TripCoherenceViolation.FINISHED_BEFORE_STARTED);
  }

  @Test
  void decidesWithoutASpringContextOrPersistence() {
    TripCoherencePolicy standalone = new TripCoherencePolicy();

    assertThat(standalone.validate(TripStatus.COMPLETED, null, null))
        .contains(TripCoherenceViolation.STARTED_AT_REQUIRED);
  }
}
