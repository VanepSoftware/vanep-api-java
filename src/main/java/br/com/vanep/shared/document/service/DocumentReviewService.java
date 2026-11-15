package br.com.vanep.shared.document.service;

import br.com.vanep.shared.document.dto.DocumentDataCorrectionRequestDTO;
import br.com.vanep.shared.document.dto.DocumentStatusUpdateRequestDTO;
import br.com.vanep.shared.document.enums.DocumentDataRequirements;
import br.com.vanep.shared.document.enums.DocumentDecisionStatus;
import br.com.vanep.shared.document.enums.DocumentReviewViolation;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.event.DocumentRejectedEvent;
import br.com.vanep.shared.document.model.DocumentDecision;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.shared.document.model.DocumentSubmission;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class DocumentReviewService {

  private final DocumentReviewPolicy policy;
  private final DocumentSubmissionRules submissionRules;
  private final UserService userService;
  private final ApplicationEventPublisher events;

  public DocumentReviewService(
      DocumentReviewPolicy policy,
      DocumentSubmissionRules submissionRules,
      UserService userService,
      ApplicationEventPublisher events) {
    this.policy = policy;
    this.submissionRules = submissionRules;
    this.userService = userService;
    this.events = events;
  }

  public DocumentReview review(
      DocumentSubmission submission,
      DocumentStatusUpdateRequestDTO request,
      DocumentDataRequirements requirements,
      LocalDate today,
      String reviewerUid) {
    return review(
        submission,
        toDecision(request),
        request.version(),
        () ->
            requireApprovalData(requirements, request.documentNumber(), request.expiresAt(), today),
        reviewerUid);
  }

  // The approval data check runs after the version, the pending state and the file are confirmed,
  // and only on an approval: each kind of submission brings its own data.
  public DocumentReview review(
      DocumentSubmission submission,
      DocumentDecision decision,
      Long seenVersion,
      Runnable approvalDataCheck,
      String reviewerUid) {
    requireSeenVersion(submission, seenVersion);
    submissionRules.requirePending(submission);
    policy
        .validate(decision, submission.getReviewedFile() != null)
        .ifPresent(
            violation -> {
              throw toException(violation);
            });
    if (decision.status() == DocumentDecisionStatus.APPROVED) {
      approvalDataCheck.run();
    }
    UserModel reviewer = userService.requireByToken(reviewerUid);
    DocumentReview review = policy.decide(decision, reviewer, Instant.now());
    if (review.getStatus() == DocumentStatus.REJECTED) {
      events.publishEvent(new DocumentRejectedEvent(submission.kind(), submission.getToken()));
    }
    return review;
  }

  public DocumentReview clear() {
    return policy.clear();
  }

  public void correctData(
      DocumentSubmission submission,
      DocumentDataCorrectionRequestDTO request,
      DocumentDataRequirements requirements,
      LocalDate today,
      String correctorUid) {
    requireCorrectable(submission, request.version());
    policy
        .validateApprovalData(requirements, request.documentNumber(), request.expiresAt())
        .ifPresent(
            violation -> {
              throw toException(violation);
            });
    DocumentDatesPolicy.requireExpiryWithin(
        "expiresAt", request.expiresAt(), requirements.latestExpiryOn(today), LocalDate.MIN);
    markCorrected(submission, correctorUid);
  }

  public void requireCorrectable(DocumentSubmission submission, Long seenVersion) {
    requireSeenVersion(submission, seenVersion);
    if (submission.getReview().getStatus() != DocumentStatus.APPROVED
        || submission.getSupersededAt() != null) {
      throw ApiProblemException.conflict("document.data.not_approved");
    }
  }

  public void markCorrected(DocumentSubmission submission, String correctorUid) {
    submission.getReview().setDataCorrectedById(userService.requireByToken(correctorUid).getId());
    submission.getReview().setDataCorrectedAt(Instant.now());
  }

  void requireApprovalData(
      DocumentDataRequirements requirements,
      String documentNumber,
      LocalDate expiresAt,
      LocalDate today) {
    policy
        .validateApprovalData(requirements, documentNumber, expiresAt)
        .ifPresent(
            violation -> {
              throw toException(violation);
            });
    DocumentDatesPolicy.requireExpiryWithin(
        "expiresAt", expiresAt, requirements.latestExpiryOn(today), today);
  }

  void requireSeenVersion(DocumentSubmission submission, Long seenVersion) {
    if (!Objects.equals(submission.getVersion(), seenVersion)) {
      throw ApiProblemException.conflict("document.review.stale");
    }
  }

  static DocumentDecision toDecision(DocumentStatusUpdateRequestDTO request) {
    return new DocumentDecision(
        request.status(),
        request.reviewMethod(),
        request.externalCheckId(),
        request.rejectionReasonCode(),
        request.rejectionReason());
  }

  static ApiProblemException toException(DocumentReviewViolation violation) {
    return switch (violation) {
      case REASON_CODE_REQUIRED ->
          ApiProblemException.badRequest("document.review.reason_code_required");
      case REASON_REQUIRED -> ApiProblemException.badRequest("document.review.reason_required");
      case FILE_REQUIRED -> ApiProblemException.unprocessable("document.review.file_required");
      case NUMBER_REQUIRED -> fieldRequired("documentNumber");
      case EXPIRY_REQUIRED -> fieldRequired("expiresAt");
    };
  }

  public static ApiProblemException fieldRequired(String field) {
    return ApiProblemException.badRequest("document.review.field_required")
        .withProperty("field", field);
  }
}
