package br.com.vanep.drivercompliance.service;

import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driver.service.DriverService;
import br.com.vanep.drivercompliance.dto.DriverMeComplianceResponseDTO;
import br.com.vanep.drivercompliance.mapper.DriverMeComplianceMapper;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverMeComplianceService {

  private final DriverService driverService;
  private final DriverComplianceService complianceService;
  private final DriverMeComplianceMapper mapper;

  public DriverMeComplianceService(
      DriverService driverService,
      DriverComplianceService complianceService,
      DriverMeComplianceMapper mapper) {
    this.driverService = driverService;
    this.complianceService = complianceService;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public DriverMeComplianceResponseDTO findMine(String callerUid) {
    DriverModel driver = driverService.requireCallerDriver(callerUid);
    LocalDate today = SchoolTransportCredentialService.today();
    return mapper.toResponse(driver, complianceService.evaluate(driver, today), today);
  }
}
