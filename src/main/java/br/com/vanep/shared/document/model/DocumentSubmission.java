package br.com.vanep.shared.document.model;

import br.com.vanep.media.model.MediaFileModel;
import java.time.Instant;

public interface DocumentSubmission {

  DocumentReview getReview();

  void setReview(DocumentReview review);

  Long getVersion();

  Instant getSupersededAt();

  void setSupersededAt(Instant supersededAt);

  MediaFileModel getReviewedFile();

  void setReviewedFile(MediaFileModel file);
}
