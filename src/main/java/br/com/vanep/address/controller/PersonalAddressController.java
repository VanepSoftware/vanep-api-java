package br.com.vanep.address.controller;

import br.com.vanep.address.dto.PersonalAddressRequestDTO;
import br.com.vanep.address.dto.PersonalAddressResponseDTO;
import br.com.vanep.address.service.PersonalAddressService;
import br.com.vanep.auth.security.SecurityHelper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/me/address")
public class PersonalAddressController {
  private final PersonalAddressService service;

  public PersonalAddressController(PersonalAddressService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public PersonalAddressResponseDTO findMyAddress(Authentication authentication) {
    return service.findMyAddress(SecurityHelper.requireCallerUid(authentication));
  }

  @PutMapping
  @PreAuthorize("isAuthenticated()")
  public PersonalAddressResponseDTO replaceMyAddress(
      Authentication authentication, @Valid @RequestBody PersonalAddressRequestDTO request) {
    return service.replaceMyAddress(SecurityHelper.requireCallerUid(authentication), request);
  }

  @DeleteMapping
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clearMyAddress(Authentication authentication) {
    service.clearMyAddress(SecurityHelper.requireCallerUid(authentication));
  }
}
