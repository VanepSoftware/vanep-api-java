package br.com.vanep.familymatch.mapper;

import br.com.vanep.familymatch.dto.FamilyMatchResponseDTO;
import br.com.vanep.familymatch.dto.FamilyMatchSummaryRow;
import java.time.LocalDate;
import java.time.Period;
import org.springframework.stereotype.Component;

@Component
public class FamilyMatchMapper {

  public FamilyMatchResponseDTO toResponse(FamilyMatchSummaryRow row, LocalDate today) {
    return new FamilyMatchResponseDTO(
        row.token(),
        row.dependentToken(),
        row.dependentName(),
        ageOn(row.dependentBirthDate(), today),
        row.clientName());
  }

  public Integer ageOn(LocalDate birthDate, LocalDate today) {
    if (birthDate == null || birthDate.isAfter(today)) {
      return null;
    }
    return Period.between(birthDate, today).getYears();
  }
}
