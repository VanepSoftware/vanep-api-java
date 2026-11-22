package br.com.vanep.shared.document.service;

import br.com.vanep.shared.document.enums.DocumentDataRequirements;
import br.com.vanep.shared.document.enums.DocumentDecisionStatus;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentReviewViolation;
import br.com.vanep.shared.document.model.DocumentDecision;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.user.model.UserModel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DocumentReviewPolicy {

  public Optional<DocumentReviewViolation> validate(DocumentDecision decision, boolean hasFile) {
    if (decision.status() == DocumentDecisionStatus.REJECTED) {
      if (decision.rejectionReasonCode() == null) {
        return Optional.of(DocumentReviewViolation.REASON_CODE_REQUIRED);
      }
      if (decision.rejectionReasonCode() == DocumentRejectionReason.OTHER
          && isBlank(decision.rejectionReason())) {
        return Optional.of(DocumentReviewViolation.REASON_REQUIRED);
      }
    }
    if (decision.status() == DocumentDecisionStatus.APPROVED && !hasFile) {
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

  public DocumentReview decide(DocumentDecision decision, UserModel reviewer, Instant reviewedAt) {
    boolean rejected = decision.status() == DocumentDecisionStatus.REJECTED;
    DocumentReview review = new DocumentReview();
    review.setStatus(decision.status().toStatus());
    review.setReviewMethod(decision.reviewMethod());
    review.setExternalCheckId(decision.externalCheckId());
    review.setRejectionReasonCode(rejected ? decision.rejectionReasonCode() : null);
    review.setRejectionReason(rejected ? stripToNull(decision.rejectionReason()) : null);
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

  static String stripToNull(String value) {
    return isBlank(value) ? null : value.strip();
  }
}
