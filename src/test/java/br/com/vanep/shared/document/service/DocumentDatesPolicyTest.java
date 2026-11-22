package br.com.vanep.shared.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vanep.shared.error.ApiProblemException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DocumentDatesPolicyTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
  private static final LocalDate LATEST = LocalDate.of(2027, 6, 20);

  @Test
  void theDayBeforeTodayIsPast() {
    assertRefused(TODAY.minusDays(1), "document.expires_at.past");
  }

  @Test
  void todayIsAccepted() {
    assertAccepted(TODAY);
  }

  @Test
  void theLastDayOfTheLimitIsAccepted() {
    assertAccepted(LATEST);
  }

  @Test
  void theDayAfterTheLimitIsTooFar() {
    assertRefused(LATEST.plusDays(1), "document.expires_at.too_far");
  }

  @Test
  void aTypeWithoutLimitAcceptsAnyFutureDate() {
    assertThatCode(
            () ->
                DocumentDatesPolicy.requireExpiryWithin(
                    "expiresAt", TODAY.plusYears(50), LocalDate.MAX, TODAY))
        .doesNotThrowAnyException();
  }

  @Test
  void aDocumentWithoutExpiryIsNotChecked() {
    assertAccepted(null);
  }

  @Test
  void theErrorNamesTheFieldItWasGiven() {
    assertThatThrownBy(
            () ->
                DocumentDatesPolicy.requireExpiryWithin(
                    "validUntil", TODAY.minusDays(1), LATEST, TODAY))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem ->
                assertThat(problem.getBody().getProperties()).containsEntry("field", "validUntil"));
  }

  private static void assertAccepted(LocalDate expiresAt) {
    assertThatCode(
            () -> DocumentDatesPolicy.requireExpiryWithin("expiresAt", expiresAt, LATEST, TODAY))
        .doesNotThrowAnyException();
  }

  private static void assertRefused(LocalDate expiresAt, String code) {
    assertThatThrownBy(
            () -> DocumentDatesPolicy.requireExpiryWithin("expiresAt", expiresAt, LATEST, TODAY))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem -> {
              assertThat(problem.getStatusCode().value()).isEqualTo(400);
              assertThat(problem.getMessageKey()).isEqualTo(code);
              assertThat(problem.getBody().getProperties()).containsEntry("field", "expiresAt");
            });
  }
}
