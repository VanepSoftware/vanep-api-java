package br.com.vanep.driverstudent.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.driverstudent.dto.DriverStudentResponseDTO;
import br.com.vanep.driverstudent.service.DriverStudentService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers/me/students")
public class DriverStudentController {

  private final DriverStudentService service;

  public DriverStudentController(DriverStudentService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public List<DriverStudentResponseDTO> findMyStudents(
      Authentication authentication, @RequestParam(required = false) String vehicleToken) {
    return service.findMyStudents(SecurityHelper.requireCallerUid(authentication), vehicleToken);
  }
}
