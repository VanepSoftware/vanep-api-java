package br.com.vanep.drivercnh.seed;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import br.com.vanep.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DriverCnhSeeder {

  private static final Logger log = LoggerFactory.getLogger(DriverCnhSeeder.class);
  private static final List<SeedCnh> SEED_CNHS =
      List.of(
          new SeedCnh("fabio.teixeira@seed.vanep.com.br", "12345678901"),
          new SeedCnh("gustavo.santos@seed.vanep.com.br", "23456789012"),
          new SeedCnh("helena.costa@seed.vanep.com.br", "34567890123"));

  private final DriverCnhRepository cnhs;
  private final DriverRepository drivers;
  private final UserRepository users;

  public DriverCnhSeeder(DriverCnhRepository cnhs, DriverRepository drivers, UserRepository users) {
    this.cnhs = cnhs;
    this.drivers = drivers;
    this.users = users;
  }

  public void seed() {
    for (SeedCnh seed : SEED_CNHS) {
      Optional<DriverModel> driver = resolveDriver(seed.email());
      if (driver.isEmpty()) {
        log.info("Seed: driver CNH seed skipped; seed driver not found ({}).", seed.email());
        continue;
      }
      seedCnh(driver.get(), seed.registration());
    }
  }

  void seedCnh(DriverModel driver, String registration) {
    if (!cnhs.findByDriverIdAndSupersededAtIsNull(driver.getId()).isEmpty()
        || cnhs.existsByRegistrationNumber(registration)) {
      return;
    }
    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver);
    cnh.setRegistrationNumber(registration);
    cnh.setCategory("D");
    cnh.setIssueDate(LocalDate.of(2020, 1, 15));
    cnh.setValidUntil(SchoolTransportCredentialService.today().plusYears(4));
    cnh.setFirstLicenseDate(LocalDate.of(2010, 3, 20));
    cnh.setIssuingState("DF");
    cnh.getReview().setStatus(DocumentStatus.APPROVED);
    cnh.getReview().setReviewMethod(ReviewMethod.MANUAL);
    cnh.getReview().setReviewedAt(Instant.now());
    cnhs.save(cnh);
    log.info("Seed: driver CNH created for driver {}.", driver.getToken());
  }

  private Optional<DriverModel> resolveDriver(String email) {
    return users.findByEmail(email).flatMap(user -> drivers.findByUserId(user.getId()));
  }

  private record SeedCnh(String email, String registration) {}
}
