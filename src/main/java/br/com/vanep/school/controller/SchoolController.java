package br.com.vanep.school.controller;

import br.com.vanep.school.dto.SchoolRequestDTO;
import br.com.vanep.school.dto.SchoolResponseDTO;
import br.com.vanep.school.service.SchoolService;
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
@RequestMapping("/api/schools")
public class SchoolController {

  private final SchoolService service;

  public SchoolController(SchoolService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_schools')")
  public Page<SchoolResponseDTO> list(@PageableDefault(size = 20) Pageable pageable) {
    return service.findAll(pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasAuthority('show_school')")
  public SchoolResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_school')")
  public SchoolResponseDTO create(@RequestBody @Valid SchoolRequestDTO request) {
    return service.create(request);
  }

  @PutMapping("/{token}")
  @PreAuthorize("hasAuthority('update_school')")
  public SchoolResponseDTO update(
      @PathVariable String token, @RequestBody @Valid SchoolRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('delete_school')")
  public void delete(@PathVariable String token) {
    service.delete(token);
  }

  @PostMapping("/{token}/restore")
  @PreAuthorize("hasAuthority('restore_school')")
  public SchoolResponseDTO restore(@PathVariable String token) {
    return service.restore(token);
  }
}
