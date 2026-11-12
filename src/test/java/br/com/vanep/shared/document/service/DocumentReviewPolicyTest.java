package br.com.vanep.shared.document.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.shared.document.enums.DocumentDecisionStatus;
import br.com.vanep.shared.document.enums.DocumentReviewViolation;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentReviewPolicyTest {

  private final DocumentReviewPolicy policy = new DocumentReviewPolicy();

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void rejectingWithoutReasonIsRefused(String reason) {
    assertThat(policy.validate(DocumentDecisionStatus.REJECTED, reason, true))
        .contains(DocumentReviewViolation.REASON_REQUIRED);
  }

  @Test
  void rejectingWithReasonIsAllowedEvenWithoutFile() {
    assertThat(policy.validate(DocumentDecisionStatus.REJECTED, "Ilegível", false)).isEmpty();
  }

  @Test
  void approvingWithoutFileIsRefused() {
    assertThat(policy.validate(DocumentDecisionStatus.APPROVED, null, false))
        .contains(DocumentReviewViolation.FILE_REQUIRED);
  }

  @Test
  void approvingWithFileIsAllowed() {
    assertThat(policy.validate(DocumentDecisionStatus.APPROVED, null, true)).isEmpty();
  }

  @Test
  void aDecisionRecordsTheReviewerTheTimeAndTheMethod() {
    UserModel admin = new UserModel();
    Instant now = Instant.parse("2026-11-11T12:00:00Z");

    DocumentReview review =
        policy.decide(
            DocumentDecisionStatus.APPROVED, ReviewMethod.MANUAL, "ext-1", null, admin, now);

    assertThat(review.getStatus()).isEqualTo(DocumentStatus.APPROVED);
    assertThat(review.getReviewMethod()).isEqualTo(ReviewMethod.MANUAL);
    assertThat(review.getExternalCheckId()).isEqualTo("ext-1");
    assertThat(review.getReviewedBy()).isSameAs(admin);
    assertThat(review.getReviewedAt()).isEqualTo(now);
  }

  @Test
  void aRejectionKeepsTheTrimmedReason() {
    DocumentReview review =
        policy.decide(
            DocumentDecisionStatus.REJECTED,
            null,
            null,
            "  Ilegível  ",
            new UserModel(),
            Instant.now());

    assertThat(review.getRejectionReason()).isEqualTo("Ilegível");
  }

  @Test
  void anApprovalDropsAReasonSentByMistake() {
    DocumentReview review =
        policy.decide(
            DocumentDecisionStatus.APPROVED,
            null,
            null,
            "Ilegível",
            new UserModel(),
            Instant.now());

    assertThat(review.getRejectionReason()).isNull();
  }

  @Test
  void theCleanReviewClearsStatusReviewerTimeMethodAndReason() {
    DocumentReview review = policy.clear();

    assertThat(review.getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(review.getReviewedBy()).isNull();
    assertThat(review.getReviewedAt()).isNull();
    assertThat(review.getReviewMethod()).isNull();
    assertThat(review.getRejectionReason()).isNull();
    assertThat(review.getExternalCheckId()).isNull();
  }

  @Test
  void approvingWithoutTheRequiredNumberIsRefused() {
    assertThat(
            policy.validateApprovalData(
                VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION, null, LocalDate.of(2027, 1, 31)))
        .contains(DocumentReviewViolation.NUMBER_REQUIRED);
  }

  @Test
  void approvingWithoutTheRequiredExpiryIsRefused() {
    assertThat(policy.validateApprovalData(VehicleDocumentType.CRLV, null, null))
        .contains(DocumentReviewViolation.EXPIRY_REQUIRED);
  }

  @Test
  void approvingWithTheRequiredDataIsAccepted() {
    assertThat(
            policy.validateApprovalData(
                VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION,
                "AUT-1",
                LocalDate.of(2027, 1, 31)))
        .isEmpty();
  }

  @Test
  void aTypeWithoutRequiredDataIsApprovedWithBothBlank() {
    assertThat(policy.validateApprovalData(DriverDocumentType.CRIMINAL_RECORD, null, null))
        .isEmpty();
  }

  @Test
  void eachTypeSaysWhatTheApprovalRequires() {
    assertThat(DriverDocumentType.MUNICIPAL_AUTHORIZATION.requiresNumberOnApproval()).isTrue();
    assertThat(DriverDocumentType.SCHOOL_DRIVER_COURSE.requiresNumberOnApproval()).isFalse();
    assertThat(DriverDocumentType.CRIMINAL_RECORD.requiresExpiryOnApproval()).isFalse();
    assertThat(VehicleDocumentType.CRLV.requiresNumberOnApproval()).isFalse();
    assertThat(VehicleDocumentType.VEHICLE_INSPECTION.requiresExpiryOnApproval()).isTrue();
    assertThat(VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION.requiresNumberOnApproval())
        .isTrue();
  }
}
