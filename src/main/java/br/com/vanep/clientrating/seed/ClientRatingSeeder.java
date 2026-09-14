package br.com.vanep.clientrating.seed;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientrating.model.ClientRatingModel;
import br.com.vanep.clientrating.repository.ClientRatingRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ClientRatingSeeder {

  private static final Logger log = LoggerFactory.getLogger(ClientRatingSeeder.class);

  private final ClientRatingRepository clientRatingRepository;
  private final DriverRepository driverRepository;
  private final ClientRepository clientRepository;

  public ClientRatingSeeder(
      ClientRatingRepository clientRatingRepository,
      DriverRepository driverRepository,
      ClientRepository clientRepository) {
    this.clientRatingRepository = clientRatingRepository;
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

    if (clientRatingRepository.existsByDriverIdAndClientId(driver.getId(), client.getId())) {
      return;
    }

    ClientRatingModel rating = new ClientRatingModel();
    rating.setDriver(driver);
    rating.setClient(client);
    rating.setRating(BigDecimal.valueOf(5.00));
    rating.setComment("Cliente pontual e educado.");

    clientRatingRepository.save(rating);
    log.info("Seed: client rating created for client {}.", client.getUser().getEmail());
  }
}
