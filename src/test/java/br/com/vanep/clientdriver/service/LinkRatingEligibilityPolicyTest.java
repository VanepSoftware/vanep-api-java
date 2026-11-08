package br.com.vanep.clientdriver.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.contract.enums.ContractStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LinkRatingEligibilityPolicyTest {

  private final LinkRatingEligibilityPolicy policy = new LinkRatingEligibilityPolicy();

  @ParameterizedTest
  @EnumSource(
      value = ContractStatus.class,
      names = {"ACTIVE", "SUSPENDED", "ENDED", "TERMINATED", "SUPERSEDED"})
  void aContractThatReachedActiveAllowsRating(ContractStatus status) {
    assertThat(policy.allowsRating(List.of(status))).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ContractStatus.class,
      names = {"AWAITING_SIGNATURES", "SIGNED", "CANCELLED"})
  void aContractThatNeverReachedActiveDoesNotAllowRating(ContractStatus status) {
    assertThat(policy.allowsRating(List.of(status))).isFalse();
  }

  @Test
  void aLinkWithoutContractsDoesNotAllowRating() {
    assertThat(policy.allowsRating(List.of())).isFalse();
  }

  @Test
  void oneContractThatReachedActiveIsEnoughNextToOthersThatDidNot() {
    assertThat(
            policy.allowsRating(
                List.of(
                    ContractStatus.CANCELLED,
                    ContractStatus.ENDED,
                    ContractStatus.AWAITING_SIGNATURES)))
        .isTrue();
  }
}
