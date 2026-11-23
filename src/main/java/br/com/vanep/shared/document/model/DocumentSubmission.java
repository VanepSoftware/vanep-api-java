package br.com.vanep.shared.document.model;

import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.shared.document.enums.DocumentKind;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public interface DocumentSubmission {

  String getToken();

  Instant getCreatedAt();

  @Nullable LocalDate expiresOn();

  @Nullable String number();

  DocumentKind kind();

  Instant getNotifiedAt();

  void setNotifiedAt(Instant notifiedAt);

  DocumentReview getReview();

  void setReview(DocumentReview review);

  Long getVersion();

  Instant getSupersededAt();

  void setSupersededAt(Instant supersededAt);

  @Nullable MediaFileModel getReviewedFile();

  void setReviewedFile(@Nullable MediaFileModel file);
}
