package br.com.vanep.media.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MediaSlotTest {

  @Test
  void photoSlotsAcceptImagesOnly() {
    assertThat(MediaSlot.DRIVER_PHOTO.allows("image/jpeg")).isTrue();
    assertThat(MediaSlot.DRIVER_PHOTO.allows("application/pdf")).isFalse();
  }

  @Test
  void documentSlotAlsoAcceptsPdf() {
    assertThat(MediaSlot.DRIVER_DOCUMENT_FILE.allows("application/pdf")).isTrue();
    assertThat(MediaSlot.DRIVER_DOCUMENT_FILE.allows("image/png")).isTrue();
  }

  @Test
  void personalDocumentsAreNeverPublic() {
    assertThat(MediaSlot.DRIVER_CNH_PHOTO.visibility()).isEqualTo(MediaVisibility.PRIVATE);
    assertThat(MediaSlot.DRIVER_DOCUMENT_FILE.visibility()).isEqualTo(MediaVisibility.PRIVATE);
    assertThat(MediaSlot.VEHICLE_PHOTO_DOCUMENT.visibility()).isEqualTo(MediaVisibility.PRIVATE);
  }

  @Test
  void profilePicturesArePublic() {
    assertThat(MediaSlot.DRIVER_PHOTO.visibility()).isEqualTo(MediaVisibility.PUBLIC);
    assertThat(MediaSlot.CLIENT_PHOTO.visibility()).isEqualTo(MediaVisibility.PUBLIC);
    assertThat(MediaSlot.ASSISTANT_PHOTO.visibility()).isEqualTo(MediaVisibility.PUBLIC);
  }

  @Test
  void everySlotOfTheSameOwnerSharesTheOwnerFolder() {
    assertThat(MediaSlot.VEHICLE_PHOTO_FRONT.ownerFolder())
        .isEqualTo(MediaSlot.VEHICLE_PHOTO_SIDE.ownerFolder());
    assertThat(MediaSlot.VEHICLE_PHOTO_FRONT.slotFolder())
        .isNotEqualTo(MediaSlot.VEHICLE_PHOTO_SIDE.slotFolder());
  }
}
