package br.com.vanep.shared.document.dto;

import br.com.vanep.shared.document.enums.DocumentDecisionStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record DocumentStatusUpdateRequestDTO(
    @NotNull(message = "{document.review.status.required}") DocumentDecisionStatus status,
    ReviewMethod reviewMethod,
    @Size(max = 64, message = "{document.review.external_check_id.too_long}")
        String externalCheckId,
    @Size(max = 255, message = "{document.review.reason.too_long}") String rejectionReason,
    @Size(max = 64, message = "{document.number.too_long}") String documentNumber,
    LocalDate expiresAt) {

  public DocumentStatusUpdateRequestDTO {
    documentNumber =
        documentNumber == null || documentNumber.isBlank() ? null : documentNumber.strip();
  }

  public boolean approves() {
    return status == DocumentDecisionStatus.APPROVED;
  }
}
