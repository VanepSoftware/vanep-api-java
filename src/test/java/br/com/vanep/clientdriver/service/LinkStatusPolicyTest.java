package br.com.vanep.clientdriver.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.contract.enums.ContractStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkStatusPolicyTest {

  private static final List<RelationshipStatus> DERIVED_STATUSES =
      List.of(RelationshipStatus.PENDING, RelationshipStatus.ACTIVE, RelationshipStatus.INACTIVE);

  private final LinkStatusPolicy policy = new LinkStatusPolicy();

  @Test
  void aLinkWithoutContractsIsPending() {
    assertDerives(List.of(), RelationshipStatus.PENDING);
  }

  @Test
  void aLinkWithOnlyEndedContractsIsInactive() {
    assertDerives(List.of(ContractStatus.ENDED), RelationshipStatus.INACTIVE);
  }

  @Test
  void anActiveContractNextToAnEndedOneActivatesTheLink() {
    assertDerives(List.of(ContractStatus.ENDED, ContractStatus.ACTIVE), RelationshipStatus.ACTIVE);
  }

  @Test
  void aSuspendedContractKeepsTheLinkActive() {
    assertDerives(List.of(ContractStatus.SUSPENDED), RelationshipStatus.ACTIVE);
  }

  @Test
  void aSignedContractActivatesTheLink() {
    assertDerives(List.of(ContractStatus.SIGNED), RelationshipStatus.ACTIVE);
  }

  @Test
  void aBlockedLinkStaysBlockedWhateverItsContracts() {
    List<List<ContractStatus>> contractSets =
        List.of(
            List.of(),
            List.of(ContractStatus.ENDED),
            List.of(ContractStatus.ENDED, ContractStatus.ACTIVE),
            List.of(ContractStatus.SUSPENDED),
            List.of(ContractStatus.SIGNED));

    for (List<ContractStatus> contracts : contractSets) {
      assertThat(policy.deriveStatus(RelationshipStatus.BLOCKED, contracts))
          .withFailMessage("a blocked link with contracts %s should stay blocked", contracts)
          .isEqualTo(RelationshipStatus.BLOCKED);
    }
  }

  private void assertDerives(List<ContractStatus> contracts, RelationshipStatus expected) {
    for (RelationshipStatus current : DERIVED_STATUSES) {
      assertThat(policy.deriveStatus(current, contracts))
          .withFailMessage(
              "a %s link with contracts %s should become %s", current, contracts, expected)
          .isEqualTo(expected);
    }
  }
}
