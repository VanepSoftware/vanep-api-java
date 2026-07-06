package br.com.vanep.vehicle.controller;

import br.com.vanep.vehicle.dto.VehicleRequestDTO;
import br.com.vanep.vehicle.dto.VehicleResponseDTO;
import br.com.vanep.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

  private final VehicleService service;

  public VehicleController(VehicleService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_vehicle')")
  public VehicleResponseDTO create(
      @Valid @RequestBody VehicleRequestDTO request,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    boolean isAdmin =
        authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    return service.create(request, jwt, isAdmin);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_vehicles')")
  public List<VehicleResponseDTO> list(
      @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
    boolean isAdmin =
        authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    return service.findAll(jwt, isAdmin);
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasAuthority('show_vehicle') or @vehicleSecurity.isOwner(#token, authentication)")
  public VehicleResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PutMapping("/{token}")
  @PreAuthorize(
      "hasAuthority('update_vehicle') or @vehicleSecurity.isOwner(#token, authentication)")
  public VehicleResponseDTO update(
      @PathVariable String token, @Valid @RequestBody VehicleRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @PreAuthorize(
      "hasAuthority('delete_vehicle') or @vehicleSecurity.isOwner(#token, authentication)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token) {
    service.delete(token);
  }

  @PostMapping("/{token}/restore")
  @PreAuthorize(
      "hasAuthority('restore_vehicle') or @vehicleSecurity.isOwner(#token, authentication)")
  public VehicleResponseDTO restore(@PathVariable String token) {
    return service.restore(token);
  }
}
