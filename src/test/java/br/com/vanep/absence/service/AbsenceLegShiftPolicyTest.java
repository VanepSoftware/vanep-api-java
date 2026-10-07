package br.com.vanep.absence.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.absence.enums.AbsenceLeg;
import br.com.vanep.shared.enums.Shift;
import org.junit.jupiter.api.Test;

class AbsenceLegShiftPolicyTest {

  private final AbsenceLegShiftPolicy policy = new AbsenceLegShiftPolicy();

  @Test
  void morningAndNightMapToOutbound() {
    assertThat(policy.uniqueLegOf(Shift.MORNING)).contains(AbsenceLeg.OUTBOUND);
    assertThat(policy.uniqueLegOf(Shift.NIGHT)).contains(AbsenceLeg.OUTBOUND);
    assertThat(policy.covers(Shift.MORNING, AbsenceLeg.OUTBOUND)).isTrue();
    assertThat(policy.covers(Shift.NIGHT, AbsenceLeg.OUTBOUND)).isTrue();
    assertThat(policy.covers(Shift.MORNING, AbsenceLeg.RETURN)).isFalse();
    assertThat(policy.covers(Shift.NIGHT, AbsenceLeg.RETURN)).isFalse();
  }

  @Test
  void afternoonMapsToReturn() {
    assertThat(policy.uniqueLegOf(Shift.AFTERNOON)).contains(AbsenceLeg.RETURN);
    assertThat(policy.covers(Shift.AFTERNOON, AbsenceLeg.RETURN)).isTrue();
    assertThat(policy.covers(Shift.AFTERNOON, AbsenceLeg.OUTBOUND)).isFalse();
  }

  @Test
  void fulltimeTripAcceptsBothLegs() {
    assertThat(policy.uniqueLegOf(Shift.FULLTIME)).isEmpty();
    assertThat(policy.covers(Shift.FULLTIME, AbsenceLeg.OUTBOUND)).isTrue();
    assertThat(policy.covers(Shift.FULLTIME, AbsenceLeg.RETURN)).isTrue();
  }

  @Test
  void nullShiftOrLegIsNotCovered() {
    assertThat(policy.covers(null, AbsenceLeg.OUTBOUND)).isFalse();
    assertThat(policy.covers(Shift.MORNING, null)).isFalse();
    assertThat(policy.uniqueLegOf(null)).isEmpty();
  }
}
