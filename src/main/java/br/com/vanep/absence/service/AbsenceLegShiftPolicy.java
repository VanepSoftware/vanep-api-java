package br.com.vanep.absence.service;

import br.com.vanep.absence.enums.AbsenceLeg;
import br.com.vanep.shared.enums.Shift;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AbsenceLegShiftPolicy {

  public boolean covers(Shift tripShift, AbsenceLeg leg) {
    if (tripShift == null || leg == null) {
      return false;
    }
    return switch (tripShift) {
      case MORNING, NIGHT -> leg == AbsenceLeg.OUTBOUND;
      case AFTERNOON -> leg == AbsenceLeg.RETURN;
      case FULLTIME -> true;
    };
  }

  public Optional<AbsenceLeg> uniqueLegOf(Shift tripShift) {
    if (tripShift == null) {
      return Optional.empty();
    }
    return switch (tripShift) {
      case MORNING, NIGHT -> Optional.of(AbsenceLeg.OUTBOUND);
      case AFTERNOON -> Optional.of(AbsenceLeg.RETURN);
      case FULLTIME -> Optional.empty();
    };
  }
}
