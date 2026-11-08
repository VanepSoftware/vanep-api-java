package br.com.vanep.driver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DriverApprovalStatusTest {

  @Test
  void containsExpectedEnumValues() {
    assertThat(DriverApprovalStatus.values())
        .containsExactlyInAnyOrder(
            DriverApprovalStatus.PENDING,
            DriverApprovalStatus.UNDER_REVIEW,
            DriverApprovalStatus.APPROVED,
            DriverApprovalStatus.REJECTED);
  }

  @Test
  void underReviewHasValidNameAndLengthForDatabaseColumn() {
    assertThat(DriverApprovalStatus.UNDER_REVIEW.name()).isEqualTo("UNDER_REVIEW");
    assertThat(DriverApprovalStatus.UNDER_REVIEW.name().length()).isLessThanOrEqualTo(16);
  }
}
