package br.com.vanep.permission.controller;

import br.com.vanep.auth.security.PermissionRegistry;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

  @GetMapping
  @PreAuthorize("hasAuthority('list_role_permissions')")
  public List<String> list() {
    return PermissionRegistry.all().stream().sorted().toList();
  }
}
