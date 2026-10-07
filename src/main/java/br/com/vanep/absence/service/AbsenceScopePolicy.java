package br.com.vanep.absence.service;

import br.com.vanep.absence.enums.AbsenceLeg;
import br.com.vanep.absence.enums.AbsenceScope;
import br.com.vanep.shared.enums.Shift;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AbsenceScopePolicy {

  private final AbsenceLegShiftPolicy legs;

  public AbsenceScopePolicy(AbsenceLegShiftPolicy legs) {
    this.legs = legs;
  }

  public List<AbsenceLeg> expand(AbsenceScope scope) {
    if (scope == null) {
      return List.of();
    }
    return switch (scope) {
      case OUTBOUND -> List.of(AbsenceLeg.OUTBOUND);
      case RETURN -> List.of(AbsenceLeg.RETURN);
      case BOTH -> List.of(AbsenceLeg.OUTBOUND, AbsenceLeg.RETURN);
    };
  }

  public boolean allows(AbsenceScope scope, Shift dependentShift) {
    if (scope == null || dependentShift == null) {
      return false;
    }
    for (AbsenceLeg leg : expand(scope)) {
      if (!legs.covers(dependentShift, leg)) {
        return false;
      }
    }
    return !expand(scope).isEmpty();
  }
}
