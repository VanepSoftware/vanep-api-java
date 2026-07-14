package br.com.vanep.state.controller;

import br.com.vanep.state.dto.StateResponseDTO;
import br.com.vanep.state.service.StateService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/states")
public class StateController {

  private final StateService service;

  public StateController(StateService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_states')")
  public Page<StateResponseDTO> list(
      @PageableDefault(size = 30, sort = "name", direction = Sort.Direction.ASC)
          Pageable pageable) {
    return service.findAll(pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasAuthority('show_state')")
  public StateResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }
}
