package br.com.vanep.media.web;

import br.com.vanep.media.model.MediaFileModel;
import org.jspecify.annotations.Nullable;

public final class MediaUrl {

  private MediaUrl() {}

  // The owner's own route, so a storage change never reaches the client. The media token
  // changes on every upload, so the "v" parameter makes clients drop the cached old photo.
  public static @Nullable String of(
      String ownerPath, String ownerToken, String slot, @Nullable MediaFileModel media) {
    return media == null
        ? null
        : "%s/%s/%s?v=%s".formatted(ownerPath, ownerToken, slot, media.getToken());
  }
}
