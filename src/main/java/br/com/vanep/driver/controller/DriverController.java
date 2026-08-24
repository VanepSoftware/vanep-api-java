package br.com.vanep.driver.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.driver.dto.DriverMeSummaryResponseDTO;
import br.com.vanep.driver.dto.DriverResponseDTO;
import br.com.vanep.driver.dto.DriverSearchResponseDTO;
import br.com.vanep.driver.dto.DriverUpdateRequestDTO;
import br.com.vanep.driver.service.DriverSearchService;
import br.com.vanep.driver.service.DriverService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/drivers")
public class DriverController {

  private final DriverService service;
  private final DriverSearchService searchService;

  public DriverController(DriverService service, DriverSearchService searchService) {
    this.service = service;
    this.searchService = searchService;
  }

  /**
   * Busca por origem + destino. Continua {@code GET} porque é genuinamente read-only: resolve as
   * âncoras sem escrever na árvore (D3). Cada caixa de autocomplete manda a sua própria sessão.
   */
  @GetMapping("/search")
  @PreAuthorize("isAuthenticated()")
  public Page<DriverSearchResponseDTO> search(
      Authentication authentication,
      @RequestParam String originPlaceId,
      @RequestParam(required = false) String originSessionToken,
      @RequestParam String destinationPlaceId,
      @RequestParam(required = false) String destinationSessionToken,
      @PageableDefault(size = 20) Pageable pageable) {
    return searchService.search(
        SecurityHelper.requireCallerUid(authentication),
        originPlaceId,
        originSessionToken,
        destinationPlaceId,
        destinationSessionToken,
        pageable);
  }

  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public DriverMeSummaryResponseDTO getMe(Authentication authentication) {
    return service.getMyProfile(SecurityHelper.requireCallerUid(authentication));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_drivers')")
  public Page<DriverResponseDTO> list(@PageableDefault(size = 20) Pageable pageable) {
    return service.findAll(pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasAuthority('show_driver') or @sec.isDriverOwner(#token, authentication)")
  public DriverResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PutMapping("/{token}")
  @PreAuthorize("hasAuthority('update_driver') or @sec.isDriverOwner(#token, authentication)")
  public DriverResponseDTO update(
      @PathVariable String token, @Valid @RequestBody DriverUpdateRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @PreAuthorize("hasAuthority('delete_driver') or @sec.isDriverOwner(#token, authentication)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token) {
    service.delete(token);
  }

  @PostMapping("/{token}/restore")
  @PreAuthorize("hasAuthority('restore_driver')")
  public DriverResponseDTO restore(@PathVariable String token) {
    return service.restore(token);
  }
}
