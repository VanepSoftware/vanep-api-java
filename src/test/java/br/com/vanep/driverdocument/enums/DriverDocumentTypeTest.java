package br.com.vanep.driverdocument.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DriverDocumentTypeTest {

  @Test
  void holdsOnlyThePersonsDocumentTypes() {
    assertThat(DriverDocumentType.values())
        .containsExactly(
            DriverDocumentType.MUNICIPAL_AUTHORIZATION,
            DriverDocumentType.SCHOOL_DRIVER_COURSE,
            DriverDocumentType.CRIMINAL_RECORD);
  }

  @Test
  void holdsNoVehicleDocumentNorPhoto() {
    assertThat(Arrays.stream(DriverDocumentType.values()).map(type -> type.name()))
        .doesNotContain(
            "CRLV",
            "VEHICLE_INSPECTION",
            "PROFILE_PHOTO",
            "VEHICLE_PHOTO",
            "RESIDENCE_PROOF",
            "OTHER");
  }
}
