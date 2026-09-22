package br.com.vanep.driver.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.driver.dto.DriverOnboardingStatusResponseDTO;
import br.com.vanep.driver.service.DriverOnboardingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
}
