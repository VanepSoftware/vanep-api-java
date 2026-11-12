package br.com.vanep.vehicledocument.mapper;

import br.com.vanep.media.web.MediaUrl;
import br.com.vanep.shared.document.mapper.DocumentFileMapper;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.vehicledocument.dto.VehicleDocumentResponseDTO;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import org.springframework.stereotype.Component;

@Component
public class VehicleDocumentMapper {

  public static final String BASE_PATH = "/api/vehicle-documents";

  public VehicleDocumentResponseDTO toResponse(VehicleDocumentModel document) {
    DocumentReview review = document.getReview();
    return new VehicleDocumentResponseDTO(
        document.getToken(),
        document.getVehicle().getToken(),
        document.getDocumentType(),
        document.getDocumentNumber(),
        MediaUrl.of(BASE_PATH, document.getToken(), "file", document.getFile()),
        DocumentFileMapper.toResponse(document.getFile()),
        document.getExpiresAt(),
        review.getStatus(),
        review.getReviewMethod(),
        review.getExternalCheckId(),
        review.getRejectionReason(),
        review.getReviewedBy() != null ? review.getReviewedBy().getToken() : null,
        review.getReviewedAt(),
        document.getCreatedAt(),
        document.getUpdatedAt());
  }
}
