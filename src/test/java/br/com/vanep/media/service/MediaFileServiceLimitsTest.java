package br.com.vanep.media.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MediaFileServiceLimitsTest {

  @Test
  void anUploadLimitAboveEveryMediaLimitIsAccepted() {
    assertThatCode(() -> MediaFileService.requireUploadAboveMediaLimits(11, 5, 10))
        .doesNotThrowAnyException();
  }

  @Test
  void anUploadLimitEqualToTheDocumentLimitRefusesToStart() {
    assertThatThrownBy(() -> MediaFileService.requireUploadAboveMediaLimits(10, 5, 10))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("max-upload-size-mb");
  }

  @Test
  void theLargestMediaLimitCountsWhicheverItIs() {
    assertThatThrownBy(() -> MediaFileService.requireUploadAboveMediaLimits(7, 8, 6))
        .isInstanceOf(IllegalStateException.class);
  }
}
