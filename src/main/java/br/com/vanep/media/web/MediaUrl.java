package br.com.vanep.media.web;

import br.com.vanep.media.model.MediaFileModel;

public final class MediaUrl {

  private MediaUrl() {}

  // The owner's own route, so a storage change never reaches the client.
  public static String of(String ownerPath, String ownerToken, String slot, MediaFileModel media) {
    return media == null ? null : "%s/%s/%s".formatted(ownerPath, ownerToken, slot);
  }
}
