package br.com.vanep.driverservicearea.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.driverservicearea.dto.DriverServiceAreaRequestDTO;
import br.com.vanep.driverservicearea.dto.DriverServiceAreaResponseDTO;
import br.com.vanep.driverservicearea.service.DriverServiceAreaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Regiões de atuação do motorista autenticado.
 *
 * <p>Não há checagem de dono por token aqui porque o recurso é sempre "o meu": o motorista é
 * derivado do próprio {@code Authentication}, não de um id na URL. Por isso não foi preciso
 * acrescentar nada ao {@code @sec}.
 */
@RestController
@RequestMapping("/api/drivers/me/service-areas")
public class DriverServiceAreaController {

  private final DriverServiceAreaService service;

  public DriverServiceAreaController(DriverServiceAreaService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public List<DriverServiceAreaResponseDTO> findMyAreas(Authentication authentication) {
    return service.findMyAreas(SecurityHelper.requireCallerUid(authentication));
  }

  @PutMapping
  @PreAuthorize("isAuthenticated()")
  public List<DriverServiceAreaResponseDTO> replaceMyAreas(
      Authentication authentication, @Valid @RequestBody DriverServiceAreaRequestDTO request) {
    return service.replaceMyAreas(SecurityHelper.requireCallerUid(authentication), request);
  }
}
