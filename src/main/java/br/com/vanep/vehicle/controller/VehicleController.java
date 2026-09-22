package br.com.vanep.vehicle.controller;

import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.web.MediaResponder;
import br.com.vanep.vehicle.dto.VehicleRequestDTO;
import br.com.vanep.vehicle.dto.VehicleResponseDTO;
import br.com.vanep.vehicle.service.VehiclePhotoService;
import br.com.vanep.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

  private final VehicleService service;
  private final VehiclePhotoService photoService;
  private final MediaResponder mediaResponder;

  public VehicleController(
      VehicleService service, VehiclePhotoService photoService, MediaResponder mediaResponder) {
    this.service = service;
    this.photoService = photoService;
    this.mediaResponder = mediaResponder;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_vehicle')")
  public VehicleResponseDTO create(
      @Valid @RequestBody VehicleRequestDTO request, @AuthenticationPrincipal Jwt jwt) {
    return service.create(request, jwt.getSubject());
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_vehicles')")
  public List<VehicleResponseDTO> list(@AuthenticationPrincipal Jwt jwt) {
    return service.findAll(jwt.getSubject());
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasAuthority('show_vehicle') or @sec.isVehicleOwner(#token, authentication)")
  public VehicleResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PutMapping("/{token}")
  @PreAuthorize("hasAuthority('update_vehicle') or @sec.isVehicleOwner(#token, authentication)")
  public VehicleResponseDTO update(
      @PathVariable String token, @Valid @RequestBody VehicleRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @PreAuthorize("hasAuthority('delete_vehicle') or @sec.isVehicleOwner(#token, authentication)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token) {
    service.delete(token);
  }

  @PostMapping("/{token}/restore")
  @PreAuthorize("hasAuthority('restore_vehicle') or @sec.isVehicleOwner(#token, authentication)")
  public VehicleResponseDTO restore(@PathVariable String token) {
    return service.restore(token);
  }

  @PostMapping(value = "/{token}/photo-front", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('update_vehicle') or @sec.isVehicleOwner(#token, authentication)")
  public void uploadPhotoFront(
      @PathVariable String token, @RequestPart("file") MultipartFile file) {
    photoService.replace(token, MediaSlot.VEHICLE_PHOTO_FRONT, file);
  }

  @GetMapping("/{token}/photo-front")
  @PreAuthorize("hasAuthority('show_vehicle') or @sec.isVehicleOwner(#token, authentication)")
  public ResponseEntity<InputStreamResource> downloadPhotoFront(@PathVariable String token) {
    return mediaResponder.respond(photoService.require(token, MediaSlot.VEHICLE_PHOTO_FRONT));
  }

  @PostMapping(value = "/{token}/photo-side", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('update_vehicle') or @sec.isVehicleOwner(#token, authentication)")
  public void uploadPhotoSide(@PathVariable String token, @RequestPart("file") MultipartFile file) {
    photoService.replace(token, MediaSlot.VEHICLE_PHOTO_SIDE, file);
  }

  @GetMapping("/{token}/photo-side")
  @PreAuthorize("hasAuthority('show_vehicle') or @sec.isVehicleOwner(#token, authentication)")
  public ResponseEntity<InputStreamResource> downloadPhotoSide(@PathVariable String token) {
    return mediaResponder.respond(photoService.require(token, MediaSlot.VEHICLE_PHOTO_SIDE));
  }

  @PostMapping(value = "/{token}/photo-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('update_vehicle') or @sec.isVehicleOwner(#token, authentication)")
  public void uploadPhotoDocument(
      @PathVariable String token, @RequestPart("file") MultipartFile file) {
    photoService.replace(token, MediaSlot.VEHICLE_PHOTO_DOCUMENT, file);
  }

  @GetMapping("/{token}/photo-document")
  @PreAuthorize("hasAuthority('show_vehicle') or @sec.isVehicleOwner(#token, authentication)")
  public ResponseEntity<InputStreamResource> downloadPhotoDocument(@PathVariable String token) {
    return mediaResponder.respond(photoService.require(token, MediaSlot.VEHICLE_PHOTO_DOCUMENT));
  }
}
