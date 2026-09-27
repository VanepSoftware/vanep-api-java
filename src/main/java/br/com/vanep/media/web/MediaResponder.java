package br.com.vanep.media.web;

import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.media.storage.StorageService;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

// The one place the storage migration touches: every owner route delegates here.
@Component
public class MediaResponder {

  private static final Duration SIGNED_URL_TTL = Duration.ofMinutes(5);

  private final MediaFileService service;
  private final StorageService storage;

  public MediaResponder(MediaFileService service, StorageService storage) {
    this.service = service;
    this.storage = storage;
  }

  public ResponseEntity<InputStreamResource> respond(MediaFileModel media) {
    if (media == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    Optional<URI> signed = storage.signedUrl(media.getObjectKey(), SIGNED_URL_TTL);
    if (signed.isPresent()) {
      return ResponseEntity.status(HttpStatus.FOUND).location(signed.get()).build();
    }

    InputStream content = service.openContent(media);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(media.getMimeType()))
        .contentLength(media.getSizeBytes())
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + media.getToken() + "\"")
        .body(new InputStreamResource(content));
  }
}
