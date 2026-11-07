package br.com.vanep.proposal.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.proposal.enums.ProposalStatus;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProposalTransitionPolicyTest {

  private static final Set<ProposalStatus> FROM_SENT =
      Set.of(
          ProposalStatus.ACCEPTED,
          ProposalStatus.SUPERSEDED,
          ProposalStatus.DECLINED,
          ProposalStatus.EXPIRED,
          ProposalStatus.WITHDRAWN);

  private final ProposalTransitionPolicy policy = new ProposalTransitionPolicy();

  static Stream<Arguments> everyPair() {
    return Arrays.stream(ProposalStatus.values())
        .flatMap(
            from ->
                Arrays.stream(ProposalStatus.values())
                    .map(
                        to ->
                            Arguments.of(
                                from, to, from == ProposalStatus.SENT && FROM_SENT.contains(to))));
  }

  @ParameterizedTest(name = "{0} -> {1} allowed: {2}")
  @MethodSource("everyPair")
  void allowsOnlyTheFiveWaysOutOfSent(ProposalStatus from, ProposalStatus to, boolean expected) {
    assertThat(policy.allows(from, to)).isEqualTo(expected);
  }

  @Test
  void everyStatusButSentIsTerminal() {
    for (ProposalStatus status : ProposalStatus.values()) {
      assertThat(policy.nextStatusesOf(status).isEmpty())
          .withFailMessage("%s terminal", status)
          .isEqualTo(status != ProposalStatus.SENT);
    }
  }
}
