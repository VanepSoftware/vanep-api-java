package br.com.vanep.drivercompliance.enums;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;

public enum DriverComplianceView {
  AWAITING_DECISION {
    @Override
    public boolean includes(EvaluatedDriver candidate, LocalDate today) {
      return candidate.driver().getApprovalStatus() == DriverApprovalStatus.UNDER_REVIEW;
    }

    @Override
    Comparator<EvaluatedDriver> order() {
      return Comparator.comparing(
              (EvaluatedDriver candidate) ->
                  candidate.compliance().nextAction() == NextAction.DRIVER)
          .thenComparing(
              candidate -> candidate.driver().getSubmittedAt(),
              Comparator.nullsLast(Comparator.<Instant>naturalOrder()));
    }
  },
  PENDING_DOCUMENTS {
    @Override
    public boolean includes(EvaluatedDriver candidate, LocalDate today) {
      return candidate.compliance().pendingReviewCount() > 0;
    }

    @Override
    Comparator<EvaluatedDriver> order() {
      return Comparator.comparing(
          candidate -> candidate.compliance().oldestPendingAt(),
          Comparator.nullsLast(Comparator.<Instant>naturalOrder()));
    }
  },
  EXPIRING {
    @Override
    public boolean includes(EvaluatedDriver candidate, LocalDate today) {
      DriverComplianceDTO compliance = candidate.compliance();
      return compliance.expiredCount() > 0
          || (compliance.nextExpiryOn() != null
              && !compliance.nextExpiryOn().isAfter(today.plusDays(EXPIRY_WINDOW_DAYS)));
    }

    @Override
    Comparator<EvaluatedDriver> order() {
      return Comparator.comparing(
          candidate -> candidate.compliance().nextExpiryOn(),
          Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()));
    }
  },
  ALL {
    @Override
    public boolean includes(EvaluatedDriver candidate, LocalDate today) {
      return true;
    }

    @Override
    Comparator<EvaluatedDriver> order() {
      return Comparator.comparing(
          candidate -> candidate.driver().getCreatedAt(),
          Comparator.nullsLast(Comparator.<Instant>reverseOrder()));
    }
  };

  static final int EXPIRY_WINDOW_DAYS = 30;

  public abstract boolean includes(EvaluatedDriver candidate, LocalDate today);

  abstract Comparator<EvaluatedDriver> order();

  // The driver's id ends every tie, so two equal requests return the same page.
  public Comparator<EvaluatedDriver> comparator() {
    return order()
        .thenComparing(
            candidate -> candidate.driver().getId(),
            Comparator.nullsLast(Comparator.<Long>naturalOrder()));
  }

  public record EvaluatedDriver(DriverModel driver, DriverComplianceDTO compliance) {}
}
