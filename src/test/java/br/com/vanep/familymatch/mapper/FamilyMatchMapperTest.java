package br.com.vanep.familymatch.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.familymatch.dto.FamilyMatchResponseDTO;
import br.com.vanep.familymatch.dto.FamilyMatchSummaryRow;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FamilyMatchMapperTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

  private final FamilyMatchMapper mapper = new FamilyMatchMapper();

  @Test
  void theAgeCountsOnlyFullYears() {
    assertThat(mapper.ageOn(LocalDate.of(2017, 11, 8), TODAY)).isEqualTo(9);
    assertThat(mapper.ageOn(LocalDate.of(2017, 11, 9), TODAY)).isEqualTo(8);
  }

  @Test
  void anUnknownOrFutureBirthDateHasNoAge() {
    assertThat(mapper.ageOn(null, TODAY)).isNull();
    assertThat(mapper.ageOn(TODAY.plusDays(1), TODAY)).isNull();
  }

  @Test
  void mapsTheSummaryRow() {
    FamilyMatchResponseDTO response =
        mapper.toResponse(
            new FamilyMatchSummaryRow(
                1L, "match", "rafael", "Rafael Costa", LocalDate.of(2017, 1, 1), "Maria Costa"),
            TODAY);

    assertThat(response)
        .isEqualTo(new FamilyMatchResponseDTO("match", "rafael", "Rafael Costa", 9, "Maria Costa"));
  }
}
