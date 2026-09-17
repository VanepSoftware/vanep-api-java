package br.com.vanep.driverrating.controller;

import br.com.vanep.driverrating.dto.DriverRatingCreateRequestDTO;
import br.com.vanep.driverrating.dto.DriverRatingResponseDTO;
import br.com.vanep.driverrating.dto.DriverRatingUpdateRequestDTO;
import br.com.vanep.driverrating.service.DriverRatingService;
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
@RequestMapping("/api/driver-ratings")
public class DriverRatingController {

  private final DriverRatingService service;

  public DriverRatingController(DriverRatingService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_driver_rating')")
  public DriverRatingResponseDTO create(
      @Valid @RequestBody DriverRatingCreateRequestDTO request, @AuthenticationPrincipal Jwt jwt) {
    return service.create(request, jwt.getSubject());
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_driver_ratings')")
  public Page<DriverRatingResponseDTO> list(
      @RequestParam(required = false) String driverToken,
      @PageableDefault(size = 20) Pageable pageable) {
    return service.findAll(driverToken, pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize(
      "hasAuthority('show_driver_rating') or @sec.isDriverRatingOwner(#token, authentication)")
  public DriverRatingResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PutMapping("/{token}")
  @PreAuthorize(
      "hasAuthority('update_driver_rating') or @sec.isDriverRatingOwner(#token, authentication)")
  public DriverRatingResponseDTO update(
      @PathVariable String token, @Valid @RequestBody DriverRatingUpdateRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @PreAuthorize(
      "hasAuthority('delete_driver_rating') or @sec.isDriverRatingOwner(#token, authentication)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token) {
    service.delete(token);
  }

  @PostMapping("/{token}/restore")
  @PreAuthorize("hasAuthority('restore_driver_rating')")
  public DriverRatingResponseDTO restore(@PathVariable String token) {
    return service.restore(token);
  }
}
