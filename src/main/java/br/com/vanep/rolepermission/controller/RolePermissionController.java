package br.com.vanep.rolepermission.controller;

import br.com.vanep.rolepermission.dto.RolePermissionCreateRequestDTO;
import br.com.vanep.rolepermission.dto.RolePermissionResponseDTO;
import br.com.vanep.rolepermission.dto.RolePermissionUpdateRequestDTO;
import br.com.vanep.rolepermission.service.RolePermissionService;
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
@RequestMapping("/api/role-permissions")
public class RolePermissionController {

  private final RolePermissionService service;

  public RolePermissionController(RolePermissionService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_role_permissions')")
  public Page<RolePermissionResponseDTO> list(@PageableDefault(size = 20) Pageable pageable) {
    return service.findAll(pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasAuthority('show_role_permission')")
  public RolePermissionResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_role_permission')")
  public RolePermissionResponseDTO create(
      @RequestBody @Valid RolePermissionCreateRequestDTO request) {
    return service.create(request);
  }

  @PutMapping("/{token}")
  @PreAuthorize("hasAuthority('update_role_permission')")
  public RolePermissionResponseDTO update(
      @PathVariable String token, @RequestBody @Valid RolePermissionUpdateRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('delete_role_permission')")
  public void delete(@PathVariable String token) {
    service.delete(token);
  }
}
