package br.com.vanep.role.controller;

import br.com.vanep.role.dto.RoleCreateRequestDTO;
import br.com.vanep.role.dto.RoleResponseDTO;
import br.com.vanep.role.dto.RoleUpdateRequestDTO;
import br.com.vanep.role.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/roles")
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {

  private final RoleService service;

  public RoleController(RoleService service) {
    this.service = service;
  }

  @GetMapping
  public Page<RoleResponseDTO> list(@PageableDefault(size = 20) Pageable pageable) {
    return service.findAll(pageable);
  }

  @GetMapping("/{token}")
  public RoleResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RoleResponseDTO create(@RequestBody @Valid RoleCreateRequestDTO request) {
    return service.create(request);
  }

  @PutMapping("/{token}")
  public RoleResponseDTO update(
      @PathVariable String token, @RequestBody @Valid RoleUpdateRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token) {
    service.delete(token);
  }

  @PostMapping("/{token}/restore")
  public RoleResponseDTO restore(@PathVariable String token) {
    return service.restore(token);
  }
}
