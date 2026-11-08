package br.com.vanep.contract.service;

import br.com.vanep.contract.enums.ContractTermsViolation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ContractTermsPolicy {

  static final Period MAXIMUM_PERIOD = Period.ofMonths(12);
  static final BigDecimal ONE_CENT = new BigDecimal("0.01");

  public Optional<ContractTermsViolation> validate(
      LocalDate startsOn, LocalDate endsOn, BigDecimal totalAmount, Integer installments) {
    if (!endsOn.isAfter(startsOn)) {
      return Optional.of(ContractTermsViolation.PERIOD_INVALID);
    }
    if (endsOn.isAfter(startsOn.plus(MAXIMUM_PERIOD))) {
      return Optional.of(ContractTermsViolation.PERIOD_TOO_LONG);
    }
    if (totalAmount.compareTo(ONE_CENT.multiply(BigDecimal.valueOf(installments))) < 0) {
      return Optional.of(ContractTermsViolation.INSTALLMENT_BELOW_ONE_CENT);
    }
    return Optional.empty();
  }
}
