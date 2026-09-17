package br.com.vanep.drivercnh.controller;

import br.com.vanep.drivercnh.dto.DriverCnhRequestDTO;
import br.com.vanep.drivercnh.dto.DriverCnhResponseDTO;
import br.com.vanep.drivercnh.service.DriverCnhService;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/driver-cnhs")
public class DriverCnhController {

  private final DriverCnhService service;

  public DriverCnhController(DriverCnhService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_driver_cnh')")
  public DriverCnhResponseDTO create(
      @Valid @RequestBody DriverCnhRequestDTO request, @AuthenticationPrincipal Jwt jwt) {
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

  @PutMapping("/{token}")
  @PreAuthorize("hasAuthority('update_driver_cnh') or @sec.isCnhOwner(#token, authentication)")
  public DriverCnhResponseDTO update(
      @PathVariable String token, @Valid @RequestBody DriverCnhRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @PreAuthorize("hasAuthority('delete_driver_cnh') or @sec.isCnhOwner(#token, authentication)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token) {
    service.delete(token);
  }

  @PostMapping("/{token}/restore")
  @PreAuthorize("hasAuthority('restore_driver_cnh') or @sec.isCnhOwner(#token, authentication)")
  public DriverCnhResponseDTO restore(@PathVariable String token) {
    return service.restore(token);
  }
}
