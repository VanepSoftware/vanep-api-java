package br.com.vanep.drivercnh.mapper;

import br.com.vanep.drivercnh.dto.DriverCnhResponseDTO;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.media.web.MediaUrl;
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
        MediaUrl.of("/api/driver-cnhs", cnh.getToken(), "photo", cnh.getPhoto()),
        cnh.isActive(),
        cnh.getCreatedAt());
  }
}
