package br.com.vanep.absence.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.trip.enums.TripStatus;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AbsenceUndoPolicyTest {

  private final AbsenceUndoPolicy policy = new AbsenceUndoPolicy();

  @Test
  void missingTripOrScheduledMayBeUndone() {
    assertThat(policy.allows(null)).isTrue();
    assertThat(policy.allows(TripStatus.SCHEDULED)).isTrue();
  }

  @Test
  void startedFinishedOrCancelledMayNotBeUndone() {
    assertThat(policy.allows(TripStatus.IN_PROGRESS)).isFalse();
    assertThat(policy.allows(TripStatus.COMPLETED)).isFalse();
    assertThat(policy.allows(TripStatus.CANCELLED)).isFalse();
  }

  @Test
  void bothLegsAreAllOrNothing() {
    assertThat(policy.allowsAll(Arrays.asList(null, TripStatus.SCHEDULED))).isTrue();
    assertThat(policy.allowsAll(List.of(TripStatus.SCHEDULED, TripStatus.SCHEDULED))).isTrue();
    assertThat(policy.allowsAll(Arrays.asList(TripStatus.SCHEDULED, TripStatus.IN_PROGRESS)))
        .isFalse();
    assertThat(policy.allowsAll(Arrays.asList(null, TripStatus.COMPLETED))).isFalse();
    assertThat(policy.allowsAll(List.of())).isTrue();
  }
}
