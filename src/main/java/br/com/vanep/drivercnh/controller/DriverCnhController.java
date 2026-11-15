package br.com.vanep.drivercnh.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.drivercnh.dto.DriverCnhDataCorrectionRequestDTO;
import br.com.vanep.drivercnh.dto.DriverCnhRequestDTO;
import br.com.vanep.drivercnh.dto.DriverCnhResponseDTO;
import br.com.vanep.drivercnh.dto.DriverCnhStatusUpdateRequestDTO;
import br.com.vanep.drivercnh.service.DriverCnhFileService;
import br.com.vanep.drivercnh.service.DriverCnhService;
import br.com.vanep.media.web.MediaResponder;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/driver-cnhs")
public class DriverCnhController {

  private final DriverCnhService service;
  private final DriverCnhFileService fileService;
  private final MediaResponder mediaResponder;

  public DriverCnhController(
      DriverCnhService service, DriverCnhFileService fileService, MediaResponder mediaResponder) {
    this.service = service;
    this.fileService = fileService;
    this.mediaResponder = mediaResponder;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_driver_cnh')")
  public DriverCnhResponseDTO create(
      @Valid @ModelAttribute DriverCnhRequestDTO request, @AuthenticationPrincipal Jwt jwt) {
    return service.create(request, jwt.getSubject());
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_driver_cnhs')")
  public List<DriverCnhResponseDTO> list(@AuthenticationPrincipal Jwt jwt) {
    return service.findAll(jwt.getSubject());
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasAuthority('show_driver_cnh') or @sec.isCnhOwner(#token, authentication)")
  public DriverCnhResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @DeleteMapping("/{token}")
  @PreAuthorize("hasAuthority('delete_driver_cnh') or @sec.isCnhOwner(#token, authentication)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token, Authentication authentication) {
    service.delete(token, SecurityHelper.hasAuthority(authentication, "delete_driver_cnh"));
  }

  @PutMapping("/{token}/status")
  @PreAuthorize("hasAuthority('review_documents')")
  public DriverCnhResponseDTO updateStatus(
      @PathVariable String token,
      @Valid @RequestBody DriverCnhStatusUpdateRequestDTO request,
      Authentication authentication) {
    return service.updateStatus(token, request, SecurityHelper.requireCallerUid(authentication));
  }

  @PutMapping("/{token}/data")
  @PreAuthorize("hasAuthority('review_documents')")
  public DriverCnhResponseDTO correctData(
      @PathVariable String token,
      @Valid @RequestBody DriverCnhDataCorrectionRequestDTO request,
      Authentication authentication) {
    return service.correctData(token, request, SecurityHelper.requireCallerUid(authentication));
  }

  @PostMapping("/{token}/restore")
  @PreAuthorize("hasAuthority('restore_driver_cnh') or @sec.isCnhOwner(#token, authentication)")
  public DriverCnhResponseDTO restore(@PathVariable String token, Authentication authentication) {
    return service.restore(
        token, SecurityHelper.hasAuthority(authentication, "restore_driver_cnh"));
  }

  @PostMapping(value = "/{token}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('update_driver_cnh') or @sec.isCnhOwner(#token, authentication)")
  public void uploadFile(@PathVariable String token, @RequestPart("file") MultipartFile file) {
    fileService.replace(token, file);
  }

  @GetMapping("/{token}/file")
  @PreAuthorize("hasAuthority('show_driver_cnh') or @sec.isCnhOwner(#token, authentication)")
  public ResponseEntity<InputStreamResource> downloadFile(
      @PathVariable String token, @RequestParam(name = "v", required = false) String version) {
    return mediaResponder.respond(fileService.requireFile(token), version);
  }
}
