package br.com.vanep.servicerequest.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ServiceRequestTransitionPolicyTest {

  private static final Set<String> LEGAL =
      Set.of(
          "OPEN->ANSWERED",
          "OPEN->DECLINED",
          "OPEN->WITHDRAWN",
          "OPEN->EXPIRED",
          "ANSWERED->OPEN",
          "ANSWERED->CONVERTED",
          "ANSWERED->DECLINED",
          "ANSWERED->WITHDRAWN");

  private final ServiceRequestTransitionPolicy policy = new ServiceRequestTransitionPolicy();

  static Stream<Arguments> everyPair() {
    return Arrays.stream(ServiceRequestStatus.values())
        .flatMap(
            from ->
                Arrays.stream(ServiceRequestStatus.values())
                    .map(to -> Arguments.of(from, to, LEGAL.contains(from + "->" + to))));
  }

  @ParameterizedTest(name = "{0} -> {1} allowed: {2}")
  @MethodSource("everyPair")
  void allowsExactlyTheTransitionsOfTheSpec(
      ServiceRequestStatus from, ServiceRequestStatus to, boolean expected) {
    assertThat(policy.allows(from, to)).isEqualTo(expected);
  }

  @Test
  void theOpenStatusesAreExactlyTheOnesThatCanStillMove() {
    Set<ServiceRequestStatus> movable = EnumSet.noneOf(ServiceRequestStatus.class);
    for (ServiceRequestStatus status : ServiceRequestStatus.values()) {
      if (!policy.nextStatusesOf(status).isEmpty()) {
        movable.add(status);
      }
    }

    assertThat(ServiceRequestStatus.OPEN_STATUSES)
        .containsExactlyInAnyOrder(ServiceRequestStatus.OPEN, ServiceRequestStatus.ANSWERED)
        .containsExactlyInAnyOrderElementsOf(movable);
  }
}
