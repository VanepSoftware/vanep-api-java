package br.com.vanep.driverstudent.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.driverstudent.enums.FamilyStatus;
import br.com.vanep.familymatch.dto.FamilyMatchResponseDTO;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerFamilyDTO;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FamilyStatusPolicyTest {

  private static final FamilyMatchResponseDTO MATCH =
      new FamilyMatchResponseDTO("match", "rafael", "Rafael Costa", 9, "Maria Costa");

  private final FamilyStatusPolicy policy = new FamilyStatusPolicy();

  @Test
  void aMergedPassengerIsWithFamily() {
    assertThat(policy.deriveUnlinkedStatus(family("rafael", Instant.now(), null)))
        .isEqualTo(FamilyStatus.WITH_FAMILY);
  }

  @Test
  void aPendingMatchMeansTheFamilyJoined() {
    assertThat(policy.deriveUnlinkedStatus(family(null, Instant.now(), MATCH)))
        .isEqualTo(FamilyStatus.FAMILY_JOINED);
  }

  @Test
  void anInviteWithoutAMatchIsInvited() {
    assertThat(policy.deriveUnlinkedStatus(family(null, Instant.now(), null)))
        .isEqualTo(FamilyStatus.INVITED);
  }

  @Test
  void otherwiseNotInvited() {
    assertThat(policy.deriveUnlinkedStatus(UnlinkedPassengerFamilyDTO.NONE))
        .isEqualTo(FamilyStatus.NOT_INVITED);
  }

  UnlinkedPassengerFamilyDTO family(
      String dependentToken, Instant invitedAt, FamilyMatchResponseDTO match) {
    return new UnlinkedPassengerFamilyDTO(null, dependentToken, invitedAt, match);
  }
}
