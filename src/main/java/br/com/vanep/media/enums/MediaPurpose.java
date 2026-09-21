package br.com.vanep.media.enums;

import java.util.Set;

public enum MediaPurpose {
  PHOTO(Set.of("image/jpeg", "image/png", "image/webp"), MediaVisibility.PUBLIC),
  PHOTO_FRONT(Set.of("image/jpeg", "image/png", "image/webp"), MediaVisibility.PRIVATE),
  PHOTO_SIDE(Set.of("image/jpeg", "image/png", "image/webp"), MediaVisibility.PRIVATE),
  DOCUMENT(
      Set.of("image/jpeg", "image/png", "image/webp", "application/pdf"), MediaVisibility.PRIVATE);

  private final Set<String> allowedMimeTypes;
  private final MediaVisibility defaultVisibility;

  MediaPurpose(Set<String> allowedMimeTypes, MediaVisibility defaultVisibility) {
    this.allowedMimeTypes = allowedMimeTypes;
    this.defaultVisibility = defaultVisibility;
  }

  public boolean allows(String mimeType) {
    return allowedMimeTypes.contains(mimeType);
  }

  public Set<String> allowedMimeTypes() {
    return allowedMimeTypes;
  }

  public MediaVisibility defaultVisibility() {
    return defaultVisibility;
  }
}
