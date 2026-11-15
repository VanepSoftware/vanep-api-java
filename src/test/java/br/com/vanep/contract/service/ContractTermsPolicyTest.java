package br.com.vanep.contract.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.contract.enums.ContractTermsViolation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContractTermsPolicyTest {

  private static final LocalDate FEBRUARY_FIRST = LocalDate.of(2027, 2, 1);
  private static final LocalDate END = LocalDate.of(2027, 12, 17);

  private final ContractTermsPolicy policy = new ContractTermsPolicy();

  @Test
  void aPeriodOfExactlyTwelveMonthsIsAccepted() {
    assertThat(period(FEBRUARY_FIRST, LocalDate.of(2028, 2, 1))).isEmpty();
  }

  @Test
  void twelveMonthsAndOneDayIsRefused() {
    assertThat(period(FEBRUARY_FIRST, LocalDate.of(2028, 2, 2)))
        .contains(ContractTermsViolation.PERIOD_TOO_LONG);
  }

  @Test
  void aSchoolYearThatCrossesTheCalendarYearIsAccepted() {
    assertThat(period(LocalDate.of(2027, 7, 1), LocalDate.of(2028, 6, 30))).isEmpty();
  }

  @Test
  void anEndOnTheStartDateIsRefused() {
    assertThat(period(FEBRUARY_FIRST, FEBRUARY_FIRST))
        .contains(ContractTermsViolation.PERIOD_INVALID);
  }

  @Test
  void anEndBeforeTheStartIsRefused() {
    assertThat(period(FEBRUARY_FIRST, LocalDate.of(2027, 1, 31)))
        .contains(ContractTermsViolation.PERIOD_INVALID);
  }

  @Test
  void oneCentPerInstallmentIsAccepted() {
    assertThat(policy.validate(FEBRUARY_FIRST, END, new BigDecimal("0.12"), 12)).isEmpty();
    assertThat(policy.validate(FEBRUARY_FIRST, END, new BigDecimal("0.01"), 1)).isEmpty();
  }

  @Test
  void aTotalBelowOneCentPerInstallmentIsRefused() {
    assertThat(policy.validate(FEBRUARY_FIRST, END, new BigDecimal("0.11"), 12))
        .contains(ContractTermsViolation.INSTALLMENT_BELOW_ONE_CENT);
  }

  private Optional<ContractTermsViolation> period(LocalDate startsOn, LocalDate endsOn) {
    return policy.validate(startsOn, endsOn, new BigDecimal("4200.00"), 12);
  }
}
