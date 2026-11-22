package br.com.vanep.drivercompliance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.schooltransportcredential.SchoolTransportCredentialFixtures;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.shared.SqlStatementCounter;
import br.com.vanep.shared.document.enums.DocumentStatus;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DriverComplianceControllerTest {

  @Autowired private WebApplicationContext webContext;
  @Autowired private ApplicationContext context;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private DriverRepository drivers;
  @Autowired private DriverDocumentRepository driverDocuments;

  private MockMvc mockMvc;
  private SchoolTransportCredentialFixtures fixtures;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
    fixtures = new SchoolTransportCredentialFixtures(context);
  }

  @Test
  void searchesByNameByEmailAndByPlateWithOrWithoutHyphen() throws Exception {
    DriverModel carlos = createCompleteDriver(0, "carlos.silva@vanep.com", "QAA1B23");
    createCompleteDriver(1, "outro@vanep.com", "QAB2C34");

    for (String search : List.of("CARLOS.SILVA", "qaa-1b23", "QAA 1B23", "qaa1b23")) {
      mockMvc
          .perform(get("/api/drivers/compliance").param("q", search).with(reviewerJwt()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalElements").value(1))
          .andExpect(jsonPath("$.content[0].token").value(carlos.getToken()));
    }
  }

  @Test
  void wildcardsAndASearchWithoutLettersMatchNobody() throws Exception {
    createCompleteDriver(0, "carlos.silva@vanep.com", "QAA1B23");
    createCompleteDriver(1, "outro@vanep.com", "QAB2C34");

    for (String search : List.of("%", "_", "\\", " - ")) {
      mockMvc
          .perform(get("/api/drivers/compliance").param("q", search).with(reviewerJwt()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalElements").value(0));
    }
  }

  @Test
  void aSortParameterIsNotReportedAsApplied() throws Exception {
    createCompleteDriver(0, "carlos.silva@vanep.com", "QAA1B23");

    mockMvc
        .perform(get("/api/drivers/compliance").param("sort", "name,asc").with(reviewerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pageable.sort.sorted").value(false));
  }

  @Test
  void pagesWithTheRightTotalAndTheSamePageTwice() throws Exception {
    for (int index = 0; index < 5; index++) {
      createCompleteDriver(
          index,
          "motorista" + index + "@vanep.com",
          "QA%sA%02d".formatted((char) ('A' + index), index));
    }

    String first =
        mockMvc
            .perform(
                get("/api/drivers/compliance")
                    .param("size", "2")
                    .param("page", "1")
                    .with(reviewerJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.content.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String second =
        mockMvc
            .perform(
                get("/api/drivers/compliance")
                    .param("size", "2")
                    .param("page", "1")
                    .with(reviewerJwt()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(second).isEqualTo(first);
  }

  @Test
  void aRowCarriesTheSituationAndTheRegistrationDateWithoutCpfOrPhone() throws Exception {
    createCompleteDriver(0, "carlos@vanep.com", "QAA1B23");

    mockMvc
        .perform(
            get("/api/drivers/compliance").param("view", "AWAITING_DECISION").with(reviewerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].email").value("carlos@vanep.com"))
        .andExpect(jsonPath("$.content[0].createdAt").exists())
        .andExpect(jsonPath("$.content[0].vehicleCount").value(1))
        .andExpect(jsonPath("$.content[0].nextAction").value("READY_TO_APPROVE"))
        .andExpect(jsonPath("$.content[0].canApprove").value(true))
        .andExpect(jsonPath("$.content[0].document").doesNotExist())
        .andExpect(jsonPath("$.content[0].phone").doesNotExist());
  }

  @Test
  void theQueueTakesTheSameNumberOfStatementsForThreeAndForThirtyDrivers() throws Exception {
    for (int index = 0; index < 3; index++) {
      createCompleteDriver(
          index,
          "motorista" + index + "@vanep.com",
          "QA%sA%02d".formatted((char) ('A' + index), index));
    }
    SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);
    long withThree = counter.countStatements(this::readTheQueue);
    for (int index = 3; index < 30; index++) {
      createCompleteDriver(
          index,
          "motorista" + index + "@vanep.com",
          "QA%sA%02d".formatted((char) ('A' + index % 26), index));
    }
    long withThirty = counter.countStatements(this::readTheQueue);

    assertThat(withThirty).isEqualTo(withThree);
  }

  @Test
  void theCountsGiveEachTabTheDriversTurnTheBadgeAndTheDate() throws Exception {
    createCompleteDriver(0, "pronto@vanep.com", "QAA1B23");
    DriverModel waiting = createCompleteDriver(1, "esperando@vanep.com", "QAB2C34");
    saveCriminalRecordOf(waiting, DocumentStatus.REJECTED);
    DriverModel approved = createCompleteDriver(2, "aprovado@vanep.com", "QAC3D45");
    approved.setApprovalStatus(DriverApprovalStatus.APPROVED);
    drivers.save(approved);

    mockMvc
        .perform(get("/api/drivers/compliance/counts").with(approverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.awaitingDecision").value(2))
        .andExpect(jsonPath("$.awaitingDriver").value(1))
        .andExpect(jsonPath("$.pendingDocuments").value(0))
        .andExpect(jsonPath("$.all").value(3))
        .andExpect(jsonPath("$.badge").value(1))
        .andExpect(jsonPath("$.asOf").value(SchoolTransportCredentialService.today().toString()));
  }

  @Test
  void aDocumentExpiredYesterdayIsInExpiringTodayWithoutAnyWrite() throws Exception {
    DriverModel expired = createCompleteDriver(0, "vencido@vanep.com", "QAA1B23");
    createCompleteDriver(1, "emdia@vanep.com", "QAB2C34");
    DriverDocumentModel record = criminalRecordOf(expired);
    record.setExpiresAt(SchoolTransportCredentialService.today().minusDays(1));
    driverDocuments.save(record);

    mockMvc
        .perform(get("/api/drivers/compliance").param("view", "EXPIRING").with(reviewerJwt()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].email").value("vencido@vanep.com"))
        .andExpect(jsonPath("$.content[0].expiredCount").value(1));
  }

  @Test
  void onlyReviewersAndApproversReadTheQueue() throws Exception {
    mockMvc.perform(get("/api/drivers/compliance").with(reviewerJwt())).andExpect(status().isOk());
    mockMvc.perform(get("/api/drivers/compliance").with(approverJwt())).andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/drivers/compliance")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DRIVER"))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/drivers/compliance/counts")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DRIVER"))))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/drivers/compliance")).andExpect(status().isUnauthorized());
  }

  private void readTheQueue() {
    try {
      mockMvc
          .perform(get("/api/drivers/compliance").param("size", "50").with(reviewerJwt()))
          .andExpect(status().isOk());
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  private DriverModel createCompleteDriver(int index, String email, String plate) {
    DriverModel driver = fixtures.createDriver(email, "%011d".formatted(index + 1));
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    driver = drivers.save(driver);
    fixtures.approveTheDriversDocuments(driver, "%011d".formatted(90000 + index));
    fixtures.approveTheVansDocuments(fixtures.createVehicle(driver, plate));
    return driver;
  }

  private DriverDocumentModel criminalRecordOf(DriverModel driver) {
    return driverDocuments.findByDriverIdAndSupersededAtIsNull(driver.getId()).stream()
        .filter(document -> document.getDocumentType() == DriverDocumentType.CRIMINAL_RECORD)
        .findFirst()
        .orElseThrow();
  }

  private void saveCriminalRecordOf(DriverModel driver, DocumentStatus status) {
    DriverDocumentModel record = criminalRecordOf(driver);
    record.getReview().setStatus(status);
    driverDocuments.save(record);
  }

  private static JwtRequestPostProcessor reviewerJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("review_documents"));
  }

  private static JwtRequestPostProcessor approverJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("approve_driver"));
  }
}
