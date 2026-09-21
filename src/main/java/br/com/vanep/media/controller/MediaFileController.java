package br.com.vanep.media.controller;

import br.com.vanep.media.dto.MediaFileResponseDTO;
import br.com.vanep.media.enums.MediaOwnerType;
import br.com.vanep.media.enums.MediaPurpose;
import br.com.vanep.media.service.MediaFileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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

  private final MediaFileService service;

  public MediaFileController(MediaFileService service) {
    this.service = service;
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
}
