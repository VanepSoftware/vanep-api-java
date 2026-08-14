package br.com.vanep.drivercnh.mapper;

import br.com.vanep.drivercnh.dto.DriverCnhResponseDTO;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import org.springframework.stereotype.Component;

@Component
public class DriverCnhMapper {

  public DriverCnhResponseDTO toResponse(DriverCnhModel cnh) {
    return new DriverCnhResponseDTO(
        cnh.getToken(),
        cnh.getDriver().getToken(),
        cnh.getRegistrationNumber(),
        cnh.getCategory(),
        cnh.getIssueDate(),
        cnh.getValidUntil(),
        cnh.getFirstLicenseDate(),
        cnh.getSecurityNumber(),
        cnh.getIssuingState(),
        cnh.getPhotoUrl(),
        cnh.isActive(),
        cnh.getCreatedAt());
  }
}
