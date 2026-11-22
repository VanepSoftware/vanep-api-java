package br.com.vanep.drivercnh.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.shared.SqlStatementCounter;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DriverCnhRepositoryTest {

  @Autowired private DriverCnhRepository cnhs;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private DriverModel driver;

  @BeforeEach
  void setUp() {
    driver = new DriverModel();
    driver.setUser(saveUser("carlos@vanep.com", "52998224725", UserType.DRIVER));
    driver.setBasePrice(new BigDecimal("100.00"));
    driver = drivers.save(driver);
  }

  @Test
  void aNewCnhIsPending() {
    assertThat(cnhs.save(newCnh("01234567890")).getReview().getStatus())
        .isEqualTo(DocumentStatus.PENDING);
  }

  @Test
  void storesAndReadsBackTheEmbeddedReviewTheVersionAndTheSupersededDate() {
    UserModel admin = saveUser("admin@vanep.com", "11144477735", UserType.ADMIN);
    DriverCnhModel cnh = newCnh("01234567890");
    cnh.getReview().setStatus(DocumentStatus.REJECTED);
    cnh.getReview().setRejectionReasonCode(DocumentRejectionReason.ILLEGIBLE);
    cnh.getReview().setRejectionReason("Foto cortada");
    cnh.getReview().setReviewMethod(ReviewMethod.MANUAL);
    cnh.getReview().setReviewedBy(admin);
    cnh.getReview().setReviewedAt(Instant.parse("2026-11-10T12:00:00Z"));
    cnh.setNotifiedAt(Instant.parse("2026-11-10T12:01:00Z"));
    cnh.setSupersededAt(Instant.parse("2026-11-12T10:00:00Z"));
    DriverCnhModel saved = cnhs.save(cnh);
    saved.setCategory("E");
    cnhs.save(saved);

    DriverCnhModel stored = cnhs.findByToken(saved.getToken()).orElseThrow();

    assertThat(stored.getVersion()).isEqualTo(1L);
    assertThat(stored.getReview().getStatus()).isEqualTo(DocumentStatus.REJECTED);
    assertThat(stored.getReview().getRejectionReasonCode())
        .isEqualTo(DocumentRejectionReason.ILLEGIBLE);
    assertThat(stored.getReview().getRejectionReason()).isEqualTo("Foto cortada");
    assertThat(stored.getReview().getReviewMethod()).isEqualTo(ReviewMethod.MANUAL);
    assertThat(stored.getReview().getReviewedBy().getId()).isEqualTo(admin.getId());
    assertThat(stored.getReview().getReviewedAt()).isEqualTo(Instant.parse("2026-11-10T12:00:00Z"));
    assertThat(stored.getNotifiedAt()).isEqualTo(Instant.parse("2026-11-10T12:01:00Z"));
    assertThat(stored.getSupersededAt()).isEqualTo(Instant.parse("2026-11-12T10:00:00Z"));
  }

  @Test
  void theListsIgnoreASupersededCnh() {
    DriverCnhModel superseded = newCnh("01234567890");
    superseded.getReview().setStatus(DocumentStatus.APPROVED);
    superseded.setSupersededAt(Instant.now());
    cnhs.save(superseded);
    String renewedToken = cnhs.save(newCnh("01234567890")).getToken();

    assertThat(cnhs.findByDriverIdAndSupersededAtIsNull(driver.getId()))
        .extracting(cnh -> cnh.getToken())
        .containsExactly(renewedToken);
    assertThat(cnhs.findBySupersededAtIsNull())
        .extracting(cnh -> cnh.getToken())
        .containsExactly(renewedToken);
  }

  @Test
  void theAdminListLoadsEachReviewerWithoutOneQueryPerCnh() {
    SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);
    saveReviewedCnhs(0, 2);
    long withTwo = counter.countStatements(this::readTheReviewers);
    saveReviewedCnhs(2, 6);
    long withSix = counter.countStatements(this::readTheReviewers);

    assertThat(withSix).isEqualTo(withTwo);
  }

  private void readTheReviewers() {
    cnhs.findBySupersededAtIsNull()
        .forEach(
            cnh -> {
              cnh.getDriver().getUser().getEmail();
              cnh.getReview().getReviewedBy().getEmail();
            });
  }

  private void saveReviewedCnhs(int from, int to) {
    for (int index = from; index < to; index++) {
      DriverModel owner = new DriverModel();
      owner.setUser(
          saveUser("owner" + index + "@vanep.com", "1000000000" + index, UserType.DRIVER));
      owner.setBasePrice(new BigDecimal("80.00"));
      owner = drivers.save(owner);
      DriverCnhModel cnh = newCnh("0000000000" + index);
      cnh.setDriver(owner);
      cnh.getReview().setStatus(DocumentStatus.APPROVED);
      cnh.getReview()
          .setReviewedBy(
              saveUser("reviewer" + index + "@vanep.com", "2000000000" + index, UserType.ADMIN));
      cnhs.save(cnh);
    }
  }

  private DriverCnhModel newCnh(String registrationNumber) {
    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver);
    cnh.setRegistrationNumber(registrationNumber);
    cnh.setCategory("D");
    cnh.setIssueDate(LocalDate.of(2024, 1, 10));
    cnh.setValidUntil(LocalDate.of(2034, 1, 10));
    return cnh;
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
