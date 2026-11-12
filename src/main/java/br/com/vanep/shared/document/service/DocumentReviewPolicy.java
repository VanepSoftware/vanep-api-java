package br.com.vanep.shared.document.service;

import br.com.vanep.shared.document.enums.DocumentDataRequirements;
import br.com.vanep.shared.document.enums.DocumentDecisionStatus;
import br.com.vanep.shared.document.enums.DocumentReviewViolation;
import br.com.vanep.shared.document.enums.ReviewMethod;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.user.model.UserModel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DocumentReviewPolicy {

  public Optional<DocumentReviewViolation> validate(
      DocumentDecisionStatus status, String rejectionReason, boolean hasFile) {
    if (status == DocumentDecisionStatus.REJECTED && isBlank(rejectionReason)) {
      return Optional.of(DocumentReviewViolation.REASON_REQUIRED);
    }
    if (status == DocumentDecisionStatus.APPROVED && !hasFile) {
      return Optional.of(DocumentReviewViolation.FILE_REQUIRED);
    }
    return Optional.empty();
  }

  public Optional<DocumentReviewViolation> validateApprovalData(
      DocumentDataRequirements requirements, String documentNumber, LocalDate expiresAt) {
    if (requirements.requiresNumberOnApproval() && documentNumber == null) {
      return Optional.of(DocumentReviewViolation.NUMBER_REQUIRED);
    }
    if (requirements.requiresExpiryOnApproval() && expiresAt == null) {
      return Optional.of(DocumentReviewViolation.EXPIRY_REQUIRED);
    }
    return Optional.empty();
  }

  public DocumentReview decide(
      DocumentDecisionStatus status,
      ReviewMethod reviewMethod,
      String externalCheckId,
      String rejectionReason,
      UserModel reviewer,
      Instant reviewedAt) {
    DocumentReview review = new DocumentReview();
    review.setStatus(status.toStatus());
    review.setReviewMethod(reviewMethod);
    review.setExternalCheckId(externalCheckId);
    review.setRejectionReason(
        status == DocumentDecisionStatus.REJECTED ? rejectionReason.strip() : null);
    review.setReviewedBy(reviewer);
    review.setReviewedAt(reviewedAt);
    return review;
  }

  public DocumentReview clear() {
    return DocumentReview.pending();
  }

  static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
