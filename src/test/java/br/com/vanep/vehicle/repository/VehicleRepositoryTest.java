package br.com.vanep.vehicle.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class VehicleRepositoryTest {

  @Autowired private VehicleRepository vehicles;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;
  @Autowired private TransactionTemplate transactions;

  private DriverModel driver;

  @BeforeEach
  void setUp() {
    UserModel user = new UserModel();
    user.setType(UserType.DRIVER);
    user.setName("Carlos");
    user.setEmail("carlos@vanep.com");
    user.setDocument("52998224725");
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    driver = new DriverModel();
    driver.setUser(users.save(user));
    driver.setBasePrice(new BigDecimal("100.00"));
    driver = drivers.save(driver);
  }

  @Test
  void locksAVehicleById() {
    VehicleModel van = createVehicle("VAN1A23");

    Optional<VehicleModel> locked = transactions.execute(status -> vehicles.lockById(van.getId()));

    assertThat(locked).get().extracting(vehicle -> vehicle.getPlate()).isEqualTo("VAN1A23");
  }

  @Test
  void aRemovedVehicleIsNotLocked() {
    VehicleModel van = createVehicle("VAN1A23");
    vehicles.delete(van);

    Optional<VehicleModel> locked = transactions.execute(status -> vehicles.lockById(van.getId()));

    assertThat(locked).isEmpty();
  }

  private VehicleModel createVehicle(String plate) {
    VehicleModel vehicle = new VehicleModel();
    vehicle.setDriver(driver);
    vehicle.setPlate(plate);
    vehicle.setBrand("Renault");
    vehicle.setModel("Master");
    vehicle.setManufactureYear(2022);
    vehicle.setColor("Branca");
    vehicle.setCapacity(15);
    return vehicles.save(vehicle);
  }
}
