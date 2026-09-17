package br.com.vanep.trip.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.trip.enums.TripStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TripTransitionPolicyTest {

  private final TripTransitionPolicy policy = new TripTransitionPolicy();

  @Test
  void acceptsOnlyTheTwoLegalTransitionsAcrossTheWholeMatrix() {
    Set<String> legal = Set.of("SCHEDULED->IN_PROGRESS", "IN_PROGRESS->COMPLETED");

    for (TripStatus from : TripStatus.values()) {
      for (TripStatus to : TripStatus.values()) {
        boolean expected = legal.contains(from + "->" + to);
        assertThat(policy.allows(from, to))
            .withFailMessage("transition %s -> %s should be %s", from, to, expected)
            .isEqualTo(expected);
      }
    }
  }

  @Test
  void onlyScheduledCanStart() {
    assertThat(policy.canStart(TripStatus.SCHEDULED)).isTrue();
    assertThat(policy.canStart(TripStatus.IN_PROGRESS)).isFalse();
    assertThat(policy.canStart(TripStatus.COMPLETED)).isFalse();
    assertThat(policy.canStart(TripStatus.CANCELLED)).isFalse();
  }

  @Test
  void onlyInProgressCanFinish() {
    assertThat(policy.canFinish(TripStatus.IN_PROGRESS)).isTrue();
    assertThat(policy.canFinish(TripStatus.SCHEDULED)).isFalse();
    assertThat(policy.canFinish(TripStatus.COMPLETED)).isFalse();
    assertThat(policy.canFinish(TripStatus.CANCELLED)).isFalse();
  }
}
