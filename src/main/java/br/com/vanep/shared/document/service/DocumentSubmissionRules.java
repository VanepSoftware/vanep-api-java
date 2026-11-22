package br.com.vanep.shared.document.service;

import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.model.DocumentSubmission;
import br.com.vanep.shared.error.ApiProblemException;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class DocumentSubmissionRules {

  private static final String ONE_PENDING_INDEX_SUFFIX = "_one_pending_key";

  private final MediaFileService media;

  public DocumentSubmissionRules(MediaFileService media) {
    this.media = media;
  }

  public void requirePending(DocumentSubmission submission) {
    if (submission.getReview().getStatus() != DocumentStatus.PENDING
        || submission.getSupersededAt() != null) {
      throw reviewed();
    }
  }

  public void requireRemovable(DocumentSubmission submission, boolean mayRemoveReviewed) {
    if (!mayRemoveReviewed) {
      requirePending(submission);
    }
  }

  public void requireNoOtherPending(
      DocumentSubmission submission, Collection<? extends DocumentSubmission> sameOwnerAndType) {
    boolean otherPending =
        sameOwnerAndType.stream()
            .anyMatch(
                other ->
                    other != submission && other.getReview().getStatus() == DocumentStatus.PENDING);
    if (otherPending) {
      throw pendingExists();
    }
  }

  public void acceptRestored(
      DocumentSubmission restored, Collection<? extends DocumentSubmission> sameOwnerAndType) {
    if (restored.getSupersededAt() != null) {
      return;
    }
    switch (restored.getReview().getStatus()) {
      case PENDING -> {
        requireNoOtherPending(restored, sameOwnerAndType);
        supersedeRejected(sameOwnerAndType);
      }
      case APPROVED -> requireNoOther(restored, sameOwnerAndType, Set.of(DocumentStatus.APPROVED));
      case REJECTED ->
          requireNoOther(
              restored, sameOwnerAndType, Set.of(DocumentStatus.PENDING, DocumentStatus.REJECTED));
    }
  }

  public void supersedeRejected(Collection<? extends DocumentSubmission> sameOwnerAndType) {
    supersede(
        sameOwnerAndType.stream()
            .filter(other -> other.getReview().getStatus() == DocumentStatus.REJECTED)
            .toList());
  }

  public void supersedeAllBut(
      DocumentSubmission approved, Collection<? extends DocumentSubmission> sameOwnerAndType) {
    supersede(sameOwnerAndType.stream().filter(other -> other != approved).toList());
  }

  public RuntimeException translate(DataIntegrityViolationException violation) {
    if (violation.getCause() instanceof ConstraintViolationException constraint
        && constraint.getConstraintName() != null
        && constraint.getConstraintName().endsWith(ONE_PENDING_INDEX_SUFFIX)) {
      return pendingExists();
    }
    return violation;
  }

  static void requireNoOther(
      DocumentSubmission restored,
      Collection<? extends DocumentSubmission> sameOwnerAndType,
      Set<DocumentStatus> conflicting) {
    boolean conflict =
        sameOwnerAndType.stream()
            .anyMatch(
                other -> other != restored && conflicting.contains(other.getReview().getStatus()));
    if (conflict) {
      throw ApiProblemException.conflict("document.submission.restore_conflict");
    }
  }

  void supersede(Collection<? extends DocumentSubmission> submissions) {
    Instant now = Instant.now();
    for (DocumentSubmission submission : submissions) {
      MediaFileModel file = submission.getReviewedFile();
      submission.setSupersededAt(now);
      submission.setReviewedFile(null);
      media.discard(file);
    }
  }

  static ApiProblemException reviewed() {
    return ApiProblemException.conflict("document.submission.reviewed");
  }

  static ApiProblemException pendingExists() {
    return ApiProblemException.conflict("document.submission.pending_exists");
  }
}
