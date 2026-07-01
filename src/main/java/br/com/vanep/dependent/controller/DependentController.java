package br.com.vanep.dependent.controller;

import br.com.vanep.dependent.dto.DependentCreateDTO;
import br.com.vanep.dependent.dto.DependentResponseDTO;
import br.com.vanep.dependent.dto.DependentUpdateDTO;
import br.com.vanep.dependent.service.DependentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dependent")
@PreAuthorize("hasAnyRole('CLIENT','ADMIN')")
public class DependentController {

  private final DependentService service;

  public DependentController(DependentService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DependentResponseDTO create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DependentCreateDTO dto) {
    return service.create(jwt, dto);
  }

  @GetMapping
  public List<DependentResponseDTO> list(@AuthenticationPrincipal Jwt jwt) {
    return service.list(jwt);
  }

  @GetMapping("/{token}")
  public DependentResponseDTO getByToken(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
    return service.getByToken(jwt, token);
  }

  @PatchMapping("/{token}")
  public DependentResponseDTO update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String token,
      @Valid @RequestBody DependentUpdateDTO dto) {
    return service.update(jwt, token, dto);
  }

  @DeleteMapping("/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
    service.delete(jwt, token);
  }

  @PostMapping("/{token}/restore")
  public DependentResponseDTO restore(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
    return service.restore(jwt, token);
  }
}
