package br.com.vanep.driverdocument.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
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
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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
            documents.findFiltered(
                driver.getId(), null, DocumentStatus.APPROVED, Pageable.unpaged()))
        .extracting(document -> document.getToken())
        .containsExactly(approvedToken);
  }

  @Test
  void theTypeAndTheStatusFilterTogether() {
    DriverDocumentModel approvedCourse = new DriverDocumentModel();
    approvedCourse.setDriver(driver);
    approvedCourse.setDocumentType(DriverDocumentType.SCHOOL_DRIVER_COURSE);
    approvedCourse.getReview().setStatus(DocumentStatus.APPROVED);
    documents.save(approvedCourse);
    DriverDocumentModel pendingCourse = new DriverDocumentModel();
    pendingCourse.setDriver(driver);
    pendingCourse.setDocumentType(DriverDocumentType.SCHOOL_DRIVER_COURSE);
    String pendingCourseToken = documents.save(pendingCourse).getToken();
    DriverDocumentModel pendingRecord = new DriverDocumentModel();
    pendingRecord.setDriver(driver);
    pendingRecord.setDocumentType(DriverDocumentType.CRIMINAL_RECORD);
    documents.save(pendingRecord);

    assertThat(
            documents.findFiltered(
                driver.getId(),
                DriverDocumentType.SCHOOL_DRIVER_COURSE,
                DocumentStatus.PENDING,
                Pageable.unpaged()))
        .extracting(document -> document.getToken())
        .containsExactly(pendingCourseToken);
  }

  @Test
  void storesAndReadsBackTheVersionTheReasonCodeAndTheSupersededDate() {
    DriverDocumentModel document = newDocument(DriverDocumentType.CRIMINAL_RECORD);
    document.getReview().setStatus(DocumentStatus.REJECTED);
    document.getReview().setRejectionReasonCode(DocumentRejectionReason.ILLEGIBLE);
    document.setSupersededAt(Instant.parse("2026-11-12T10:00:00Z"));
    DriverDocumentModel saved = documents.save(document);
    assertThat(saved.getVersion()).isZero();

    saved.setDocumentNumber("changed");
    documents.save(saved);
    DriverDocumentModel stored = documents.findByToken(saved.getToken()).orElseThrow();

    assertThat(stored.getVersion()).isEqualTo(1L);
    assertThat(stored.getReview().getRejectionReasonCode())
        .isEqualTo(DocumentRejectionReason.ILLEGIBLE);
    assertThat(stored.getSupersededAt()).isEqualTo(Instant.parse("2026-11-12T10:00:00Z"));
  }

  @Test
  void theListsIgnoreSupersededSubmissions() {
    DriverDocumentModel superseded = newDocument(DriverDocumentType.CRIMINAL_RECORD);
    superseded.getReview().setStatus(DocumentStatus.APPROVED);
    superseded.setSupersededAt(Instant.now());
    documents.save(superseded);
    DriverDocumentModel live = newDocument(DriverDocumentType.CRIMINAL_RECORD);
    live.getReview().setStatus(DocumentStatus.APPROVED);
    String liveToken = documents.save(live).getToken();

    List<Supplier<List<DriverDocumentModel>>> queries =
        List.of(
            () -> documents.findByDriverIdAndSupersededAtIsNull(driver.getId()),
            () ->
                documents.findFiltered(driver.getId(), null, null, Pageable.unpaged()).getContent(),
            () ->
                documents
                    .findFiltered(
                        driver.getId(),
                        DriverDocumentType.CRIMINAL_RECORD,
                        DocumentStatus.APPROVED,
                        Pageable.unpaged())
                    .getContent(),
            () -> documents.findFiltered(null, null, null, Pageable.unpaged()).getContent());
    queries.forEach(
        query ->
            assertThat(query.get())
                .extracting(document -> document.getToken())
                .containsExactly(liveToken));
  }

  @Test
  void theListsLoadEachReviewerWithoutOneQueryPerDocument() {
    SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);
    saveReviewedDocuments(0, 2);
    long withTwo = counter.countStatements(this::readTheReviewersOfTheFirstPage);
    saveReviewedDocuments(2, 6);
    long withSix = counter.countStatements(this::readTheReviewersOfTheFirstPage);

    assertThat(withSix).isEqualTo(withTwo);
  }

  private void readTheReviewersOfTheFirstPage() {
    documents
        .findFiltered(driver.getId(), null, null, PageRequest.of(0, 20))
        .forEach(document -> document.getReview().getReviewedBy().getEmail());
    documents
        .findByDriverIdAndSupersededAtIsNull(driver.getId())
        .forEach(document -> document.getReview().getReviewedBy().getEmail());
  }

  private void saveReviewedDocuments(int from, int to) {
    for (int index = from; index < to; index++) {
      DriverDocumentModel document = newDocument(DriverDocumentType.SCHOOL_DRIVER_COURSE);
      document.getReview().setStatus(DocumentStatus.APPROVED);
      document
          .getReview()
          .setReviewedBy(
              saveUser("reviewer" + index + "@vanep.com", "0000000000" + index, UserType.ADMIN));
      documents.save(document);
    }
  }

  private DriverDocumentModel newDocument(DriverDocumentType type) {
    DriverDocumentModel document = new DriverDocumentModel();
    document.setDriver(driver);
    document.setDocumentType(type);
    return document;
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
