package br.com.vanep.driverdocument.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DriverDocumentRepositoryTest {

  @Autowired private DriverDocumentRepository documents;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;

  private DriverModel driver;

  @BeforeEach
  void setUp() {
    driver = new DriverModel();
    driver.setUser(saveUser("carlos@vanep.com", "52998224725", UserType.DRIVER));
    driver.setBasePrice(new BigDecimal("100.00"));
    driver = drivers.save(driver);
  }

  @Test
  void storesAndReadsBackTheNumberAndTheEmbeddedReview() {
    UserModel admin = saveUser("admin@vanep.com", "11144477735", UserType.ADMIN);
    DriverDocumentModel document = new DriverDocumentModel();
    document.setDriver(driver);
    document.setDocumentType(DriverDocumentType.MUNICIPAL_AUTHORIZATION);
    document.setDocumentNumber("TE-0042/2026");
    document.getReview().setStatus(DocumentStatus.REJECTED);
    document.getReview().setReviewMethod(ReviewMethod.MANUAL);
    document.getReview().setRejectionReason("Ilegível");
    document.getReview().setReviewedBy(admin);
    String token = documents.save(document).getToken();

    DriverDocumentModel stored = documents.findByToken(token).orElseThrow();

    assertThat(stored.getDocumentNumber()).isEqualTo("TE-0042/2026");
    assertThat(stored.getReview().getStatus()).isEqualTo(DocumentStatus.REJECTED);
    assertThat(stored.getReview().getReviewMethod()).isEqualTo(ReviewMethod.MANUAL);
    assertThat(stored.getReview().getRejectionReason()).isEqualTo("Ilegível");
    assertThat(stored.getReview().getReviewedBy().getId()).isEqualTo(admin.getId());
  }

  @Test
  void filtersTheDriversDocumentsByTheReviewStatus() {
    DriverDocumentModel approved = new DriverDocumentModel();
    approved.setDriver(driver);
    approved.setDocumentType(DriverDocumentType.CRIMINAL_RECORD);
    approved.getReview().setStatus(DocumentStatus.APPROVED);
    String approvedToken = documents.save(approved).getToken();
    DriverDocumentModel pending = new DriverDocumentModel();
    pending.setDriver(driver);
    pending.setDocumentType(DriverDocumentType.SCHOOL_DRIVER_COURSE);
    documents.save(pending);

    assertThat(
            documents.findByDriverIdAndReviewStatus(
                driver.getId(), DocumentStatus.APPROVED, Pageable.unpaged()))
        .extracting(document -> document.getToken())
        .containsExactly(approvedToken);
  }

  private UserModel saveUser(String email, String document, UserType type) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName(email);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }
}
