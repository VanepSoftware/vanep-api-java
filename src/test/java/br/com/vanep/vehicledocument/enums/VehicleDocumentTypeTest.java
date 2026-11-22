package br.com.vanep.vehicledocument.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class VehicleDocumentTypeTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

  @Test
  void theCrlvExpiresByTheEndOfNextYear() {
    assertThat(VehicleDocumentType.CRLV.latestExpiryOn(TODAY))
        .isEqualTo(LocalDate.of(2027, 12, 31));
    assertThat(VehicleDocumentType.CRLV.latestExpiryOn(LocalDate.of(2027, 1, 1)))
        .isEqualTo(LocalDate.of(2028, 12, 31));
  }

  @Test
  void theInspectionExpiresWithinSevenMonths() {
    assertThat(VehicleDocumentType.VEHICLE_INSPECTION.latestExpiryOn(TODAY))
        .isEqualTo(LocalDate.of(2027, 6, 20));
  }

  @Test
  void theSchoolAuthorizationExpiresWithinTwoYears() {
    assertThat(VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION.latestExpiryOn(TODAY))
        .isEqualTo(LocalDate.of(2028, 11, 20));
  }
}
