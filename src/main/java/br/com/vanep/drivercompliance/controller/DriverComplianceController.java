package br.com.vanep.drivercompliance.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.drivercompliance.dto.DriverComplianceCountsResponseDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDetailResponseDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceRowResponseDTO;
import br.com.vanep.drivercompliance.dto.DriverMeComplianceResponseDTO;
import br.com.vanep.drivercompliance.enums.DriverComplianceView;
import br.com.vanep.drivercompliance.service.DriverComplianceDetailService;
import br.com.vanep.drivercompliance.service.DriverComplianceQueueService;
import br.com.vanep.drivercompliance.service.DriverMeComplianceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers")
public class DriverComplianceController {

  private final DriverComplianceQueueService queueService;
  private final DriverComplianceDetailService detailService;
  private final DriverMeComplianceService meService;

  public DriverComplianceController(
      DriverComplianceQueueService queueService,
      DriverComplianceDetailService detailService,
      DriverMeComplianceService meService) {
    this.queueService = queueService;
    this.detailService = detailService;
    this.meService = meService;
  }

  @GetMapping("/me/compliance")
  @PreAuthorize("isAuthenticated()")
  public DriverMeComplianceResponseDTO findMine(Authentication authentication) {
    return meService.findMine(SecurityHelper.requireCallerUid(authentication));
  }

  @GetMapping("/compliance")
  @PreAuthorize("hasAuthority('review_documents') or hasAuthority('approve_driver')")
  public Page<DriverComplianceRowResponseDTO> findQueue(
      @RequestParam(defaultValue = "ALL") DriverComplianceView view,
      @RequestParam(name = "q", required = false) String search,
      @PageableDefault Pageable pageable) {
    return queueService.findQueue(view, search, pageable);
  }

  @GetMapping("/compliance/counts")
  @PreAuthorize("hasAuthority('review_documents') or hasAuthority('approve_driver')")
  public DriverComplianceCountsResponseDTO countQueues() {
    return queueService.countQueues();
  }

  @GetMapping("/{token}/compliance")
  @PreAuthorize("hasAuthority('review_documents') or hasAuthority('approve_driver')")
  public DriverComplianceDetailResponseDTO findDriver(@PathVariable String token) {
    return detailService.findByDriverToken(token);
  }
}
