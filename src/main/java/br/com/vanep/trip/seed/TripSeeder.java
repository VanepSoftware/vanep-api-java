package br.com.vanep.trip.seed;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.enums.Shift;
import br.com.vanep.trip.enums.TripStatus;
import br.com.vanep.trip.model.TripModel;
import br.com.vanep.trip.repository.TripRepository;
import br.com.vanep.trip.service.TripService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TripSeeder {

  private static final Logger log = LoggerFactory.getLogger(TripSeeder.class);

  private final TripRepository tripRepository;
  private final DriverRepository driverRepository;

  public TripSeeder(TripRepository tripRepository, DriverRepository driverRepository) {
    this.tripRepository = tripRepository;
    this.driverRepository = driverRepository;
  }

  public void seed() {
    DriverModel driver =
        driverRepository.findAll().stream()
            .filter(candidate -> candidate.getApprovalStatus() == DriverApprovalStatus.APPROVED)
            .findFirst()
            .orElse(null);

    if (driver == null) {
      return;
    }

    LocalDate today = LocalDate.now(TripService.SERVICE_ZONE);
    LocalDate yesterday = today.minusDays(1);

    if (!tripRepository.findByDriverAndServiceDate(driver.getId(), today).isEmpty()
        || !tripRepository.findByDriverAndServiceDate(driver.getId(), yesterday).isEmpty()) {
      return;
    }

    Instant now = Instant.now();
    TripModel completed = new TripModel();
    completed.setDriver(driver);
    completed.setServiceDate(yesterday);
    completed.setShift(Shift.MORNING);
    completed.setStatus(TripStatus.COMPLETED);
    completed.setStartedAt(now.minus(30, ChronoUnit.HOURS));
    completed.setFinishedAt(now.minus(26, ChronoUnit.HOURS));

    TripModel inProgress = new TripModel();
    inProgress.setDriver(driver);
    inProgress.setServiceDate(today);
    inProgress.setShift(Shift.MORNING);
    inProgress.setStatus(TripStatus.IN_PROGRESS);
    inProgress.setStartedAt(now.minus(2, ChronoUnit.HOURS));

    tripRepository.saveAll(List.of(completed, inProgress));
    log.info(
        "Seed: trips created for driver {} (yesterday COMPLETED, today IN_PROGRESS).",
        driver.getToken());
  }
}
