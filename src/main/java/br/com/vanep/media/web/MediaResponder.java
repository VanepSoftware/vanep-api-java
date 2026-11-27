package br.com.vanep.media.web;

import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.media.storage.StorageService;
import br.com.vanep.shared.error.ApiProblemException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

// The one place the storage migration touches: every owner route delegates here.
@Component
public class MediaResponder {

  private static final Duration SIGNED_URL_TTL = Duration.ofMinutes(5);
  private static final CacheControl IMMUTABLE =
      CacheControl.maxAge(Duration.ofDays(1)).cachePrivate().immutable();
  private static final CacheControl REVALIDATE = CacheControl.noCache().cachePrivate();

  private final MediaFileService service;
  private final StorageService storage;

  public MediaResponder(MediaFileService service, StorageService storage) {
    this.service = service;
    this.storage = storage;
  }

  // The URL carries the media token as "v" (MediaUrl), and a token never changes content.
  public ResponseEntity<InputStreamResource> respond(
      MediaFileModel media, String requestedVersion) {
    if (media == null) {
      throw ApiProblemException.notFound("media.not_found");
    }

    Optional<URI> signed = storage.signedUrl(media.getObjectKey(), SIGNED_URL_TTL);
    if (signed.isPresent()) {
      return ResponseEntity.status(HttpStatus.FOUND)
          .location(signed.get())
          .cacheControl(REVALIDATE)
          .build();
    }

    InputStream content = service.openContent(media);
    return ResponseEntity.ok()
        .cacheControl(media.getToken().equals(requestedVersion) ? IMMUTABLE : REVALIDATE)
        .contentType(MediaType.parseMediaType(media.getMimeType()))
        .contentLength(media.getSizeBytes())
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + media.getToken() + "\"")
        .body(new InputStreamResource(content));
  }
}
