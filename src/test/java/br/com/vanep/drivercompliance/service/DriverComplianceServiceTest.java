package br.com.vanep.drivercompliance.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO;
import br.com.vanep.drivercompliance.enums.NextAction;
import br.com.vanep.schooltransportcredential.SchoolTransportCredentialFixtures;
import br.com.vanep.shared.SqlStatementCounter;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DriverComplianceServiceTest {

  private static final LocalDate TODAY = LocalDate.now();

  @Autowired private ApplicationContext context;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private DriverComplianceService service;
  @Autowired private DriverRepository drivers;
  @Autowired private VehicleRepository vehicles;

  private SchoolTransportCredentialFixtures fixtures;
  private DriverModel driver;
  private VehicleModel van;

  @BeforeEach
  void setUp() {
    fixtures = new SchoolTransportCredentialFixtures(context);
    driver = createCompleteDriver(0);
    van = vehicles.findByDriverId(driver.getId()).get(0);
  }

  @Test
  void aCompleteDriverUnderReviewCanBeApproved() {
    DriverComplianceDTO compliance = service.evaluate(driver, TODAY);

    assertThat(compliance.canApprove()).isTrue();
    assertThat(compliance.nextAction()).isEqualTo(NextAction.READY_TO_APPROVE);
    assertThat(compliance.qualifiedVehicles()).hasSize(1);
  }

  @Test
  void evaluatesManyDriversInTheSameNumberOfStatements() {
    List<DriverModel> few = createCompleteDrivers(1, 3);
    List<DriverModel> many = new ArrayList<>(few);
    many.addAll(createCompleteDrivers(3, 30));
    SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);

    long withFew = counter.countStatements(() -> service.evaluateAll(few, TODAY));
    long withMany = counter.countStatements(() -> service.evaluateAll(many, TODAY));

    assertThat(withMany).isEqualTo(withFew);
    assertThat(withFew).isEqualTo(4);
  }

  @Test
  void evaluatingOneDriverGivesTheSameResultAsEvaluatingThemWithOthers() {
    List<DriverModel> others = createCompleteDrivers(1, 3);
    List<DriverModel> all = new ArrayList<>(others);
    all.add(driver);

    Map<Long, DriverComplianceDTO> evaluations = service.evaluateAll(all, TODAY);
    DriverComplianceDTO alone = service.evaluate(driver, TODAY);

    DriverComplianceDTO together = evaluations.get(driver.getId());
    assertThat(together.canApprove()).isEqualTo(alone.canApprove());
    assertThat(together.nextAction()).isEqualTo(alone.nextAction());
    assertThat(together.blockers()).isEqualTo(alone.blockers());
    assertThat(together.nextExpiryOn()).isEqualTo(alone.nextExpiryOn());
    assertThat(evaluations).hasSize(3);
  }

  @Test
  void anInactiveVanIsNeverQualified() {
    van.setActive(false);
    vehicles.save(van);

    assertThat(service.evaluate(driver, TODAY).qualifiedVehicles()).isEmpty();
  }

  @Test
  void aRemovedVanIsNeverQualified() {
    vehicles.delete(van);

    assertThat(service.evaluate(driver, TODAY).vehicles()).isEmpty();
  }

  @Test
  void aVanWithAPendingInspectionDoesNotQualifyTheDriver() {
    vehicles.delete(van);
    VehicleModel pendingVan = fixtures.createVehicle(driver, "PEN1D23");
    fixtures.createVehicleDocument(
        pendingVan, VehicleDocumentType.CRLV, "RENAVAM-2", DocumentStatus.APPROVED);
    fixtures.createVehicleDocument(
        pendingVan, VehicleDocumentType.VEHICLE_INSPECTION, null, DocumentStatus.PENDING);
    fixtures.createVehicleDocument(
        pendingVan,
        VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION,
        "AUT-2",
        DocumentStatus.APPROVED);

    DriverComplianceDTO compliance = service.evaluate(driver, TODAY);

    assertThat(compliance.qualifiedVehicles()).isEmpty();
    assertThat(compliance.canApprove()).isFalse();
  }

  private List<DriverModel> createCompleteDrivers(int from, int to) {
    List<DriverModel> created = new ArrayList<>();
    for (int index = from; index < to; index++) {
      created.add(createCompleteDriver(index));
    }
    return created;
  }

  private DriverModel createCompleteDriver(int index) {
    DriverModel created =
        fixtures.createDriver("motorista" + index + "@vanep.com", "%011d".formatted(index + 1));
    created.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    created = drivers.save(created);
    fixtures.approveTheDriversDocuments(created, "%011d".formatted(90000 + index));
    fixtures.approveTheVansDocuments(
        fixtures.createVehicle(created, "QA%s%02dB12".formatted((char) ('A' + index % 26), index)));
    return created;
  }
}
