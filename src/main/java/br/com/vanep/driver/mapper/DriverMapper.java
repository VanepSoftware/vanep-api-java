package br.com.vanep.driver.mapper;

import br.com.vanep.driver.dto.DriverResponseDTO;
import br.com.vanep.driver.model.DriverModel;
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
        driver.getCity(),
        driver.getBasePrice(),
        driver.getWorkStartTime(),
        driver.getWorkEndTime(),
        driver.getWorkDays(),
        driver.getWaitToleranceMinutes(),
        driver.getServiceAreas(),
        driver.getApprovalStatus(),
        driver.isActive(),
        driver.isAvailable(),
        driver.getCreatedAt(),
        driver.getUpdatedAt());
  }
}
