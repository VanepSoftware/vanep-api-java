package br.com.vanep.driverrating.seed;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverrating.model.DriverRatingModel;
import br.com.vanep.driverrating.repository.DriverRatingRepository;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DriverRatingSeeder {

  private static final Logger log = LoggerFactory.getLogger(DriverRatingSeeder.class);

  private final DriverRatingRepository driverRatingRepository;
  private final DriverRepository driverRepository;
  private final ClientRepository clientRepository;

  public DriverRatingSeeder(
      DriverRatingRepository driverRatingRepository,
      DriverRepository driverRepository,
      ClientRepository clientRepository) {
    this.driverRatingRepository = driverRatingRepository;
    this.driverRepository = driverRepository;
    this.clientRepository = clientRepository;
  }

  public void seed() {
    List<DriverModel> drivers = driverRepository.findAll();
    List<ClientModel> clients = clientRepository.findAll();

    if (drivers.isEmpty() || clients.isEmpty()) {
      return;
    }

    DriverModel driver = drivers.get(0);
    ClientModel client = clients.get(0);

    if (driverRatingRepository.existsByDriverIdAndClientId(driver.getId(), client.getId())) {
      return;
    }

    DriverRatingModel rating = new DriverRatingModel();
    rating.setDriver(driver);
    rating.setClient(client);
    rating.setRating(BigDecimal.valueOf(5.00));
    rating.setComment("Excelente motorista! Muito pontual e atencioso.");

    driverRatingRepository.save(rating);
    log.info("Seed: driver rating created for driver {}.", driver.getUser().getEmail());
  }
}
