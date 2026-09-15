package br.com.vanep.trip.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.trip.dto.TripResponseDTO;
import br.com.vanep.trip.dto.TripStartRequestDTO;
import br.com.vanep.trip.service.TripService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers/me/trips")
public class DriverTripController {

  private final TripService service;

  public DriverTripController(TripService service) {
    this.service = service;
  }

  @PostMapping("/start")
  @PreAuthorize("hasAuthority('start_trip')")
  public TripResponseDTO start(
      Authentication authentication, @Valid @RequestBody TripStartRequestDTO request) {
    return service.startToday(SecurityHelper.requireCallerUid(authentication), request.shift());
  }

  @PostMapping("/finish")
  @PreAuthorize("hasAuthority('finish_trip')")
  public TripResponseDTO finish(
      Authentication authentication, @Valid @RequestBody TripStartRequestDTO request) {
    return service.finishToday(SecurityHelper.requireCallerUid(authentication), request.shift());
  }

  @GetMapping("/today")
  @PreAuthorize("isAuthenticated()")
  public List<TripResponseDTO> today(Authentication authentication) {
    return service.findToday(SecurityHelper.requireCallerUid(authentication));
  }
}
