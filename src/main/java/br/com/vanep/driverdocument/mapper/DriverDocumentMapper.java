package br.com.vanep.driverdocument.mapper;

import br.com.vanep.driverdocument.dto.DriverDocumentResponseDTO;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.media.web.MediaUrl;
import br.com.vanep.shared.document.mapper.DocumentFileMapper;
import br.com.vanep.shared.document.model.DocumentReview;
import org.springframework.stereotype.Component;

@Component
public class DriverDocumentMapper {

  public DriverDocumentResponseDTO toResponse(DriverDocumentModel document) {
    DocumentReview review = document.getReview();
    String reviewedByUserToken =
        review.getReviewedBy() != null ? review.getReviewedBy().getToken() : null;

    return new DriverDocumentResponseDTO(
        document.getToken(),
        document.getDriver() != null ? document.getDriver().getToken() : null,
        document.getDocumentType(),
        document.getDocumentNumber(),
        MediaUrl.of("/api/driver-documents", document.getToken(), "file", document.getFile()),
        DocumentFileMapper.toResponse(document.getFile()),
        document.getExpiresAt(),
        review.getStatus(),
        review.getReviewMethod(),
        review.getExternalCheckId(),
        review.getRejectionReasonCode(),
        review.getRejectionReason(),
        reviewedByUserToken,
        review.getReviewedAt(),
        review.getDataCorrectedAt(),
        document.getNotifiedAt(),
        document.isActive(),
        document.getVersion(),
        document.getCreatedAt(),
        document.getUpdatedAt());
  }
}
