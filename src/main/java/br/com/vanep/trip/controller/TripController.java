package br.com.vanep.trip.controller;

import br.com.vanep.trip.dto.TripCreateRequestDTO;
import br.com.vanep.trip.dto.TripResponseDTO;
import br.com.vanep.trip.dto.TripUpdateRequestDTO;
import br.com.vanep.trip.service.TripService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
public class TripController {

  private final TripService service;

  public TripController(TripService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_trip')")
  public TripResponseDTO create(@Valid @RequestBody TripCreateRequestDTO request) {
    return service.create(request);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_trips')")
  public Page<TripResponseDTO> list(
      @RequestParam(required = false) String driverToken, @PageableDefault Pageable pageable) {
    return service.findAll(driverToken, pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasAuthority('show_trip') or @sec.isTripOwner(#token, authentication)")
  public TripResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PatchMapping("/{token}")
  @PreAuthorize("hasAuthority('update_trip') or @sec.isTripOwner(#token, authentication)")
  public TripResponseDTO update(
      @PathVariable String token, @Valid @RequestBody TripUpdateRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('delete_trip')")
  public void delete(@PathVariable String token) {
    service.delete(token);
  }

  @PostMapping("/{token}/restore")
  @PreAuthorize("hasAuthority('restore_trip')")
  public TripResponseDTO restore(@PathVariable String token) {
    return service.restore(token);
  }
}
