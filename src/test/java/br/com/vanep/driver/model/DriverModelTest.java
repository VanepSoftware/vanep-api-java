package br.com.vanep.driver.model;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.user.model.UserModel;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DriverModelTest {

  @Test
  void testOnboardingAndReviewFields() {
    DriverModel driver = new DriverModel();
    UserModel reviewer = new UserModel();
    reviewer.setName("Admin Reviewer");
    Instant now = Instant.now();

    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    driver.setSubmittedAt(now);
    driver.setRejectionReason("Documento com foto ilegível");
    driver.setReviewedAt(now.plusSeconds(3600));
    driver.setReviewedBy(reviewer);

    assertThat(driver.getApprovalStatus()).isEqualTo(DriverApprovalStatus.UNDER_REVIEW);
    assertThat(driver.getSubmittedAt()).isEqualTo(now);
    assertThat(driver.getRejectionReason()).isEqualTo("Documento com foto ilegível");
    assertThat(driver.getReviewedAt()).isEqualTo(now.plusSeconds(3600));
    assertThat(driver.getReviewedBy()).isEqualTo(reviewer);
  }
}
