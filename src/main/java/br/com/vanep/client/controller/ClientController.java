package br.com.vanep.client.controller;

import br.com.vanep.client.dto.ClientDTO;
import br.com.vanep.client.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientController {

  private final ClientService clientService;

  @PostMapping("/api/create")
  public ResponseEntity<ClientDTO.Response> create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ClientDTO.Request dto) {

    Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

    ClientDTO.Response response = clientService.create(userId, dto);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}