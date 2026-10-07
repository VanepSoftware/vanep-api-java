package br.com.vanep.absence.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.absence.enums.AbsenceLeg;
import br.com.vanep.absence.enums.AbsenceScope;
import br.com.vanep.shared.enums.Shift;
import org.junit.jupiter.api.Test;

class AbsenceScopePolicyTest {

  private final AbsenceScopePolicy policy = new AbsenceScopePolicy(new AbsenceLegShiftPolicy());

  @Test
  void bothIsAllowedOnlyForFulltimeDependents() {
    assertThat(policy.allows(AbsenceScope.BOTH, Shift.FULLTIME)).isTrue();
    assertThat(policy.allows(AbsenceScope.BOTH, Shift.MORNING)).isFalse();
    assertThat(policy.allows(AbsenceScope.BOTH, Shift.AFTERNOON)).isFalse();
    assertThat(policy.allows(AbsenceScope.BOTH, Shift.NIGHT)).isFalse();
  }

  @Test
  void returnIsRefusedForMorningDependents() {
    assertThat(policy.allows(AbsenceScope.RETURN, Shift.MORNING)).isFalse();
    assertThat(policy.allows(AbsenceScope.OUTBOUND, Shift.MORNING)).isTrue();
  }

  @Test
  void outboundIsRefusedForAfternoonDependents() {
    assertThat(policy.allows(AbsenceScope.OUTBOUND, Shift.AFTERNOON)).isFalse();
    assertThat(policy.allows(AbsenceScope.RETURN, Shift.AFTERNOON)).isTrue();
  }

  @Test
  void nightDependentMayReportOutboundOnly() {
    assertThat(policy.allows(AbsenceScope.OUTBOUND, Shift.NIGHT)).isTrue();
    assertThat(policy.allows(AbsenceScope.RETURN, Shift.NIGHT)).isFalse();
  }

  @Test
  void expandTurnsBothIntoTwoLegs() {
    assertThat(policy.expand(AbsenceScope.BOTH))
        .containsExactly(AbsenceLeg.OUTBOUND, AbsenceLeg.RETURN);
    assertThat(policy.expand(AbsenceScope.OUTBOUND)).containsExactly(AbsenceLeg.OUTBOUND);
    assertThat(policy.expand(AbsenceScope.RETURN)).containsExactly(AbsenceLeg.RETURN);
  }
}
