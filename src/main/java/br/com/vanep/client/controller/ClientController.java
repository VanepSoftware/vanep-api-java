package br.com.vanep.client.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.client.dto.ClientMeSummaryResponseDTO;
import br.com.vanep.client.dto.ClientResponseDTO;
import br.com.vanep.client.dto.ClientUpdateRequestDTO;
import br.com.vanep.client.service.ClientPhotoService;
import br.com.vanep.client.service.ClientService;
import br.com.vanep.media.web.MediaResponder;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/clients")
public class ClientController {
  private final ClientService service;
  private final ClientPhotoService photoService;
  private final MediaResponder mediaResponder;

  public ClientController(
      ClientService service, ClientPhotoService photoService, MediaResponder mediaResponder) {
    this.service = service;
    this.photoService = photoService;
    this.mediaResponder = mediaResponder;
  }

  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public ClientMeSummaryResponseDTO getMe(Authentication authentication) {
    return service.getMyProfile(SecurityHelper.requireCallerUid(authentication));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_clients')")
  public Page<ClientResponseDTO> list(@PageableDefault(size = 20) Pageable pageable) {
    return service.findAll(pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasAuthority('show_client') or @sec.isClientOwner(#token, authentication)")
  public ClientResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PatchMapping("/{token}")
  @PreAuthorize("hasAuthority('update_client') or @sec.isClientOwner(#token, authentication)")
  public ClientResponseDTO update(
      @PathVariable String token, @Valid @RequestBody ClientUpdateRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @PreAuthorize("hasAuthority('delete_client')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token) {
    service.delete(token);
  }

  @PostMapping(value = "/{token}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('update_client') or @sec.isClientOwner(#token, authentication)")
  public void uploadPhoto(@PathVariable String token, @RequestPart("file") MultipartFile file) {
    photoService.replace(token, file);
  }

  @GetMapping("/{token}/photo")
  @PreAuthorize("hasAuthority('show_client') or @sec.isClientOwner(#token, authentication)")
  public ResponseEntity<InputStreamResource> downloadPhoto(@PathVariable String token) {
    return mediaResponder.respond(photoService.requirePhoto(token));
  }
}
