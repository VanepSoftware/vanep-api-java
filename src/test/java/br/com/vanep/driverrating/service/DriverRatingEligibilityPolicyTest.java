package br.com.vanep.driverrating.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.clientdriver.service.LinkRatingEligibilityPolicy;
import br.com.vanep.contract.enums.ContractStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DriverRatingEligibilityPolicyTest {

  private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

  private final DriverRatingEligibilityPolicy policy =
      new DriverRatingEligibilityPolicy(new LinkRatingEligibilityPolicy());

  @Test
  void aLinkWhoseContractEndedHasAContractThatReachedActive() {
    assertThat(policy.hasContractThatReachedActive(List.of(ContractStatus.ENDED))).isTrue();
  }

  @Test
  void aLinkWithOnlyACancelledContractHasNoContractThatReachedActive() {
    assertThat(policy.hasContractThatReachedActive(List.of(ContractStatus.CANCELLED))).isFalse();
  }

  @Test
  void aLinkWithoutContractsHasNoContractThatReachedActive() {
    assertThat(policy.hasContractThatReachedActive(List.of())).isFalse();
  }

  @Test
  void aLinkCreatedFourMinutesAgoIsTooRecent() {
    assertThat(policy.isLinkOldEnough(NOW.minus(Duration.ofMinutes(4)), NOW)).isFalse();
  }

  @Test
  void aLinkCreatedExactlyFiveMinutesAgoIsOldEnough() {
    assertThat(policy.isLinkOldEnough(NOW.minus(Duration.ofMinutes(5)), NOW)).isTrue();
  }

  @Test
  void aLinkCreatedADayAgoIsOldEnough() {
    assertThat(policy.isLinkOldEnough(NOW.minus(Duration.ofDays(1)), NOW)).isTrue();
  }

  @Test
  void canRateRequiresAContractThatReachedActiveAndALinkOldEnough() {
    Instant longAgo = NOW.minus(Duration.ofDays(1));
    Instant justNow = NOW.minus(Duration.ofMinutes(1));
    List<ContractStatus> ended = List.of(ContractStatus.ENDED);

    assertThat(policy.canRate(ended, longAgo, NOW)).isTrue();
    assertThat(policy.canRate(ended, justNow, NOW)).isFalse();
    assertThat(policy.canRate(List.of(ContractStatus.SIGNED), longAgo, NOW)).isFalse();
    assertThat(policy.canRate(List.of(), longAgo, NOW)).isFalse();
  }
}
