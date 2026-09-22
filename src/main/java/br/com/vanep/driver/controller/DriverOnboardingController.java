package br.com.vanep.driver.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.driver.dto.DriverOnboardingStatusResponseDTO;
import br.com.vanep.driver.dto.DriverRejectionRequestDTO;
import br.com.vanep.driver.dto.DriverResponseDTO;
import br.com.vanep.driver.service.DriverOnboardingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers")
public class DriverOnboardingController {

  private final DriverOnboardingService onboardingService;

  public DriverOnboardingController(DriverOnboardingService onboardingService) {
    this.onboardingService = onboardingService;
  }

  @GetMapping("/me/onboarding")
  @PreAuthorize("isAuthenticated()")
  public DriverOnboardingStatusResponseDTO getMyOnboardingStatus(Authentication authentication) {
    return onboardingService.getOnboardingStatus(SecurityHelper.requireCallerUid(authentication));
  }

  @PostMapping("/me/submit-onboarding")
  @PreAuthorize("isAuthenticated()")
  public DriverOnboardingStatusResponseDTO submitMyOnboarding(Authentication authentication) {
    return onboardingService.submitOnboarding(SecurityHelper.requireCallerUid(authentication));
  }

  @PostMapping("/{token}/approve")
  @PreAuthorize("hasAuthority('approve_driver')")
  public DriverResponseDTO approveDriver(
      @PathVariable String token, Authentication authentication) {
    return onboardingService.approve(token, SecurityHelper.requireCallerUid(authentication));
  }

  @PostMapping("/{token}/reject")
  @PreAuthorize("hasAuthority('approve_driver')")
  public DriverResponseDTO rejectDriver(
      @PathVariable String token,
      @Valid @RequestBody DriverRejectionRequestDTO request,
      Authentication authentication) {
    return onboardingService.reject(
        token, request, SecurityHelper.requireCallerUid(authentication));
  }
}
