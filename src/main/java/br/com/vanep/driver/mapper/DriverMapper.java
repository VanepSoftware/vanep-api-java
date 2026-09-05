package br.com.vanep.driver.mapper;

import br.com.vanep.driver.dto.DriverMeSummaryResponseDTO;
import br.com.vanep.driver.dto.DriverResponseDTO;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.dto.UserMeResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class DriverMapper {
  public DriverResponseDTO toResponse(DriverModel driver) {
    return new DriverResponseDTO(
        driver.getToken(),
        driver.getUser().getName(),
        driver.getUser().getEmail(),
        driver.getUser().getPhone(),
        driver.getUser().getDocument(),
        driver.getPhoto(),
        driver.getRating(),
        driver.getBio(),
        driver.getCnpj(),
        driver.getExperienceYears(),
        driver.getBasePrice(),
        driver.getWorkStartTime(),
        driver.getWorkEndTime(),
        driver.getWorkDays(),
        driver.getWaitToleranceMinutes(),
        driver.getApprovalStatus(),
        driver.isActive(),
        driver.isAvailable(),
        driver.getCreatedAt(),
        driver.getUpdatedAt());
  }

  public DriverMeSummaryResponseDTO toMeSummary(DriverModel driver, UserMeResponseDTO user) {
    return new DriverMeSummaryResponseDTO(
        driver.getToken(),
        driver.getPhoto(),
        driver.getRating(),
        driver.getApprovalStatus(),
        driver.isAvailable(),
        driver.isActive(),
        user);
  }
}
