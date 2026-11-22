package br.com.vanep.shared.document.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record DocumentDataCorrectionRequestDTO(
    @Size(max = 64, message = "{document.number.too_long}") String documentNumber,
    LocalDate expiresAt,
    @NotNull(message = "{document.review.version.required}") Long version) {

  public DocumentDataCorrectionRequestDTO {
    documentNumber =
        documentNumber == null || documentNumber.isBlank() ? null : documentNumber.strip();
  }
}
