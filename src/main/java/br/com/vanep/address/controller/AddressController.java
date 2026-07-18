package br.com.vanep.address.controller;

import br.com.vanep.address.dto.AddressRequestDTO;
import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.address.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/addresses")
public class AddressController {

  private final AddressService service;

  public AddressController(AddressService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_addresses')")
  public Page<AddressResponseDTO> list(
      @PageableDefault(size = 20, sort = "street", direction = Sort.Direction.ASC)
          Pageable pageable) {
    return service.findAll(pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasAuthority('show_address')")
  public AddressResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_address')")
  public AddressResponseDTO create(@RequestBody @Valid AddressRequestDTO request) {
    return service.create(request);
  }

  @PutMapping("/{token}")
  @PreAuthorize("hasAuthority('update_address')")
  public AddressResponseDTO update(
      @PathVariable String token, @RequestBody @Valid AddressRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('delete_address')")
  public void delete(@PathVariable String token) {
    service.delete(token);
  }

  @PostMapping("/{token}/restore")
  @PreAuthorize("hasAuthority('update_address')")
  public AddressResponseDTO restore(@PathVariable String token) {
    return service.restore(token);
  }
}
