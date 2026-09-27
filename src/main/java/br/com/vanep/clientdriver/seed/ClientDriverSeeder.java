package br.com.vanep.clientdriver.seed;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ClientDriverSeeder {

  private static final Logger log = LoggerFactory.getLogger(ClientDriverSeeder.class);

  private final ClientDriverRepository links;
  private final ClientRepository clients;
  private final DriverRepository drivers;

  public ClientDriverSeeder(
      ClientDriverRepository links, ClientRepository clients, DriverRepository drivers) {
    this.links = links;
    this.clients = clients;
    this.drivers = drivers;
  }

  public void seed() {
    ClientModel client = clients.findAll().stream().findFirst().orElse(null);
    DriverModel driver =
        drivers.findAll().stream()
            .filter(candidate -> candidate.getApprovalStatus() == DriverApprovalStatus.APPROVED)
            .findFirst()
            .orElse(null);

    if (client == null || driver == null) {
      return;
    }
    if (links.findByPair(client.getId(), driver.getId()).isPresent()) {
      return;
    }

    ClientDriverModel link = new ClientDriverModel();
    link.setClient(client);
    link.setDriver(driver);
    link.setStatus(RelationshipStatus.ACTIVE);
    links.save(link);

    log.info("Seed: client_driver created ({} <-> {}).", client.getToken(), driver.getToken());
  }
}
