package br.com.vanep.country.controller;

import br.com.vanep.country.dto.CountryRequestDTO;
import br.com.vanep.country.dto.CountryResponseDTO;
import br.com.vanep.country.service.CountryService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/countries")
public class CountryController {

  private final CountryService service;

  public CountryController(CountryService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_country')")
  public CountryResponseDTO create(@Valid @RequestBody CountryRequestDTO request) {
    return service.create(request);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_countries')")
  public List<CountryResponseDTO> list() {
    return service.findAll();
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasAuthority('show_country')")
  public CountryResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PutMapping("/{token}")
  @PreAuthorize("hasAuthority('update_country')")
  public CountryResponseDTO update(
      @PathVariable String token, @Valid @RequestBody CountryRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @PreAuthorize("hasAuthority('delete_country')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token) {
    service.delete(token);
  }

  @PostMapping("/{token}/restore")
  @PreAuthorize("hasAuthority('restore_country')")
  public CountryResponseDTO restore(@PathVariable String token) {
    return service.restore(token);
  }
}
