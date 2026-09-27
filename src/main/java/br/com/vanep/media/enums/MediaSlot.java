package br.com.vanep.media.enums;

import java.util.Set;

public enum MediaSlot {
  DRIVER_PHOTO("driver", "photo", Kind.IMAGE, MediaVisibility.PUBLIC),
  CLIENT_PHOTO("client", "photo", Kind.IMAGE, MediaVisibility.PUBLIC),
  ASSISTANT_PHOTO("assistant", "photo", Kind.IMAGE, MediaVisibility.PUBLIC),
  VEHICLE_PHOTO_FRONT("vehicle", "photo-front", Kind.IMAGE, MediaVisibility.PRIVATE),
  VEHICLE_PHOTO_SIDE("vehicle", "photo-side", Kind.IMAGE, MediaVisibility.PRIVATE),
  VEHICLE_PHOTO_DOCUMENT("vehicle", "photo-document", Kind.IMAGE, MediaVisibility.PRIVATE),
  DRIVER_CNH_PHOTO("driver-cnh", "photo", Kind.IMAGE, MediaVisibility.PRIVATE),
  DRIVER_DOCUMENT_FILE("driver-document", "file", Kind.DOCUMENT, MediaVisibility.PRIVATE);

  private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
  private static final Set<String> DOCUMENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

  private enum Kind {
    IMAGE,
    DOCUMENT
  }

  private final String ownerFolder;
  private final String slotFolder;
  private final Kind kind;
  private final MediaVisibility visibility;

  MediaSlot(String ownerFolder, String slotFolder, Kind kind, MediaVisibility visibility) {
    this.ownerFolder = ownerFolder;
    this.slotFolder = slotFolder;
    this.kind = kind;
    this.visibility = visibility;
  }

  public String ownerFolder() {
    return ownerFolder;
  }

  public String slotFolder() {
    return slotFolder;
  }

  public MediaVisibility visibility() {
    return visibility;
  }

  public boolean allows(String mimeType) {
    return allowedMimeTypes().contains(mimeType);
  }

  public Set<String> allowedMimeTypes() {
    return kind == Kind.DOCUMENT ? DOCUMENT_TYPES : IMAGE_TYPES;
  }

  public boolean isDocument() {
    return kind == Kind.DOCUMENT;
  }
}
