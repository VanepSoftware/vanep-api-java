package br.com.vanep.media.controller;

import br.com.vanep.media.dto.MediaFileResponseDTO;
import br.com.vanep.media.enums.MediaOwnerType;
import br.com.vanep.media.enums.MediaPurpose;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
public class MediaFileController {

  private static final Duration SIGNED_URL_TTL = Duration.ofMinutes(5);

  private final MediaFileService service;
  private final StorageService storage;

  public MediaFileController(MediaFileService service, StorageService storage) {
    this.service = service;
    this.storage = storage;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_media')")
  public MediaFileResponseDTO upload(
      @RequestParam MediaOwnerType ownerType,
      @RequestParam String ownerToken,
      @RequestParam MediaPurpose purpose,
      @RequestPart("file") MultipartFile file) {
    return service.upload(ownerType, ownerToken, purpose, file);
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasAuthority('show_media') or @sec.isMediaOwner(#token, authentication)")
  public MediaFileResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @GetMapping("/{token}/download")
  @PreAuthorize("hasAuthority('show_media') or @sec.isMediaOwner(#token, authentication)")
  public ResponseEntity<InputStreamResource> download(@PathVariable String token) {
    MediaFileModel media = service.requireMedia(token);

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

  @DeleteMapping("/{token}")
  @PreAuthorize("hasAuthority('delete_media') or @sec.isMediaOwner(#token, authentication)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token) {
    service.delete(token);
  }
}
