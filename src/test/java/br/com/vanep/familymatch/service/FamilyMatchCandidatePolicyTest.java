package br.com.vanep.familymatch.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.unlinkedpassenger.model.UnlinkedPassengerModel;
import java.util.List;
import org.junit.jupiter.api.Test;

class FamilyMatchCandidatePolicyTest {

  private final FamilyMatchCandidatePolicy policy = new FamilyMatchCandidatePolicy();

  @Test
  void picksTheOnlyPassengerWithTheSameFirstNameIgnoringAccentsAndCase() {
    UnlinkedPassengerModel rafael = passenger("RAFAÉL");

    assertThat(policy.pickCandidate("Rafael Costa", List.of(rafael, passenger("Laura"))))
        .containsSame(rafael);
  }

  @Test
  void picksNobodyWhenTwoPassengersShareTheFirstName() {
    assertThat(
            policy.pickCandidate(
                "Rafael Costa", List.of(passenger("Rafael Lima"), passenger("Rafael Souza"))))
        .isEmpty();
  }

  @Test
  void picksNobodyWithoutACandidateOrWithABlankName() {
    assertThat(policy.pickCandidate("Rafael", List.of(passenger("Laura")))).isEmpty();
    assertThat(policy.pickCandidate("  ", List.of(passenger("Laura")))).isEmpty();
    assertThat(policy.pickCandidate(null, List.of(passenger("Laura")))).isEmpty();
  }

  @Test
  void normalizesOnlyTheFirstName() {
    assertThat(policy.normalizeFirstName("  João   Pedro ")).isEqualTo("joao");
  }

  UnlinkedPassengerModel passenger(String name) {
    UnlinkedPassengerModel passenger = new UnlinkedPassengerModel();
    passenger.setName(name);
    return passenger;
  }
}
