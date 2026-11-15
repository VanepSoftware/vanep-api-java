package br.com.vanep.drivercnh.mapper;

import br.com.vanep.drivercnh.dto.DriverCnhResponseDTO;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.media.web.MediaUrl;
import br.com.vanep.shared.document.mapper.DocumentFileMapper;
import br.com.vanep.shared.document.model.DocumentReview;
import org.springframework.stereotype.Component;

@Component
public class DriverCnhMapper {

  public DriverCnhResponseDTO toResponse(DriverCnhModel cnh) {
    DocumentReview review = cnh.getReview();
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
        MediaUrl.of("/api/driver-cnhs", cnh.getToken(), "file", cnh.getFile()),
        DocumentFileMapper.toResponse(cnh.getFile()),
        review.getStatus(),
        review.getReviewMethod(),
        review.getRejectionReasonCode(),
        review.getRejectionReason(),
        review.getReviewedBy() != null ? review.getReviewedBy().getToken() : null,
        review.getReviewedAt(),
        review.getDataCorrectedAt(),
        cnh.getVersion(),
        cnh.isActive(),
        cnh.getCreatedAt());
  }
}
