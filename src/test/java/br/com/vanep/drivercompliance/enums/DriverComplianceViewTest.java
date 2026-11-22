package br.com.vanep.drivercompliance.enums;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO;
import br.com.vanep.drivercompliance.enums.DriverComplianceView.EvaluatedDriver;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DriverComplianceViewTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

  @Test
  void awaitingDecisionKeepsDriversUnderReviewWithTheDriversTurnLast() {
    EvaluatedDriver driversTurn =
        candidate(1L, DriverApprovalStatus.UNDER_REVIEW, NextAction.DRIVER, at("2026-11-01"));
    EvaluatedDriver ready =
        candidate(
            2L, DriverApprovalStatus.UNDER_REVIEW, NextAction.READY_TO_APPROVE, at("2026-11-10"));
    EvaluatedDriver admins =
        candidate(3L, DriverApprovalStatus.UNDER_REVIEW, NextAction.ADMIN, at("2026-11-05"));
    EvaluatedDriver approved = candidate(4L, DriverApprovalStatus.APPROVED, NextAction.ADMIN, null);

    assertThat(view(DriverComplianceView.AWAITING_DECISION, driversTurn, ready, admins, approved))
        .containsExactly(3L, 2L, 1L);
  }

  @Test
  void pendingDocumentsListsTheOldestPendingFirstWhateverTheRegistration() {
    EvaluatedDriver newer = withPending(1L, DriverApprovalStatus.APPROVED, at("2026-11-12"));
    EvaluatedDriver older = withPending(2L, DriverApprovalStatus.UNDER_REVIEW, at("2026-11-02"));
    EvaluatedDriver none = candidate(3L, DriverApprovalStatus.APPROVED, NextAction.NONE, null);

    assertThat(view(DriverComplianceView.PENDING_DOCUMENTS, newer, older, none))
        .containsExactly(2L, 1L);
  }

  @Test
  void expiringKeepsTheExpiredAndThoseExpiringWithinThirtyDays() {
    EvaluatedDriver expired = withExpiry(1L, TODAY.minusDays(1), 1);
    EvaluatedDriver inThirty = withExpiry(2L, TODAY.plusDays(30), 0);
    EvaluatedDriver inThirtyOne = withExpiry(3L, TODAY.plusDays(31), 0);
    EvaluatedDriver expiredWithoutDate = withExpiry(4L, null, 1);

    assertThat(
            view(DriverComplianceView.EXPIRING, inThirtyOne, inThirty, expiredWithoutDate, expired))
        .containsExactly(1L, 2L, 4L);
  }

  @Test
  void allListsTheNewestRegistrationFirst() {
    EvaluatedDriver older = candidate(1L, DriverApprovalStatus.APPROVED, NextAction.NONE, null);
    older.driver().setCreatedAt(at("2026-01-01"));
    EvaluatedDriver newer = candidate(2L, DriverApprovalStatus.PENDING, NextAction.DRIVER, null);
    newer.driver().setCreatedAt(at("2026-06-01"));

    assertThat(view(DriverComplianceView.ALL, older, newer)).containsExactly(2L, 1L);
  }

  @Test
  void aTieIsBrokenByTheDriver() {
    EvaluatedDriver second = withPending(7L, DriverApprovalStatus.APPROVED, at("2026-11-02"));
    EvaluatedDriver first = withPending(5L, DriverApprovalStatus.APPROVED, at("2026-11-02"));

    assertThat(view(DriverComplianceView.PENDING_DOCUMENTS, second, first)).containsExactly(5L, 7L);
  }

  private static List<Long> view(DriverComplianceView view, EvaluatedDriver... candidates) {
    return Stream.of(candidates)
        .filter(candidate -> view.includes(candidate, TODAY))
        .sorted(view.comparator())
        .map(candidate -> candidate.driver().getId())
        .toList();
  }

  private static EvaluatedDriver candidate(
      Long id, DriverApprovalStatus status, NextAction nextAction, Instant submittedAt) {
    return evaluated(id, status, submittedAt, compliance(nextAction, 0, null, 0, null));
  }

  private static EvaluatedDriver withPending(Long id, DriverApprovalStatus status, Instant oldest) {
    return evaluated(id, status, null, compliance(NextAction.ADMIN, 1, oldest, 0, null));
  }

  private static EvaluatedDriver withExpiry(Long id, LocalDate nextExpiry, int expired) {
    return evaluated(
        id,
        DriverApprovalStatus.APPROVED,
        null,
        compliance(NextAction.DRIVER, 0, null, expired, nextExpiry));
  }

  private static EvaluatedDriver evaluated(
      Long id, DriverApprovalStatus status, Instant submittedAt, DriverComplianceDTO compliance) {
    DriverModel driver = new DriverModel();
    driver.setId(id);
    driver.setApprovalStatus(status);
    driver.setSubmittedAt(submittedAt);
    return new EvaluatedDriver(driver, compliance);
  }

  private static DriverComplianceDTO compliance(
      NextAction nextAction,
      int pending,
      Instant oldestPending,
      int expired,
      LocalDate nextExpiry) {
    return new DriverComplianceDTO(
        List.of(),
        List.of(),
        false,
        List.of(),
        nextAction,
        pending,
        oldestPending,
        0,
        0,
        expired,
        nextExpiry);
  }

  private static Instant at(String date) {
    return Instant.parse(date + "T12:00:00Z");
  }
}
