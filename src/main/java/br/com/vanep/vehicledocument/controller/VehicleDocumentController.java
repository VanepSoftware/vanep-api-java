package br.com.vanep.vehicledocument.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.media.web.MediaResponder;
import br.com.vanep.shared.document.dto.DocumentStatusUpdateRequestDTO;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.vehicledocument.dto.VehicleDocumentRequestDTO;
import br.com.vanep.vehicledocument.dto.VehicleDocumentResponseDTO;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import br.com.vanep.vehicledocument.service.VehicleDocumentFileService;
import br.com.vanep.vehicledocument.service.VehicleDocumentService;
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
@RequestMapping("/api")
public class VehicleDocumentController {

  private final VehicleDocumentService service;
  private final VehicleDocumentFileService fileService;
  private final MediaResponder mediaResponder;

  public VehicleDocumentController(
      VehicleDocumentService service,
      VehicleDocumentFileService fileService,
      MediaResponder mediaResponder) {
    this.service = service;
    this.fileService = fileService;
    this.mediaResponder = mediaResponder;
  }

  @GetMapping("/vehicles/{token}/documents")
  @PreAuthorize(
      "hasAuthority('list_vehicle_documents') or @sec.isVehicleOwner(#token, authentication)")
  public Page<VehicleDocumentResponseDTO> listByVehicle(
      @PathVariable String token,
      @RequestParam(required = false) VehicleDocumentType documentType,
      @RequestParam(required = false) DocumentStatus status,
      @PageableDefault Pageable pageable) {
    return service.findByVehicle(token, documentType, status, pageable);
  }

  @PostMapping(
      value = "/vehicles/{token}/documents",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(
      "hasAuthority('update_vehicle_document') or @sec.isVehicleOwner(#token, authentication)")
  public VehicleDocumentResponseDTO create(
      @PathVariable String token, @Valid @ModelAttribute VehicleDocumentRequestDTO request) {
    return service.create(token, request);
  }

  @GetMapping("/vehicle-documents/{token}")
  @PreAuthorize(
      "hasAuthority('show_vehicle_document') or @sec.isVehicleDocumentOwner(#token, authentication)")
  public VehicleDocumentResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PutMapping("/vehicle-documents/{token}/status")
  @PreAuthorize("hasAuthority('update_vehicle_document')")
  public VehicleDocumentResponseDTO updateStatus(
      @PathVariable String token,
      @Valid @RequestBody DocumentStatusUpdateRequestDTO request,
      Authentication authentication) {
    return service.updateStatus(token, request, SecurityHelper.requireCallerUid(authentication));
  }

  @DeleteMapping("/vehicle-documents/{token}")
  @PreAuthorize(
      "hasAuthority('delete_vehicle_document')"
          + " or @sec.isVehicleDocumentOwner(#token, authentication)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token) {
    service.delete(token);
  }

  @PostMapping("/vehicle-documents/{token}/restore")
  @PreAuthorize("hasAuthority('restore_vehicle_document')")
  public VehicleDocumentResponseDTO restore(@PathVariable String token) {
    return service.restore(token);
  }

  @PostMapping(
      value = "/vehicle-documents/{token}/file",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(
      "hasAuthority('update_vehicle_document')"
          + " or @sec.isVehicleDocumentOwner(#token, authentication)")
  public void uploadFile(@PathVariable String token, @RequestPart("file") MultipartFile file) {
    fileService.replace(token, file);
  }

  @GetMapping("/vehicle-documents/{token}/file")
  @PreAuthorize(
      "hasAuthority('show_vehicle_document') or @sec.isVehicleDocumentOwner(#token, authentication)")
  public ResponseEntity<InputStreamResource> downloadFile(@PathVariable String token) {
    return mediaResponder.respond(fileService.requireFile(token));
  }
}
