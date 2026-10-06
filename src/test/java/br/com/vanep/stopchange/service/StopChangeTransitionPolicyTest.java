package br.com.vanep.stopchange.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.stopchange.enums.StopChangeStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StopChangeTransitionPolicyTest {

  private final StopChangeTransitionPolicy policy = new StopChangeTransitionPolicy();

  @Test
  void acceptsOnlyLegalTransitionsAcrossTheWholeMatrix() {
    Set<String> legal =
        Set.of("PENDING->APPROVED", "PENDING->REJECTED", "PENDING->CANCELLED", "PENDING->EXPIRED");

    for (StopChangeStatus from : StopChangeStatus.values()) {
      for (StopChangeStatus to : StopChangeStatus.values()) {
        boolean expected = legal.contains(from + "->" + to);
        assertThat(policy.allows(from, to))
            .withFailMessage("transition %s -> %s should be %s", from, to, expected)
            .isEqualTo(expected);
      }
    }
  }

  @Test
  void onlyPendingCanBeApproved() {
    assertThat(policy.canApprove(StopChangeStatus.PENDING)).isTrue();
    assertThat(policy.canApprove(StopChangeStatus.APPROVED)).isFalse();
    assertThat(policy.canApprove(StopChangeStatus.REJECTED)).isFalse();
    assertThat(policy.canApprove(StopChangeStatus.CANCELLED)).isFalse();
    assertThat(policy.canApprove(StopChangeStatus.EXPIRED)).isFalse();
  }

  @Test
  void onlyPendingCanBeRejected() {
    assertThat(policy.canReject(StopChangeStatus.PENDING)).isTrue();
    assertThat(policy.canReject(StopChangeStatus.APPROVED)).isFalse();
    assertThat(policy.canReject(StopChangeStatus.REJECTED)).isFalse();
    assertThat(policy.canReject(StopChangeStatus.CANCELLED)).isFalse();
    assertThat(policy.canReject(StopChangeStatus.EXPIRED)).isFalse();
  }

  @Test
  void onlyPendingCanBeCancelled() {
    assertThat(policy.canCancel(StopChangeStatus.PENDING)).isTrue();
    assertThat(policy.canCancel(StopChangeStatus.APPROVED)).isFalse();
    assertThat(policy.canCancel(StopChangeStatus.REJECTED)).isFalse();
    assertThat(policy.canCancel(StopChangeStatus.CANCELLED)).isFalse();
    assertThat(policy.canCancel(StopChangeStatus.EXPIRED)).isFalse();
  }

  @Test
  void onlyPendingCanBeExpired() {
    assertThat(policy.canExpire(StopChangeStatus.PENDING)).isTrue();
    assertThat(policy.canExpire(StopChangeStatus.APPROVED)).isFalse();
    assertThat(policy.canExpire(StopChangeStatus.REJECTED)).isFalse();
    assertThat(policy.canExpire(StopChangeStatus.CANCELLED)).isFalse();
    assertThat(policy.canExpire(StopChangeStatus.EXPIRED)).isFalse();
  }
}
