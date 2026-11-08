package br.com.vanep.vehicle.seed;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VehicleSeeder {

  private static final Logger log = LoggerFactory.getLogger(VehicleSeeder.class);

  private record VehicleSeed(
      String driverEmail,
      String plate,
      String brand,
      String model,
      int manufactureYear,
      String color,
      int capacity) {}

  private static final List<VehicleSeed> SEEDS =
      List.of(
          new VehicleSeed(
              "fabio.teixeira@seed.vanep.com.br",
              "VAN1A23",
              "Mercedes-Benz",
              "Sprinter 415",
              2021,
              "Branca",
              15),
          new VehicleSeed(
              "gustavo.santos@seed.vanep.com.br",
              "VAN2B34",
              "Renault",
              "Master Minibus",
              2020,
              "Prata",
              16),
          new VehicleSeed(
              "helena.costa@seed.vanep.com.br",
              "VAN3C45",
              "Fiat",
              "Ducato Minibus",
              2022,
              "Branca",
              14));

  private final VehicleRepository vehicles;
  private final DriverRepository drivers;
  private final UserRepository users;

  public VehicleSeeder(VehicleRepository vehicles, DriverRepository drivers, UserRepository users) {
    this.vehicles = vehicles;
    this.drivers = drivers;
    this.users = users;
  }

  public void seed() {
    for (VehicleSeed seed : SEEDS) {
      Optional<DriverModel> driver = resolveDriver(seed.driverEmail());
      if (driver.isEmpty()) {
        log.info("Seed: vehicle seed skipped; driver not found ({}).", seed.driverEmail());
        continue;
      }
      if (vehicles.existsByPlate(seed.plate())) {
        continue;
      }
      VehicleModel vehicle = new VehicleModel();
      vehicle.setDriver(driver.get());
      vehicle.setPlate(seed.plate());
      vehicle.setBrand(seed.brand());
      vehicle.setModel(seed.model());
      vehicle.setManufactureYear(seed.manufactureYear());
      vehicle.setColor(seed.color());
      vehicle.setCapacity(seed.capacity());
      vehicles.save(vehicle);
      log.info("Seed: vehicle created ({}).", seed.plate());
    }
  }

  private Optional<DriverModel> resolveDriver(String email) {
    return users.findByEmail(email).flatMap(user -> drivers.findByUserId(user.getId()));
  }
}
