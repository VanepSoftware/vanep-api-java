package br.com.vanep.dependent.seed;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.school.seed.SchoolSeeder;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.user.repository.UserRepository;
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
  private final SchoolRepository schools;
  private final AddressRepository addresses;

  public DependentSeeder(
      DependentRepository dependents,
      ClientRepository clients,
      UserRepository users,
      SchoolRepository schools,
      AddressRepository addresses) {
    this.dependents = dependents;
    this.clients = clients;
    this.users = users;
    this.schools = schools;
    this.addresses = addresses;
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
    dependent.setShift(SchoolShift.MORNING);
    dependent.setDefaultDependent(isDefault);
    schools
        .findFirstByName(SchoolSeeder.SEED_SCHOOL_NAME)
        .ifPresentOrElse(
            school -> {
              dependent.setSchoolId(school.getId());
              dependent.setAddressId(createHome(school).getId());
            },
            () -> log.info("Seed: {} created without school and address; no seed school.", name));
    dependents.save(dependent);
    log.info("Seed: dependent created ({}).", name);
  }

  private AddressModel createHome(SchoolModel school) {
    AddressModel address = new AddressModel();
    address.setCity(school.getCity());
    address.setZipCode("01001000");
    address.setStreet("Rua das Acácias");
    address.setNumber("250");
    address.setNeighborhood("Centro");
    return addresses.save(address);
  }
}
