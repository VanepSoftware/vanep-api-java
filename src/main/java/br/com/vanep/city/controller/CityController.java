package br.com.vanep.city.controller;

import br.com.vanep.city.dto.CityRequestDTO;
import br.com.vanep.city.dto.CityResponseDTO;
import br.com.vanep.city.service.CityService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/cities")
public class CityController {

  private final CityService service;

  public CityController(CityService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_cities')")
  public Page<CityResponseDTO> list(
      @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
          Pageable pageable) {
    return service.findAll(pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasAuthority('show_city')")
  public CityResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_city')")
  public CityResponseDTO create(@RequestBody @Valid CityRequestDTO request) {
    return service.create(request);
  }

  @PutMapping("/{token}")
  @PreAuthorize("hasAuthority('update_city')")
  public CityResponseDTO update(
      @PathVariable String token, @RequestBody @Valid CityRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('delete_city')")
  public void delete(@PathVariable String token) {
    service.delete(token);
  }

  @PostMapping("/{token}/restore")
  @PreAuthorize("hasAuthority('update_city')")
  public CityResponseDTO restore(@PathVariable String token) {
    return service.restore(token);
  }
}
