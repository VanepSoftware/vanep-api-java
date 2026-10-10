package br.com.vanep.contract.service;

import br.com.vanep.contract.enums.ContractTermsViolation;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ContractTermsPolicy {

  static final Period MAXIMUM_PERIOD = Period.ofMonths(12);

  public Optional<ContractTermsViolation> validate(LocalDate startsOn, LocalDate endsOn) {
    if (!endsOn.isAfter(startsOn)) {
      return Optional.of(ContractTermsViolation.PERIOD_INVALID);
    }
    if (endsOn.isAfter(startsOn.plus(MAXIMUM_PERIOD))) {
      return Optional.of(ContractTermsViolation.PERIOD_TOO_LONG);
    }
    return Optional.empty();
  }
}
