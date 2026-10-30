package br.com.vanep.contract.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.contract.enums.ContractStatus;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContractTransitionPolicyTest {

  private final ContractTransitionPolicy policy = new ContractTransitionPolicy();

  @Test
  void acceptsExactlyTheLegalTransitionsAcrossTheWholeMatrix() {
    Set<String> legal =
        Set.of(
            "AWAITING_SIGNATURES->SIGNED",
            "AWAITING_SIGNATURES->CANCELLED",
            "SIGNED->ACTIVE",
            "SIGNED->CANCELLED",
            "ACTIVE->SUSPENDED",
            "ACTIVE->ENDED",
            "ACTIVE->TERMINATED",
            "ACTIVE->SUPERSEDED",
            "SUSPENDED->ACTIVE",
            "SUSPENDED->ENDED",
            "SUSPENDED->TERMINATED");
    int checkedPairs = 0;

    for (ContractStatus from : ContractStatus.values()) {
      for (ContractStatus to : ContractStatus.values()) {
        boolean expected = legal.contains(from + "->" + to);
        assertThat(policy.allows(from, to))
            .withFailMessage("transition %s -> %s should be %s", from, to, expected)
            .isEqualTo(expected);
        checkedPairs++;
      }
    }

    assertThat(checkedPairs).isEqualTo(64);
  }

  @Test
  void terminalStatusesLeadNowhere() {
    Set<ContractStatus> terminals =
        Set.of(
            ContractStatus.ENDED,
            ContractStatus.TERMINATED,
            ContractStatus.CANCELLED,
            ContractStatus.SUPERSEDED);

    for (ContractStatus terminal : terminals) {
      for (ContractStatus to : ContractStatus.values()) {
        assertThat(policy.allows(terminal, to))
            .withFailMessage("terminal %s should not move to %s", terminal, to)
            .isFalse();
      }
    }
  }

  @Test
  void reachedActiveHoldsExactlyTheStatusesReachableOnlyThroughActive() {
    Set<ContractStatus> reachableWithoutActive = EnumSet.of(ContractStatus.AWAITING_SIGNATURES);
    Deque<ContractStatus> pending = new ArrayDeque<>(reachableWithoutActive);
    while (!pending.isEmpty()) {
      for (ContractStatus next : policy.nextStatusesOf(pending.pop())) {
        if (next != ContractStatus.ACTIVE && reachableWithoutActive.add(next)) {
          pending.push(next);
        }
      }
    }

    assertThat(ContractStatus.REACHED_ACTIVE)
        .containsExactlyInAnyOrderElementsOf(
            EnumSet.complementOf(EnumSet.copyOf(reachableWithoutActive)));
  }
}
