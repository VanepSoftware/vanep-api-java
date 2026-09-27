package br.com.vanep.driverdocument.mapper;

import br.com.vanep.driverdocument.dto.DriverDocumentResponseDTO;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.media.web.MediaUrl;
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
        MediaUrl.of("/api/driver-documents", document.getToken(), "file", document.getFile()),
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
