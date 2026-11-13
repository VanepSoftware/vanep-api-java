package br.com.vanep.schooltransportcredential.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.schooltransportcredential.SchoolTransportCredentialFixtures;
import br.com.vanep.schooltransportcredential.enums.CredentialRequirement;
import br.com.vanep.shared.SqlStatementCounter;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
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
class SchoolTransportCredentialServiceTest {

  private static final LocalDate TODAY = LocalDate.now();

  @Autowired private ApplicationContext context;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private SchoolTransportCredentialService service;
  @Autowired private VehicleRepository vehicles;

  private SchoolTransportCredentialFixtures fixtures;
  private DriverModel driver;
  private VehicleModel van;

  @BeforeEach
  void setUp() {
    fixtures = new SchoolTransportCredentialFixtures(context);
    driver = fixtures.createDriver("dono@vanep.com", "12345678909");
    van = fixtures.createVehicle(driver, "ABC1D23");
    fixtures.approveTheDriversDocuments(driver, "01234567890");
    fixtures.approveTheVansDocuments(van);
  }

  @Test
  void theDriverAndTheVanWithEverythingApprovedAreQualified() {
    assertThat(service.findCredentials(driver.getId(), van.getId(), TODAY).failed()).isEmpty();
    assertThat(service.hasAnyQualifiedVehicle(driver.getId(), TODAY)).isTrue();
  }

  @Test
  void readsTheCredentialsInThreeStatementsWhateverTheNumberOfDocuments() {
    SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);
    long withFew =
        counter.countStatements(() -> service.findCredentials(driver.getId(), van.getId(), TODAY));

    for (int index = 0; index < 4; index++) {
      fixtures.createVehicleDocument(
          van, VehicleDocumentType.VEHICLE_INSPECTION, "VIST-" + index, DocumentStatus.APPROVED);
      fixtures.createDriverDocument(
          driver,
          DriverDocumentType.MUNICIPAL_AUTHORIZATION,
          "AUT-" + index,
          DocumentStatus.PENDING);
    }
    long withMany =
        counter.countStatements(() -> service.findCredentials(driver.getId(), van.getId(), TODAY));

    assertThat(withFew).isEqualTo(3);
    assertThat(withMany).isEqualTo(3);
  }

  @Test
  void checksEveryActiveVanInThreeStatements() {
    for (int index = 0; index < 3; index++) {
      fixtures.approveTheVansDocuments(fixtures.createVehicle(driver, "QAA" + index + "B12"));
    }
    SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);

    assertThat(counter.countStatements(() -> service.hasAnyQualifiedVehicle(driver.getId(), TODAY)))
        .isEqualTo(3);
  }

  @Test
  void theDocumentsOfAnotherVanDoNotCount() {
    VehicleModel vanWithoutDocuments = fixtures.createVehicle(driver, "XYZ9K87");

    assertThat(service.findCredentials(driver.getId(), vanWithoutDocuments.getId(), TODAY).failed())
        .containsExactlyInAnyOrder(
            CredentialRequirement.VEHICLE_REGISTRATION,
            CredentialRequirement.VEHICLE_INSPECTION,
            CredentialRequirement.SCHOOL_VEHICLE_AUTHORIZATION);
  }

  @Test
  void theVanOfAnotherDriverFailsEveryVehicleRequirement() {
    DriverModel other = fixtures.createDriver("outro@vanep.com", "15350946056");
    fixtures.approveTheDriversDocuments(other, "09876543210");

    assertThat(service.findCredentials(other.getId(), van.getId(), TODAY).failed())
        .containsExactlyInAnyOrder(
            CredentialRequirement.VEHICLE_REGISTRATION,
            CredentialRequirement.VEHICLE_INSPECTION,
            CredentialRequirement.SCHOOL_VEHICLE_AUTHORIZATION);
  }

  @Test
  void anInactiveVanIsNeverQualified() {
    van.setActive(false);
    vehicles.save(van);

    assertThat(service.findCredentials(driver.getId(), van.getId(), TODAY).failed())
        .contains(CredentialRequirement.VEHICLE_REGISTRATION);
    assertThat(service.hasAnyQualifiedVehicle(driver.getId(), TODAY)).isFalse();
  }

  @Test
  void aRemovedVanIsNeverQualified() {
    vehicles.delete(van);

    assertThat(service.hasAnyQualifiedVehicle(driver.getId(), TODAY)).isFalse();
  }

  @Test
  void aVanWithAPendingInspectionDoesNotQualifyTheDriver() {
    VehicleModel onlyVan = van;
    vehicles.delete(onlyVan);
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

    assertThat(service.hasAnyQualifiedVehicle(driver.getId(), TODAY)).isFalse();
  }
}
