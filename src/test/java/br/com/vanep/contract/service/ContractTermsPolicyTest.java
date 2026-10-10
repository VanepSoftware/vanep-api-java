package br.com.vanep.contract.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.contract.enums.ContractTermsViolation;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ContractTermsPolicyTest {

  private static final LocalDate FEBRUARY_FIRST = LocalDate.of(2027, 2, 1);

  private final ContractTermsPolicy policy = new ContractTermsPolicy();

  @Test
  void aPeriodOfExactlyTwelveMonthsIsAccepted() {
    assertThat(policy.validate(FEBRUARY_FIRST, LocalDate.of(2028, 2, 1))).isEmpty();
  }

  @Test
  void twelveMonthsAndOneDayIsRefused() {
    assertThat(policy.validate(FEBRUARY_FIRST, LocalDate.of(2028, 2, 2)))
        .contains(ContractTermsViolation.PERIOD_TOO_LONG)
        .map(violation -> violation.messageKey())
        .contains("contract.period.too_long");
  }

  @Test
  void aSchoolYearThatCrossesTheCalendarYearIsAccepted() {
    assertThat(policy.validate(LocalDate.of(2027, 7, 1), LocalDate.of(2028, 6, 30))).isEmpty();
  }

  @Test
  void anEndOnTheStartDateIsRefused() {
    assertThat(policy.validate(FEBRUARY_FIRST, FEBRUARY_FIRST))
        .contains(ContractTermsViolation.PERIOD_INVALID)
        .map(violation -> violation.messageKey())
        .contains("contract.period.invalid");
  }

  @Test
  void anEndBeforeTheStartIsRefused() {
    assertThat(policy.validate(FEBRUARY_FIRST, LocalDate.of(2027, 1, 31)))
        .contains(ContractTermsViolation.PERIOD_INVALID);
  }
}
