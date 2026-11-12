package br.com.vanep.shared.document.service;

import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.shared.document.dto.DocumentStatusUpdateRequestDTO;
import br.com.vanep.shared.document.enums.DocumentDataRequirements;
import br.com.vanep.shared.document.enums.DocumentReviewViolation;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentReviewService {

  private final DocumentReviewPolicy policy;
  private final UserService userService;
  private final MessageSource messages;

  public DocumentReviewService(
      DocumentReviewPolicy policy, UserService userService, MessageSource messages) {
    this.policy = policy;
    this.userService = userService;
    this.messages = messages;
  }

  public DocumentReview review(
      DocumentStatusUpdateRequestDTO request,
      MediaFileModel file,
      DocumentDataRequirements requirements,
      String reviewerUid) {
    policy
        .validate(request.status(), request.rejectionReason(), file != null)
        .or(
            () ->
                request.approves()
                    ? policy.validateApprovalData(
                        requirements, request.documentNumber(), request.expiresAt())
                    : Optional.empty())
        .ifPresent(
            violation -> {
              throw toException(violation);
            });
    UserModel reviewer = userService.requireByToken(reviewerUid);
    return policy.decide(
        request.status(),
        request.reviewMethod(),
        request.externalCheckId(),
        request.rejectionReason(),
        reviewer,
        Instant.now());
  }

  public DocumentReview clear() {
    return policy.clear();
  }

  ResponseStatusException toException(DocumentReviewViolation violation) {
    return switch (violation) {
      case REASON_REQUIRED ->
          new ResponseStatusException(
              HttpStatus.BAD_REQUEST, message("document.review.reason_required"));
      case FILE_REQUIRED ->
          new ResponseStatusException(
              HttpStatus.UNPROCESSABLE_CONTENT, message("document.review.file_required"));
      case NUMBER_REQUIRED -> fieldRequired("documentNumber");
      case EXPIRY_REQUIRED -> fieldRequired("expiresAt");
    };
  }

  public ResponseStatusException fieldRequired(String field) {
    ResponseStatusException exception =
        new ResponseStatusException(
            HttpStatus.BAD_REQUEST, message("document.review.field_required"));
    exception.getBody().setProperty("field", field);
    return exception;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
