package br.com.vanep.dependent.seed;

import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.enums.Shift;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.user.UserRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DependentSeeder {

  private static final Logger log = LoggerFactory.getLogger(DependentSeeder.class);
  private static final String SEED_CLIENT_EMAIL = "ana.souza@seed.vanep.com.br";

  private final DependentRepository dependents;
  private final ClientRepository clients;
  private final UserRepository users;

  public DependentSeeder(
      DependentRepository dependents, ClientRepository clients, UserRepository users) {
    this.dependents = dependents;
    this.clients = clients;
    this.users = users;
  }

  public void seed() {
    Optional<Long> clientId = resolveSeedClientId();
    if (clientId.isEmpty()) {
      log.info("Seed: dependent seed skipped; seed client not found ({}).", SEED_CLIENT_EMAIL);
      return;
    }
    createIfMissing(clientId.get(), "Lucas Souza", "90000000001", true);
    createIfMissing(clientId.get(), "Marina Souza", "90000000002", false);
  }

  private Optional<Long> resolveSeedClientId() {
    return users
        .findByEmail(SEED_CLIENT_EMAIL)
        .flatMap(user -> clients.findByUserId(user.getId()))
        .map(client -> client.getId());
  }

  private void createIfMissing(Long clientId, String name, String document, boolean isDefault) {
    if (dependents.existsByDocument(document)) {
      return;
    }
    DependentModel dependent = new DependentModel();
    dependent.setClientId(clientId);
    dependent.setName(name);
    dependent.setDocument(document);
    dependent.setShift(Shift.MORNING);
    dependent.setDefaultDependent(isDefault);
    dependents.save(dependent);
    log.info("Seed: dependent created ({}).", name);
  }
}
