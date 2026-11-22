package br.com.vanep.driverdocument.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.driverdocument.dto.DriverDocumentRequestDTO;
import br.com.vanep.driverdocument.dto.DriverDocumentResponseDTO;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.service.DriverDocumentFileService;
import br.com.vanep.driverdocument.service.DriverDocumentService;
import br.com.vanep.media.web.MediaResponder;
import br.com.vanep.shared.document.dto.DocumentDataCorrectionRequestDTO;
import br.com.vanep.shared.document.dto.DocumentStatusUpdateRequestDTO;
import br.com.vanep.shared.document.enums.DocumentStatus;
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
@RequestMapping("/api/driver-documents")
public class DriverDocumentController {

  private final DriverDocumentService service;
  private final DriverDocumentFileService fileService;
  private final MediaResponder mediaResponder;

  public DriverDocumentController(
      DriverDocumentService service,
      DriverDocumentFileService fileService,
      MediaResponder mediaResponder) {
    this.service = service;
    this.fileService = fileService;
    this.mediaResponder = mediaResponder;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_driver_document')")
  public DriverDocumentResponseDTO create(
      @Valid @ModelAttribute DriverDocumentRequestDTO request, Authentication authentication) {
    return service.create(request, SecurityHelper.requireCallerUid(authentication));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_driver_documents')")
  public Page<DriverDocumentResponseDTO> list(
      @RequestParam(required = false) String driverToken,
      @RequestParam(required = false) DriverDocumentType documentType,
      @RequestParam(required = false) DocumentStatus status,
      Authentication authentication,
      @PageableDefault Pageable pageable) {
    return service.findAll(
        driverToken,
        documentType,
        status,
        SecurityHelper.requireCallerUid(authentication),
        pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize(
      "hasAuthority('show_driver_document') or @sec.isDriverDocumentOwner(#token, authentication)")
  public DriverDocumentResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PutMapping("/{token}/status")
  @PreAuthorize("hasAuthority('review_documents')")
  public DriverDocumentResponseDTO updateStatus(
      @PathVariable String token,
      @Valid @RequestBody DocumentStatusUpdateRequestDTO request,
      Authentication authentication) {
    return service.updateStatus(token, request, SecurityHelper.requireCallerUid(authentication));
  }

  @PutMapping("/{token}/data")
  @PreAuthorize("hasAuthority('review_documents')")
  public DriverDocumentResponseDTO correctData(
      @PathVariable String token,
      @Valid @RequestBody DocumentDataCorrectionRequestDTO request,
      Authentication authentication) {
    return service.correctData(token, request, SecurityHelper.requireCallerUid(authentication));
  }

  @DeleteMapping("/{token}")
  @PreAuthorize(
      "hasAuthority('delete_driver_document') or @sec.isDriverDocumentOwner(#token, authentication)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token, Authentication authentication) {
    service.delete(token, SecurityHelper.hasAuthority(authentication, "delete_driver_document"));
  }

  @PostMapping("/{token}/restore")
  @PreAuthorize("hasAuthority('restore_driver_document')")
  public DriverDocumentResponseDTO restore(@PathVariable String token) {
    return service.restore(token);
  }

  @PostMapping(value = "/{token}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(
      "hasAuthority('update_driver_document') or @sec.isDriverDocumentOwner(#token, authentication)")
  public void uploadFile(@PathVariable String token, @RequestPart("file") MultipartFile file) {
    fileService.replace(token, file);
  }

  @GetMapping("/{token}/file")
  @PreAuthorize(
      "hasAuthority('show_driver_document') or @sec.isDriverDocumentOwner(#token, authentication)")
  public ResponseEntity<InputStreamResource> downloadFile(
      @PathVariable String token, @RequestParam(name = "v", required = false) String version) {
    return mediaResponder.respond(fileService.requireFile(token), version);
  }
}
