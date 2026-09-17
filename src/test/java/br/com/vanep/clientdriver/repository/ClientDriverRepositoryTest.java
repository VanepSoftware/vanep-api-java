package br.com.vanep.clientdriver.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ClientDriverRepositoryTest {

  @Autowired private ClientDriverRepository repository;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;

  private ClientModel maria;
  private DriverModel carlos;

  @BeforeEach
  void setUp() {
    maria = createClient("maria@vanep.com", "11144477735");
    carlos = createDriver("carlos@vanep.com", "52998224725");
  }

  @Test
  void findsTheLinkByPair() {
    ClientDriverModel saved = repository.save(newLink(maria, carlos));

    assertThat(repository.findByPair(maria.getId(), carlos.getId()))
        .get()
        .extracting(link -> link.getId())
        .isEqualTo(saved.getId());
  }

  @Test
  void generatesOpaqueTokenOnPersist() {
    ClientDriverModel saved = repository.save(newLink(maria, carlos));

    assertThat(saved.getToken()).isNotBlank();
    assertThat(saved.getToken()).doesNotContain("-");
    assertThat(repository.findByToken(saved.getToken())).isPresent();
  }

  @Test
  void aLinkIsBornPending() {
    ClientDriverModel saved = repository.save(newLink(maria, carlos));

    assertThat(saved.getStatus()).isEqualTo(RelationshipStatus.PENDING);
  }

  @Test
  void oneClientLinksToSeveralDrivers() {
    DriverModel helena = createDriver("helena@vanep.com", "15350946056");

    repository.save(newLink(maria, carlos));
    repository.save(newLink(maria, helena));

    assertThat(repository.findByClientUserId(maria.getUser().getId())).hasSize(2);
  }

  @Test
  void oneDriverLinksToSeveralClients() {
    ClientModel bruno = createClient("bruno@vanep.com", "12345678909");

    repository.save(newLink(maria, carlos));
    repository.save(newLink(bruno, carlos));

    assertThat(repository.findByDriverUserId(carlos.getUser().getId())).hasSize(2);
  }

  @Test
  void eachSideSeesOnlyItsOwnLinks() {
    ClientModel bruno = createClient("bruno@vanep.com", "12345678909");
    DriverModel helena = createDriver("helena@vanep.com", "15350946056");

    repository.save(newLink(maria, carlos));
    repository.save(newLink(bruno, helena));

    assertThat(repository.findByClientUserId(maria.getUser().getId())).hasSize(1);
    assertThat(repository.findByDriverUserId(helena.getUser().getId())).hasSize(1);
  }

  @Test
  void deactivatingKeepsTheLinkVisible() {
    ClientDriverModel saved = repository.save(newLink(maria, carlos));
    saved.setStatus(RelationshipStatus.INACTIVE);
    repository.save(saved);

    assertThat(repository.findByToken(saved.getToken()))
        .get()
        .extracting(link -> link.getStatus())
        .isEqualTo(RelationshipStatus.INACTIVE);
  }

  @Test
  void softDeletedLinkIsAbsentFromDefaultQueries() {
    ClientDriverModel saved = repository.save(newLink(maria, carlos));

    repository.delete(saved);

    assertThat(repository.findByToken(saved.getToken())).isEmpty();
    assertThat(repository.findByPair(maria.getId(), carlos.getId())).isEmpty();
    assertThat(repository.findByClientUserId(maria.getUser().getId())).isEmpty();
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void allowsANewLinkAfterTheSamePairWasSoftDeleted() {
    ClientDriverModel removed = repository.save(newLink(maria, carlos));
    repository.delete(removed);

    ClientDriverModel recreated = repository.save(newLink(maria, carlos));

    assertThat(recreated.getId()).isNotEqualTo(removed.getId());
    assertThat(repository.findByPair(maria.getId(), carlos.getId()))
        .get()
        .extracting(link -> link.getId())
        .isEqualTo(recreated.getId());
  }

  private ClientDriverModel newLink(ClientModel client, DriverModel driver) {
    ClientDriverModel link = new ClientDriverModel();
    link.setClient(client);
    link.setDriver(driver);
    return link;
  }

  private UserModel createUser(UserType type, String name, String email, String document) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName(name);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }

  private ClientModel createClient(String email, String document) {
    ClientModel client = new ClientModel();
    client.setUser(createUser(UserType.CLIENT, "Cliente", email, document));
    return clients.save(client);
  }

  private DriverModel createDriver(String email, String document) {
    DriverModel driver = new DriverModel();
    driver.setUser(createUser(UserType.DRIVER, "Motorista", email, document));
    driver.setBasePrice(new BigDecimal("100.00"));
    return drivers.save(driver);
  }
}
