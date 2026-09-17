package br.com.vanep.driverdocument.mapper;

import br.com.vanep.driverdocument.dto.DriverDocumentResponseDTO;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import org.springframework.stereotype.Component;

@Component
public class DriverDocumentMapper {

  public DriverDocumentResponseDTO toResponse(DriverDocumentModel document) {
    String reviewedByUserToken =
        document.getReviewedBy() != null ? document.getReviewedBy().getToken() : null;

    return new DriverDocumentResponseDTO(
        document.getToken(),
        document.getDriver() != null ? document.getDriver().getToken() : null,
        document.getDocumentType(),
        document.getFileUrl(),
        document.getExpiresAt(),
        document.getStatus(),
        document.getReviewMethod(),
        document.getExternalCheckId(),
        document.getRejectionReason(),
        reviewedByUserToken,
        document.getReviewedAt(),
        document.getNotifiedAt(),
        document.isActive(),
        document.getCreatedAt(),
        document.getUpdatedAt());
  }
}
