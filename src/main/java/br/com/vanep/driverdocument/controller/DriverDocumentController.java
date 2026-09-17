package br.com.vanep.driverdocument.controller;

import br.com.vanep.driverdocument.dto.DriverDocumentRequestDTO;
import br.com.vanep.driverdocument.dto.DriverDocumentResponseDTO;
import br.com.vanep.driverdocument.dto.DriverDocumentStatusUpdateRequestDTO;
import br.com.vanep.driverdocument.enums.DocumentStatusEnum;
import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import br.com.vanep.driverdocument.service.DriverDocumentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/driver-documents")
public class DriverDocumentController {

  private final DriverDocumentService service;

  public DriverDocumentController(DriverDocumentService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_driver_document')")
  public DriverDocumentResponseDTO create(
      @Valid @RequestBody DriverDocumentRequestDTO request, @AuthenticationPrincipal Jwt jwt) {
    return service.create(request, jwt.getSubject());
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_driver_documents')")
  public Page<DriverDocumentResponseDTO> list(
      @RequestParam(required = false) String driverToken,
      @RequestParam(required = false) DocumentTypeEnum documentType,
      @RequestParam(required = false) DocumentStatusEnum status,
      @AuthenticationPrincipal Jwt jwt,
      @PageableDefault Pageable pageable) {
    return service.findAll(driverToken, documentType, status, jwt.getSubject(), pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize(
      "hasAuthority('show_driver_document') or @sec.isDriverDocumentOwner(#token, authentication)")
  public DriverDocumentResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PutMapping("/{token}")
  @PreAuthorize(
      "hasAuthority('update_driver_document') or @sec.isDriverDocumentOwner(#token, authentication)")
  public DriverDocumentResponseDTO update(
      @PathVariable String token, @Valid @RequestBody DriverDocumentRequestDTO request) {
    return service.update(token, request);
  }

  @PutMapping("/{token}/status")
  @PreAuthorize("hasAuthority('update_driver_document')")
  public DriverDocumentResponseDTO updateStatus(
      @PathVariable String token,
      @Valid @RequestBody DriverDocumentStatusUpdateRequestDTO request,
      @AuthenticationPrincipal Jwt jwt) {
    return service.updateStatus(token, request, jwt.getSubject());
  }

  @DeleteMapping("/{token}")
  @PreAuthorize(
      "hasAuthority('delete_driver_document') or @sec.isDriverDocumentOwner(#token, authentication)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token) {
    service.delete(token);
  }

  @PostMapping("/{token}/restore")
  @PreAuthorize("hasAuthority('restore_driver_document')")
  public DriverDocumentResponseDTO restore(@PathVariable String token) {
    return service.restore(token);
  }
}
