package br.com.vanep.driveroccupancy.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.driveroccupancy.dto.DriverOccupancyResponseDTO;
import br.com.vanep.driveroccupancy.service.DriverOccupancyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers/me/occupancy")
public class DriverOccupancyController {
  private final DriverOccupancyService service;

  public DriverOccupancyController(DriverOccupancyService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public DriverOccupancyResponseDTO findMyOccupancy(Authentication authentication) {
    return service.findMyOccupancy(SecurityHelper.requireCallerUid(authentication));
  }
}
