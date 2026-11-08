package br.com.vanep.driverdocument.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentTypeEnumTest {

  @Test
  void containsSchoolTransportDocumentTypes() {
    assertThat(DocumentTypeEnum.values())
        .contains(
            DocumentTypeEnum.CRLV,
            DocumentTypeEnum.VEHICLE_INSPECTION,
            DocumentTypeEnum.MUNICIPAL_AUTHORIZATION);
  }
}
