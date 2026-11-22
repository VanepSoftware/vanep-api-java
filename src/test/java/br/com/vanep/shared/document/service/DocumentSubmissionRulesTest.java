package br.com.vanep.shared.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.error.ApiProblemException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class DocumentSubmissionRulesTest {

  private final MediaFileService media = mock(MediaFileService.class);
  private final DocumentSubmissionRules rules = new DocumentSubmissionRules(media);

  @Test
  void aPendingSubmissionMayChange() {
    assertThatCode(() -> rules.requirePending(submission(DocumentStatus.PENDING)))
        .doesNotThrowAnyException();
  }

  @Test
  void aDecidedSubmissionIsFinal() {
    assertThatThrownBy(() -> rules.requirePending(submission(DocumentStatus.APPROVED)))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem ->
                assertThat(problem.getMessageKey()).isEqualTo("document.submission.reviewed"));
  }

  @Test
  void aSupersededSubmissionIsFinalEvenIfPending() {
    DriverDocumentModel superseded = submission(DocumentStatus.PENDING);
    superseded.setSupersededAt(Instant.now());

    assertThatThrownBy(() -> rules.requirePending(superseded))
        .isInstanceOf(ApiProblemException.class);
  }

  @Test
  void onlyACallerAllowedToRemoveReviewedSubmissionsRemovesADecidedOne() {
    DriverDocumentModel approved = submission(DocumentStatus.APPROVED);

    assertThatThrownBy(() -> rules.requireRemovable(approved, false))
        .isInstanceOf(ApiProblemException.class);
    assertThatCode(() -> rules.requireRemovable(approved, true)).doesNotThrowAnyException();
  }

  @Test
  void anotherPendingSubmissionOfTheSameTypeIsRefused() {
    DriverDocumentModel submission = submission(DocumentStatus.PENDING);

    assertThatThrownBy(
            () ->
                rules.requireNoOtherPending(
                    submission, List.of(submission, submission(DocumentStatus.PENDING))))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem ->
                assertThat(problem.getMessageKey())
                    .isEqualTo("document.submission.pending_exists"));
    assertThatCode(
            () ->
                rules.requireNoOtherPending(
                    submission, List.of(submission, submission(DocumentStatus.APPROVED))))
        .doesNotThrowAnyException();
  }

  @Test
  void supersedingKeepsTheDecisionAndDiscardsTheFile() {
    DriverDocumentModel rejected = submission(DocumentStatus.REJECTED);
    MediaFileModel file = new MediaFileModel();
    rejected.setFile(file);
    DriverDocumentModel approved = submission(DocumentStatus.APPROVED);

    rules.supersedeRejected(List.of(rejected, approved));

    assertThat(rejected.getSupersededAt()).isNotNull();
    assertThat(rejected.getFile()).isNull();
    assertThat(rejected.getReview().getStatus()).isEqualTo(DocumentStatus.REJECTED);
    assertThat(approved.getSupersededAt()).isNull();
    verify(media).discard(file);
  }

  @Test
  void approvingSupersedesEveryOtherSubmission() {
    DriverDocumentModel approved = submission(DocumentStatus.APPROVED);
    DriverDocumentModel previous = submission(DocumentStatus.APPROVED);

    rules.supersedeAllBut(approved, List.of(approved, previous));

    assertThat(approved.getSupersededAt()).isNull();
    assertThat(previous.getSupersededAt()).isNotNull();
  }

  @Test
  void restoringAnApprovedSubmissionNextToAPendingRenewalIsAccepted() {
    DriverDocumentModel restored = submission(DocumentStatus.APPROVED);

    assertThatCode(
            () ->
                rules.acceptRestored(
                    restored,
                    List.of(
                        restored,
                        submission(DocumentStatus.PENDING),
                        submission(DocumentStatus.REJECTED))))
        .doesNotThrowAnyException();
  }

  @Test
  void restoringAnApprovedSubmissionNextToAnotherApprovedIsAConflict() {
    DriverDocumentModel restored = submission(DocumentStatus.APPROVED);

    assertRestoreConflict(restored, submission(DocumentStatus.APPROVED));
  }

  @Test
  void restoringARejectedSubmissionNextToAPendingOrRejectedIsAConflict() {
    DriverDocumentModel restored = submission(DocumentStatus.REJECTED);

    assertRestoreConflict(restored, submission(DocumentStatus.PENDING));
    assertRestoreConflict(restored, submission(DocumentStatus.REJECTED));
    assertThatCode(
            () ->
                rules.acceptRestored(
                    restored, List.of(restored, submission(DocumentStatus.APPROVED))))
        .doesNotThrowAnyException();
  }

  @Test
  void restoringAPendingSubmissionActsAsANewOne() {
    DriverDocumentModel restored = submission(DocumentStatus.PENDING);
    DriverDocumentModel rejected = submission(DocumentStatus.REJECTED);

    rules.acceptRestored(restored, List.of(restored, rejected));

    assertThat(rejected.getSupersededAt()).isNotNull();
    assertThatThrownBy(
            () ->
                rules.acceptRestored(
                    restored, List.of(restored, submission(DocumentStatus.PENDING))))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem ->
                assertThat(problem.getMessageKey())
                    .isEqualTo("document.submission.pending_exists"));
  }

  @Test
  void restoringASupersededSubmissionChangesNothingElse() {
    DriverDocumentModel restored = submission(DocumentStatus.APPROVED);
    restored.setSupersededAt(Instant.now());

    assertThatCode(
            () -> rules.acceptRestored(restored, List.of(submission(DocumentStatus.APPROVED))))
        .doesNotThrowAnyException();
  }

  @Test
  void aRaceOnTheOnePendingIndexBecomesAConflict() {
    DataIntegrityViolationException violation =
        new DataIntegrityViolationException(
            "duplicate",
            new ConstraintViolationException(
                "duplicate", new SQLException("23505"), "driver_document_one_pending_key"));

    assertThat(rules.translate(violation))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem ->
                assertThat(problem.getMessageKey())
                    .isEqualTo("document.submission.pending_exists"));
  }

  @Test
  void anyOtherViolationIsKept() {
    DataIntegrityViolationException violation =
        new DataIntegrityViolationException(
            "check",
            new ConstraintViolationException(
                "check", new SQLException("23514"), "driver_document_type_check"));

    assertThat(rules.translate(violation)).isSameAs(violation);
  }

  private void assertRestoreConflict(DriverDocumentModel restored, DriverDocumentModel other) {
    assertThatThrownBy(() -> rules.acceptRestored(restored, List.of(restored, other)))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem ->
                assertThat(problem.getMessageKey())
                    .isEqualTo("document.submission.restore_conflict"));
  }

  private static DriverDocumentModel submission(DocumentStatus status) {
    DriverDocumentModel submission = new DriverDocumentModel();
    submission.getReview().setStatus(status);
    return submission;
  }
}
