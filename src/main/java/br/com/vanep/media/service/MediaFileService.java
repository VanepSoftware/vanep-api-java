package br.com.vanep.media.service;

import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.event.MediaDiscardedEvent;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.repository.MediaFileRepository;
import br.com.vanep.media.storage.StorageService;
import br.com.vanep.shared.error.ApiProblemException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaFileService {

  private static final long BYTES_IN_MB = 1024L * 1024L;

  private final MediaFileRepository repository;
  private final StorageService storage;
  private final MimeTypeDetector mimeTypeDetector;
  private final ApplicationEventPublisher events;
  private final long maxPhotoBytes;
  private final long maxDocumentBytes;

  public MediaFileService(
      MediaFileRepository repository,
      StorageService storage,
      MimeTypeDetector mimeTypeDetector,
      ApplicationEventPublisher events,
      @Value("${vanep.media.max-photo-size-mb}") long maxPhotoSizeMb,
      @Value("${vanep.media.max-document-size-mb}") long maxDocumentSizeMb,
      @Value("${vanep.media.max-upload-size-mb}") long maxUploadSizeMb) {
    requireUploadAboveMediaLimits(maxUploadSizeMb, maxPhotoSizeMb, maxDocumentSizeMb);
    this.repository = repository;
    this.storage = storage;
    this.mimeTypeDetector = mimeTypeDetector;
    this.events = events;
    this.maxPhotoBytes = maxPhotoSizeMb * BYTES_IN_MB;
    this.maxDocumentBytes = maxDocumentSizeMb * BYTES_IN_MB;
  }

  static void requireUploadAboveMediaLimits(
      long maxUploadSizeMb, long maxPhotoSizeMb, long maxDocumentSizeMb) {
    long largestMediaMb = Math.max(maxPhotoSizeMb, maxDocumentSizeMb);
    if (maxUploadSizeMb <= largestMediaMb) {
      throw new IllegalStateException(
          "vanep.media.max-upload-size-mb (%d) must be larger than the largest media limit (%d)"
              .formatted(maxUploadSizeMb, largestMediaMb));
    }
  }

  @Transactional
  public MediaFileModel store(MediaSlot slot, String ownerToken, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw badRequest("media.file.required");
    }
    if (file.getSize() > maxSizeFor(slot)) {
      throw tooLarge();
    }
    return store(slot, ownerToken, readAll(file), file.getOriginalFilename());
  }

  // For content that does not come from a request, such as the seed's files.
  @Transactional
  public MediaFileModel store(
      MediaSlot slot, String ownerToken, byte[] content, String originalName) {
    if (content.length == 0) {
      throw badRequest("media.file.required");
    }
    if (content.length > maxSizeFor(slot)) {
      throw tooLarge();
    }
    String mimeType =
        mimeTypeDetector.detect(content).orElseThrow(() -> badRequest("media.mime.not_allowed"));
    if (!slot.allows(mimeType)) {
      throw badRequest("media.mime.not_allowed");
    }

    MediaFileModel media = new MediaFileModel();
    media.setToken(newToken());
    media.setProvider(storage.provider());
    media.setObjectKey(objectKey(slot, ownerToken, media.getToken(), mimeType));
    media.setMimeType(mimeType);
    media.setSizeBytes((long) content.length);
    media.setOriginalName(originalName);
    media.setVisibility(slot.visibility());

    storage.upload(
        media.getObjectKey(), new ByteArrayInputStream(content), mimeType, content.length);

    return repository.save(media);
  }

  @Transactional
  public void discard(MediaFileModel media) {
    if (media == null) {
      return;
    }
    repository.delete(media);
    events.publishEvent(new MediaDiscardedEvent(media.getObjectKey()));
  }

  @Transactional(readOnly = true)
  public InputStream openContent(MediaFileModel media) {
    return storage.open(media.getObjectKey());
  }

  private String objectKey(MediaSlot slot, String ownerToken, String mediaToken, String mimeType) {
    return "%s/%s/%s/%s.%s"
        .formatted(
            slot.ownerFolder(), ownerToken, slot.slotFolder(), mediaToken, extensionFor(mimeType));
  }

  private String extensionFor(String mimeType) {
    return switch (mimeType) {
      case "image/jpeg" -> "jpg";
      case "image/png" -> "png";
      case "image/webp" -> "webp";
      case "application/pdf" -> "pdf";
      default -> "bin";
    };
  }

  private long maxSizeFor(MediaSlot slot) {
    return slot.isDocument() ? maxDocumentBytes : maxPhotoBytes;
  }

  private byte[] readAll(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException cause) {
      throw badRequest("media.file.unreadable");
    }
  }

  private String newToken() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 25);
  }

  private ApiProblemException badRequest(String key) {
    return ApiProblemException.badRequest(key);
  }

  public static ApiProblemException tooLarge() {
    return ApiProblemException.of(HttpStatus.CONTENT_TOO_LARGE, "media.file.too_large");
  }
}
