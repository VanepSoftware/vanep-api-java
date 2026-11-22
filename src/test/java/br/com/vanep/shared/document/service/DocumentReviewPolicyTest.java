package br.com.vanep.shared.document.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.shared.document.enums.DocumentDecisionStatus;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentReviewViolation;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import br.com.vanep.shared.document.model.DocumentDecision;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentReviewPolicyTest {

  private final DocumentReviewPolicy policy = new DocumentReviewPolicy();

  @Test
  void rejectingWithoutACodeIsRefused() {
    assertThat(policy.validate(rejection(null, "Ilegível"), true))
        .contains(DocumentReviewViolation.REASON_CODE_REQUIRED);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void rejectingForAnotherReasonWithoutAMessageIsRefused(String reason) {
    assertThat(policy.validate(rejection(DocumentRejectionReason.OTHER, reason), true))
        .contains(DocumentReviewViolation.REASON_REQUIRED);
  }

  @ParameterizedTest
  @EnumSource(
      value = DocumentRejectionReason.class,
      names = "OTHER",
      mode = EnumSource.Mode.EXCLUDE)
  void rejectingWithAnyOtherCodeNeedsNoMessage(DocumentRejectionReason code) {
    assertThat(policy.validate(rejection(code, null), false)).isEmpty();
  }

  @Test
  void approvingWithoutFileIsRefused() {
    assertThat(policy.validate(approval(), false)).contains(DocumentReviewViolation.FILE_REQUIRED);
  }

  @Test
  void approvingWithFileIsAllowed() {
    assertThat(policy.validate(approval(), true)).isEmpty();
  }

  @Test
  void aDecisionRecordsTheReviewerTheTimeAndTheMethod() {
    UserModel admin = new UserModel();
    Instant now = Instant.parse("2026-11-11T12:00:00Z");

    DocumentReview review =
        policy.decide(
            new DocumentDecision(
                DocumentDecisionStatus.APPROVED, ReviewMethod.MANUAL, "ext-1", null, null),
            admin,
            now);

    assertThat(review.getStatus()).isEqualTo(DocumentStatus.APPROVED);
    assertThat(review.getReviewMethod()).isEqualTo(ReviewMethod.MANUAL);
    assertThat(review.getExternalCheckId()).isEqualTo("ext-1");
    assertThat(review.getReviewedBy()).isSameAs(admin);
    assertThat(review.getReviewedAt()).isEqualTo(now);
  }

  @Test
  void aRejectionKeepsTheCodeAndTheTrimmedMessage() {
    DocumentReview review =
        policy.decide(
            rejection(DocumentRejectionReason.OTHER, "  Foto de outro documento  "),
            new UserModel(),
            Instant.now());

    assertThat(review.getRejectionReasonCode()).isEqualTo(DocumentRejectionReason.OTHER);
    assertThat(review.getRejectionReason()).isEqualTo("Foto de outro documento");
  }

  @Test
  void aRejectionWithABlankMessageStoresNoMessage() {
    DocumentReview review =
        policy.decide(
            rejection(DocumentRejectionReason.ILLEGIBLE, "  "), new UserModel(), Instant.now());

    assertThat(review.getRejectionReasonCode()).isEqualTo(DocumentRejectionReason.ILLEGIBLE);
    assertThat(review.getRejectionReason()).isNull();
  }

  @Test
  void anApprovalClearsTheCodeAndTheMessageSentByMistake() {
    DocumentReview review =
        policy.decide(
            new DocumentDecision(
                DocumentDecisionStatus.APPROVED,
                null,
                null,
                DocumentRejectionReason.EXPIRED,
                "Vencido"),
            new UserModel(),
            Instant.now());

    assertThat(review.getRejectionReasonCode()).isNull();
    assertThat(review.getRejectionReason()).isNull();
  }

  @Test
  void theCleanReviewClearsEverything() {
    DocumentReview review = policy.clear();

    assertThat(review.getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(review.getReviewedBy()).isNull();
    assertThat(review.getReviewedAt()).isNull();
    assertThat(review.getReviewMethod()).isNull();
    assertThat(review.getRejectionReasonCode()).isNull();
    assertThat(review.getRejectionReason()).isNull();
    assertThat(review.getExternalCheckId()).isNull();
  }

  private static DocumentDecision rejection(DocumentRejectionReason code, String reason) {
    return new DocumentDecision(
        DocumentDecisionStatus.REJECTED, ReviewMethod.MANUAL, null, code, reason);
  }

  private static DocumentDecision approval() {
    return new DocumentDecision(
        DocumentDecisionStatus.APPROVED, ReviewMethod.MANUAL, null, null, null);
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
