package br.com.vanep.schooltransportcredential.controller;

import br.com.vanep.schooltransportcredential.dto.SchoolTransportCredentialsResponseDTO;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicles")
public class SchoolTransportCredentialController {

  private final SchoolTransportCredentialService service;

  public SchoolTransportCredentialController(SchoolTransportCredentialService service) {
    this.service = service;
  }

  @GetMapping("/{token}/school-transport-credentials")
  @PreAuthorize("hasAuthority('show_vehicle') or @sec.isVehicleOwner(#token, authentication)")
  public SchoolTransportCredentialsResponseDTO findToday(@PathVariable String token) {
    return service.findTodayForVehicle(token);
  }
}
