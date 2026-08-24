package br.com.vanep.school.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.school.dto.SchoolResolveRequestDTO;
import br.com.vanep.school.dto.SchoolResponseDTO;
import br.com.vanep.school.mapper.SchoolMapper;
import br.com.vanep.school.service.SchoolResolveService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resolve um place do Google em uma escola persistida.
 *
 * <p>{@code POST} e não {@code GET} porque a operação faz {@code findOrCreate} — é escrita. Um
 * {@code GET} é definido como safe e é pré-buscável por intermediários: prefetch de browser criaria
 * escola (D9). Segue idempotente por {@code google_place_id}: repetir devolve a mesma linha, com
 * {@code 200} em vez de {@code 201}.
 */
@RestController
@RequestMapping("/api/schools")
public class SchoolResolveController {

  private final SchoolResolveService service;
  private final SchoolMapper mapper;

  public SchoolResolveController(SchoolResolveService service, SchoolMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @PostMapping("/resolve")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<SchoolResponseDTO> resolve(
      Authentication authentication, @Valid @RequestBody SchoolResolveRequestDTO request) {
    SchoolResolveService.Resolution resolution =
        service.resolve(SecurityHelper.requireCallerUid(authentication), request);
    return ResponseEntity.status(resolution.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(mapper.toResponse(resolution.school()));
  }
}
