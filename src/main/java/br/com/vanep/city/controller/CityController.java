package br.com.vanep.city.controller;

import br.com.vanep.city.dto.CityResponseDTO;
import br.com.vanep.city.service.CityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cities")
public class CityController {
  private final CityService service;

  public CityController(CityService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public Page<CityResponseDTO> list(
      @RequestParam(required = false) String uf,
      @RequestParam(required = false) String search,
      @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
          Pageable pageable) {
    return service.findByUf(uf, search, pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize("isAuthenticated()")
  public CityResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }
}
