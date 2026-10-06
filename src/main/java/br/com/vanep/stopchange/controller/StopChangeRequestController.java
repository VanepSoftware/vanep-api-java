package br.com.vanep.stopchange.controller;

import br.com.vanep.stopchange.dto.StopChangeResponseDTO;
import br.com.vanep.stopchange.mapper.StopChangeRequestMapper;
import br.com.vanep.stopchange.service.StopChangeRequestService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stop-change-requests")
public class StopChangeRequestController {

  private final StopChangeRequestService service;
  private final StopChangeRequestMapper mapper;

  public StopChangeRequestController(
      StopChangeRequestService service, StopChangeRequestMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_stop_change_requests')")
  public Page<StopChangeResponseDTO> list(@PageableDefault Pageable pageable) {
    return service.findPage(pageable).map(mapper::toResponse);
  }

  @GetMapping("/{token}")
  @PreAuthorize(
      "hasAuthority('show_stop_change_request') or @sec.isStopChangeRequestOwner(#token, authentication)")
  public StopChangeResponseDTO getByToken(@PathVariable String token) {
    return mapper.toResponse(service.findByToken(token));
  }

  @DeleteMapping("/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('delete_stop_change_request')")
  public void delete(@PathVariable String token) {
    service.deleteByToken(token);
  }

  @PostMapping("/{token}/restore")
  @PreAuthorize("hasAuthority('restore_stop_change_request')")
  public StopChangeResponseDTO restore(@PathVariable String token) {
    return mapper.toResponse(service.restoreByToken(token));
  }
}
