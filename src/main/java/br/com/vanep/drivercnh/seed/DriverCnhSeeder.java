package br.com.vanep.drivercnh.seed;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.user.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DriverCnhSeeder {

  private static final Logger log = LoggerFactory.getLogger(DriverCnhSeeder.class);
  private static final String SEED_DRIVER_EMAIL = "fabio.teixeira@seed.vanep.com.br";
  private static final String SEED_REGISTRATION = "12345678901";

  private final DriverCnhRepository cnhs;
  private final DriverRepository drivers;
  private final UserRepository users;

  public DriverCnhSeeder(DriverCnhRepository cnhs, DriverRepository drivers, UserRepository users) {
    this.cnhs = cnhs;
    this.drivers = drivers;
    this.users = users;
  }

  public void seed() {
    Optional<DriverModel> driver = resolveSeedDriver();
    if (driver.isEmpty()) {
      log.info("Seed: driver CNH seed skipped; seed driver not found ({}).", SEED_DRIVER_EMAIL);
      return;
    }
    if (cnhs.existsByRegistrationNumber(SEED_REGISTRATION)
        || cnhs.existsByDriverId(driver.get().getId())) {
      return;
    }
    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver.get());
    cnh.setRegistrationNumber(SEED_REGISTRATION);
    cnh.setCategory("D");
    cnh.setIssueDate(LocalDate.of(2020, 1, 15));
    cnh.setValidUntil(LocalDate.of(2030, 1, 15));
    cnh.setFirstLicenseDate(LocalDate.of(2010, 3, 20));
    cnh.setIssuingState("DF");
    cnhs.save(cnh);
    log.info("Seed: driver CNH created for {}.", SEED_DRIVER_EMAIL);
  }

  private Optional<DriverModel> resolveSeedDriver() {
    return users.findByEmail(SEED_DRIVER_EMAIL).flatMap(user -> drivers.findByUserId(user.getId()));
  }
}
