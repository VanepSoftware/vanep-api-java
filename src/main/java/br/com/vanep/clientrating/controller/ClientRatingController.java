package br.com.vanep.clientrating.controller;

import br.com.vanep.clientrating.dto.ClientRatingCreateRequestDTO;
import br.com.vanep.clientrating.dto.ClientRatingResponseDTO;
import br.com.vanep.clientrating.service.ClientRatingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client-ratings")
public class ClientRatingController {

  private final ClientRatingService service;

  public ClientRatingController(ClientRatingService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_client_rating')")
  public ClientRatingResponseDTO create(
      @Valid @RequestBody ClientRatingCreateRequestDTO request, @AuthenticationPrincipal Jwt jwt) {
    return service.create(request, jwt.getSubject());
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_client_ratings')")
  public Page<ClientRatingResponseDTO> list(
      @RequestParam(required = false) String clientToken,
      @PageableDefault(size = 20) Pageable pageable) {
    return service.findAll(clientToken, pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize(
      "hasAuthority('show_client_rating') or @sec.isClientRatingOwner(#token, authentication)")
  public ClientRatingResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @DeleteMapping("/{token}")
  @PreAuthorize(
      "hasAuthority('delete_client_rating') or @sec.isClientRatingOwner(#token, authentication)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token) {
    service.delete(token);
  }
}
